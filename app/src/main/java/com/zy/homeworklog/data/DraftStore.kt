package com.zy.homeworklog.data

import android.content.Context
import android.content.SharedPreferences
import com.zy.homeworklog.HomeworkApp
import com.zy.homeworklog.util.TimeFmt
import java.util.Base64
import kotlinx.coroutines.launch

/**
 * 添加页草稿。
 *
 * 添加到一半关闭程序后，下次打开添加页恢复上次未提交的内容；草稿存放超过 24 小时自动作废，
 * 下次打开为纯净状态。
 *
 * 适用范围：仅「添加」流程（recordId == 0）。编辑已有记录不写/不读草稿。
 * 清空时机：保存成功后立即清空；草稿过期（>24h）在读取时清空。
 * 用户主动「返回/取消」时保留草稿，不丢已填写的内容。
 */
class DraftStore internal constructor(
    private val prefs: SharedPreferences,
    private val onDiscardImages: (List<String>) -> Unit = {},
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("add_draft", Context.MODE_PRIVATE),
        { paths ->
            (context.applicationContext as? HomeworkApp)?.let { app ->
                app.appScope.launch { app.repo.discardUnreferencedImages(paths) }
            }
        },
    )

    data class Draft(
        val subjectId: Long?,
        val typeName: String?,
        val content: String,
        val deadline: Long,
        val isDefaultDeadline: Boolean,
        val ruleId: Long?,
        /** 单条覆盖的提醒时刻；null = 跟随全局提前量 */
        val remindAt: Long? = null,
        val imagePaths: List<String> = emptyList(),
    )

    /** 有实际填写才算草稿；只打开没填不落草稿。 */
    private fun meaningful(d: Draft): Boolean =
        d.content.isNotBlank() || d.subjectId != null || d.typeName != null ||
            !d.isDefaultDeadline || d.ruleId != null || d.remindAt != null || d.imagePaths.isNotEmpty()

    fun save(d: Draft, now: Long = System.currentTimeMillis()): Unit = synchronized(lock) {
        if (!meaningful(d)) {
            clear()
            return@synchronized
        }
        prefs.edit()
            .putLong(KEY_SUBJECT, d.subjectId ?: -1L)
            .putString(KEY_TYPE, d.typeName)
            .putString(KEY_CONTENT, d.content)
            .putLong(KEY_DEADLINE, d.deadline)
            .putBoolean(KEY_DEFAULT, d.isDefaultDeadline)
            .putLong(KEY_RULE, d.ruleId ?: -1L)
            .putLong(KEY_REMIND, d.remindAt ?: -1L)
            .putString(KEY_IMAGES, d.imagePaths.joinToString("\n") {
                Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
            })
            .putLong(KEY_SAVED_AT, now)
            .apply()
    }

    /** 24 小时内返回草稿；过期则清空并返回 null。 */
    fun load(now: Long = System.currentTimeMillis()): Draft? = synchronized(lock) {
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        if (savedAt == 0L) return@synchronized null
        if (now - savedAt > TTL_MS) {
            clear()
            return@synchronized null
        }
        val sid = prefs.getLong(KEY_SUBJECT, -1L)
        val rid = prefs.getLong(KEY_RULE, -1L)
        val rem = prefs.getLong(KEY_REMIND, -1L)
        Draft(
            subjectId = if (sid > 0) sid else null,
            typeName = prefs.getString(KEY_TYPE, null),
            content = prefs.getString(KEY_CONTENT, "") ?: "",
            deadline = prefs.getLong(KEY_DEADLINE, TimeFmt.defaultDeadline()),
            isDefaultDeadline = prefs.getBoolean(KEY_DEFAULT, true),
            ruleId = if (rid > 0) rid else null,
            remindAt = if (rem > 0) rem else null,
            imagePaths = storedImages(),
        )
    }

    fun clear(): Unit = synchronized(lock) {
        val discarded = storedImages()
        prefs.edit().clear().apply()
        if (discarded.isNotEmpty()) onDiscardImages(discarded)
    }

    private fun storedImages(): List<String> =
        (prefs.getString(KEY_IMAGES, "") ?: "").lineSequence().filter { it.isNotBlank() }
            .mapNotNull { encoded -> runCatching {
                String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            }.getOrNull() }.toList()

    /** 晚到的保存回调只能清自己的草稿，不覆盖随后填写的新草稿。 */
    fun clearIfMatches(expected: Draft, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (load(now) == expected) clear()
    }

    companion object {
        private val lock = Any()
        const val TTL_MS = 24L * 60 * 60 * 1000
        private const val KEY_SUBJECT = "subjectId"
        private const val KEY_TYPE = "typeName"
        private const val KEY_CONTENT = "content"
        private const val KEY_DEADLINE = "deadline"
        private const val KEY_DEFAULT = "isDefaultDeadline"
        private const val KEY_RULE = "ruleId"
        private const val KEY_REMIND = "remindAt"
        private const val KEY_IMAGES = "imagePaths"
        private const val KEY_SAVED_AT = "savedAt"
    }
}
