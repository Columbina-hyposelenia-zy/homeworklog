package com.zy.homeworklog.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zy.homeworklog.HomeworkApp
import com.zy.homeworklog.MainActivity
import com.zy.homeworklog.R
import com.zy.homeworklog.data.HomeworkRecord
import com.zy.homeworklog.util.TimeFmt
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 提醒调度 —— 定点闹钟（AlarmManager）。
 *
 * 规则：
 *  1. 每件作业一个提醒时刻：`remindAt ?: (deadline − 全局提前量)`，最终都是一个**绝对时间点**。
 *  2. 只维护「最近的那一个」闹钟；到点后标记提醒时刻、尝试发通知，再重排下一个。
 *  3. 两次提醒之间（空档）不唤醒、不检测。
 *  4. 任何改动都必须调用 [refresh]（全量重算），否则会漏 —— 见 Repository 各写操作。
 *  5. 逾期（deadline 已过）不再提醒；每个提醒时刻只尝试通知一次，改过提醒时刻后可再次触发。
 *
 * 精确闹钟设置若抛出 SecurityException，会尝试退回不精确闹钟；通知能否显示取决于通知权限和系统设置。
 */
object AlarmScheduler {
    private val schedulingLock = Mutex()

    /** 全局提前量偏好键（毫秒）与默认值（1 天）—— 提醒设置页与调度器共用，单一来源。 */
    const val PREF_LEAD_MS = "rem_lead_ms"
    const val DEFAULT_LEAD_MS = 86_400_000L

    /** 总开关偏好键。 */
    const val PREF_ENABLED = "rem_enabled"

    private const val REQUEST_CODE = 1001
    private const val CONTENT_REQUEST_CODE = 1002
    private const val ACTION_FIRE = "com.zy.homeworklog.action.REMIND"
    /** 闹钟到点判定容差：把紧邻的几十秒也算作「到点」，避免因微小提前而漏发。 */
    private const val DUE_TOLERANCE_MS = 60_000L

    /**
     * **全量重算 + 重排**。任何会改变提醒计划的入口都必须调用它：
     * 数据增/删/改/勾完成、开机、覆盖安装、系统时间或时区变更、打开 App、提醒发出后。
     */
    fun refresh(context: Context) {
        val app = context.applicationContext as? HomeworkApp ?: return
        app.appScope.launch {
            try {
                refreshNow(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AlarmScheduler", "Unable to refresh reminders", e)
            }
        }
    }

    /** 广播可以等待重排完成；查询、撤销、设置闹钟和到点处理使用同一把锁。 */
    suspend fun refreshNow(app: HomeworkApp) = schedulingLock.withLock { reschedule(app) }

    /** 更新记录与重置提醒标记不能被到点通知插入，否则刚写入的标记可能被清掉。 */
    suspend fun <T> withReminderUpdate(block: suspend () -> T): T = schedulingLock.withLock { block() }

    /** 撤掉当前闹钟（不清数据，下次 refresh 会重排）。 */
    fun cancel(context: Context) {
        val app = context.applicationContext as? HomeworkApp ?: return
        app.appScope.launch {
            schedulingLock.withLock {
                app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(app))
            }
        }
    }

    /** 提醒时刻：单条覆盖优先，否则 = 截止 − 全局提前量。 */
    fun remindAtOf(r: HomeworkRecord, globalLeadMs: Long): Long =
        r.remindAt ?: (r.deadline - globalLeadMs)

    fun reminderChanged(before: HomeworkRecord, after: HomeworkRecord, globalLeadMs: Long): Boolean =
        remindAtOf(before, globalLeadMs) != remindAtOf(after, globalLeadMs)

    /** 全量重算后，返回「下一次需要触发的时刻」；null = 没有待提醒项（闹钟会被撤掉）。 */
    suspend fun nextTriggerAt(app: HomeworkApp): Long? {
        if (!remindersOn(app)) return null
        val lead = app.repo.prefLong(PREF_LEAD_MS, DEFAULT_LEAD_MS)
        val now = System.currentTimeMillis()
        return app.repo.incompleteNow()
            .mapNotNull { pendingAt(app, it, lead, now) }
            .minOrNull()
            ?.coerceAtLeast(now)
    }

