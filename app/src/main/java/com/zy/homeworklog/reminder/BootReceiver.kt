package com.zy.homeworklog.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zy.homeworklog.HomeworkApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 系统事件 → 全量重排提醒。
 *
 * AlarmManager 登记的闹钟在下列事件后会丢失，需要重新排一遍：
 *  - BOOT_COMPLETED        开机
 *  - MY_PACKAGE_REPLACED   覆盖安装 APK
 *  - TIME_SET / TIMEZONE_CHANGED  系统时间或时区变更
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }

        val app = context.applicationContext as? HomeworkApp ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                AlarmScheduler.refreshNow(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to refresh reminders", e)
            } finally {
                pending.finish()
            }
        }
    }
}