    /**
     * 该记录「尚未提醒过」的提醒时刻；已提醒过 / 已逾期则返回 null。
     * 幂等键存的是**上次提醒时刻的值**（不是布尔），所以改过提醒时间就会重新提醒。
     */
    private fun pendingAt(app: HomeworkApp, r: HomeworkRecord, leadMs: Long, now: Long): Long? {
        if (r.deadline <= now) return null // 逾期不再提醒
        val at = remindAtOf(r, leadMs)
        if (app.repo.remindDone(r.id) == at) return null // 该时间点已提醒过
        return at
    }

    /**
     * 闹钟到点后先标记符合条件的提醒，再尝试合并发送通知；finally 中会调用重排。
     */
    suspend fun fireDue(app: HomeworkApp) = schedulingLock.withLock {
        try {
            if (remindersOn(app)) {
                val lead = app.repo.prefLong(PREF_LEAD_MS, DEFAULT_LEAD_MS)
                val now = System.currentTimeMillis()
                val due = app.repo.incompleteNow()
                    .filter { r ->
                        val at = pendingAt(app, r, lead, now)
                        at != null && at <= now + DUE_TOLERANCE_MS
                    }
                    .sortedBy { it.deadline }
                if (due.isNotEmpty()) {
                    // 先标记再发：即使通知构建失败也不会留下「永远待发」的项导致闹钟空转
                    due.forEach { app.repo.setRemindDone(it.id, remindAtOf(it, lead)) }
                    runCatching { notify(app, due, lead) }
                }
            }
        } finally {
            reschedule(app)
        }
    }

    // ---- 内部 ----

    // 精确闹钟设置若被系统拒绝，会尝试退回不精确闹钟。
    @SuppressLint("MissingPermission")
    private suspend fun reschedule(app: HomeworkApp) {
        // 查询失败时保留原闹钟，避免先撤掉后因异常失去后续提醒。
        val at = nextTriggerAt(app)
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(app)
        am?.cancel(pi)
        if (at == null) return
        val now = System.currentTimeMillis()
        val trigger = if (at <= now) now + 1_000L else at
        try {
            am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (e: SecurityException) {
            // 精确闹钟调用被拒绝时，尝试设置不精确闹钟；触发时间可能延后。
            am?.set(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun remindersOn(app: HomeworkApp): Boolean =
        app.repo.pref(PREF_ENABLED, true) && notificationsAllowed(app)

    private fun notificationsAllowed(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission") // 已在 remindersOn() 里守卫 POST_NOTIFICATIONS
    private suspend fun notify(app: HomeworkApp, due: List<HomeworkRecord>, leadMs: Long) {
        val first = due.first()
        val title: String
        val text: String
        if (due.size == 1) {
            val name = app.repo.subject(first.subjectId)?.name ?: "作业"
            title = "$name · " + if (first.remindAt != null) "提醒" else "提前 ${TimeFmt.fmtLead(leadMs)}"
            text = if (first.content.isBlank()) {
                "截止 ${TimeFmt.fmtDeadline(first.deadline)}"
            } else {
                "${first.content.take(24)}｜截止 ${TimeFmt.fmtDeadline(first.deadline)}"
            }
        } else {
            title = "有 ${due.size} 件作业快到截止"
            val names = due.mapNotNull { app.repo.subject(it.subjectId)?.name }.distinct().take(4)
            text = names.joinToString("、") + "｜最近 ${TimeFmt.fmtDeadline(first.deadline)}"
        }
        val n = NotificationCompat.Builder(app, HomeworkApp.CHANNEL_DEADLINE)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(app))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(app).notify((3000 + first.id % 1000).toInt(), n)
    }

    /** 点通知回到 App 首页。 */
    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
