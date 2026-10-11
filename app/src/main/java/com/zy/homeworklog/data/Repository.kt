package com.zy.homeworklog.data

import android.content.Context
import androidx.room.withTransaction
import com.zy.homeworklog.reminder.AlarmScheduler
import com.zy.homeworklog.util.TimeFmt
import java.io.File
import java.util.UUID
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeworkRepository(private val db: AppDatabase, private val context: Context) {
    private val imageMutex = Mutex()

    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    val subjects = db.subjectDao().all()
    val incomplete = db.recordDao().incomplete()
    val counts = db.recordDao().counts()
    val completed = db.recordDao().completed()
    val images = db.imageDao().all()

    suspend fun subject(id: Long): Subject? = db.subjectDao().byId(id)
    suspend fun record(id: Long): HomeworkRecord? = db.recordDao().byId(id)
    suspend fun imagesFor(recordId: Long): List<RecordImage> = db.imageDao().forRecord(recordId)
    suspend fun rule(id: Long): RecurringRule? = db.ruleDao().byId(id)
    suspend fun allRulesNow(): List<RecurringRule> = db.ruleDao().allNow()
    suspend fun incompleteForRule(ruleId: Long): List<HomeworkRecord> = db.recordDao().incompleteForRule(ruleId)

    /** 名称检查、科目写入和冲突换色一起提交，失败时不留下半次操作。 */
    suspend fun saveSubjectWithSwap(
        id: Long?, name: String, color: Long, conflict: Subject?, swap: Long,
    ): Long = db.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "科目名不能为空" }
        val duplicate = db.subjectDao().byName(trimmed)
        require(duplicate == null || duplicate.id == id) { "已存在同名科目，请换一个名称" }
        val savedId = if (id == null) {
            db.subjectDao().insert(Subject(name = trimmed, colorArgb = color))
        } else {
            val old = db.subjectDao().byId(id) ?: error("该科目已不存在，请重新选择")
            db.subjectDao().update(old.copy(name = trimmed, colorArgb = color))
            id
        }
        conflict?.takeIf { it.id != savedId }?.let { previous ->
            db.subjectDao().byId(previous.id)?.takeIf { it.colorArgb == color }?.let {
                db.subjectDao().update(it.copy(colorArgb = swap))
            }
        }
        savedId
    }

    /** 保留历史记录的外键；没有任何记录时连同周期规则一起删除。 */
    suspend fun deleteSubject(s: Subject): Boolean = db.withTransaction {
        if (db.subjectDao().recordCount(s.id) > 0) return@withTransaction false
        db.ruleDao().deleteForSubject(s.id)
        db.subjectDao().delete(s)
        true
    }

    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction(block)

    private suspend fun validRule(subjectId: Long, ruleId: Long?): Long? =
        ruleId?.takeIf { db.ruleDao().byId(it)?.subjectId == subjectId }

    suspend fun addRecord(
        subjectId: Long,
        typeName: String,
        content: String,
        deadline: Long,
        ruleId: Long?,
        imagePaths: List<String>,
        remindAt: Long? = null,
    ): Long = imageMutex.withLock {
        validateImagePaths(imagePaths)
        val id = db.withTransaction {
            require(db.subjectDao().byId(subjectId) != null) { "该科目已删除，请重新选择科目" }
            val inserted = db.recordDao().insert(
                HomeworkRecord(
                    subjectId = subjectId,
                    typeName = typeName,
                    content = content,
                    deadline = deadline,
                    createdAt = System.currentTimeMillis(),
                    recurringRuleId = validRule(subjectId, ruleId),
                    remindAt = remindAt,
                )
            )
            imagePaths.forEachIndexed { i, p ->
                db.imageDao().insert(RecordImage(recordId = inserted, path = p, ord = i))
            }
            inserted
        }
        refreshReminder()
        id
    }

    suspend fun updateRecord(
        id: Long,
        subjectId: Long,
        typeName: String,
        content: String,
        deadline: Long,
        ruleId: Long?,
        imagePaths: List<String>? = null,
        keepImageIds: List<Long> = emptyList(),
        remindAt: Long? = null,
    ) = AlarmScheduler.withReminderUpdate {
      imageMutex.withLock {
        if (imagePaths != null) validateImagePaths(imagePaths)
        val discardedPaths = mutableListOf<String>()
        var reminderChanged = false
        db.withTransaction {
            val old = db.recordDao().byId(id) ?: error("该作业已不存在，当前内容尚未保存")
            require(db.subjectDao().byId(subjectId) != null) { "该科目已删除，请重新选择科目" }
            val updated = old.copy(
                subjectId = subjectId,
                typeName = typeName,
                content = content,
                deadline = deadline,
                recurringRuleId = validRule(subjectId, ruleId),
                remindAt = remindAt,
            )
            db.recordDao().update(updated)
            reminderChanged = AlarmScheduler.reminderChanged(old, updated, prefLong(AlarmScheduler.PREF_LEAD_MS, AlarmScheduler.DEFAULT_LEAD_MS))
            // 纯文字编辑未传图片清单时保留已有附件；显式传入清单才会变更图片。
            if (imagePaths != null) {
                discardedPaths += db.imageDao().forRecord(id)
                    .filter { it.id !in keepImageIds && it.path !in imagePaths }.map { it.path }
                db.imageDao().deleteForRecord(id)
                imagePaths.forEachIndexed { i, p ->
                    db.imageDao().insert(RecordImage(recordId = id, path = p, ord = i))
                }
            }
        }
        // 文件只能在事务成功后删除，数据库回滚时附件仍然完整。
        removeUnreferencedImagesLocked(discardedPaths)
        // 仅修改文字不会重复提醒；实际提醒时刻变化才清除旧标记。
        if (reminderChanged) clearRemindDone(id)
        refreshReminder()
      }
    }

    suspend fun complete(id: Long) {
        db.recordDao().complete(id, System.currentTimeMillis())
        clearRemindDone(id)
        refreshReminder()
    }

    suspend fun delete(id: Long) {
        val discardedPaths = db.withTransaction {
            val paths = imagesFor(id).map { it.path }
            db.imageDao().deleteForRecord(id)
            db.recordDao().deleteById(id)
            paths
        }
        discardUnreferencedImages(discardedPaths)
        clearRemindDone(id)
        refreshReminder()
    }

    /** 删除超期至少 [TimeFmt.OVERDUE_LIMIT_MS] 的未完成记录及未引用附件，并清理提醒标记。
     * 有记录删除时会重排提醒；返回删除条数，0 表示数据库无变动。
     */
    suspend fun sweepExpired(now: Long = System.currentTimeMillis()): Int {
        val discardedPaths = mutableListOf<String>()
        val removed = db.withTransaction {
            val dead = db.recordDao().expiredIncomplete(now - TimeFmt.OVERDUE_LIMIT_MS)
            dead.forEach { r ->
                discardedPaths += imagesFor(r.id).map { it.path }
                db.imageDao().deleteForRecord(r.id)
                db.recordDao().deleteById(r.id)
            }
            dead
        }
        discardUnreferencedImages(discardedPaths)
        removed.forEach { clearRemindDone(it.id) }
        if (removed.isNotEmpty()) refreshReminder()
        return removed.size
    }

    suspend fun addRule(subjectId: Long, weekdays: String): Long {
        val id = db.withTransaction {
            require(db.subjectDao().byId(subjectId) != null) { "该科目已删除，请重新选择科目" }
            db.ruleDao().insert(RecurringRule(subjectId = subjectId, weekdays = weekdays))
        }
        refreshReminder()
        return id
    }

    suspend fun updateRule(r: RecurringRule) {
        db.ruleDao().update(r)
        refreshReminder()
    }

    suspend fun deleteRule(r: RecurringRule) {
        db.withTransaction {
            db.ruleDao().delete(r)
            // 只改引用列，避免旧快照覆盖刚刚完成或编辑的记录。
            db.recordDao().detachIncompleteRule(r.id)
        }
        refreshReminder()
    }

    suspend fun deleteImage(img: RecordImage) {
        db.imageDao().deleteById(img.id)
        discardUnreferencedImages(listOf(img.path))
    }

    fun allRules() = db.ruleDao().all()

    suspend fun clearAll() {
        db.clearAllTables()
        File(context.filesDir, "images").deleteRecursively()
        prefs.edit().clear().commit()
        DraftStore(context).clear()
        refreshReminder()
    }

    // ---- 提醒偏好（布尔/长整型单值都存 prefs，键定义在 AlarmScheduler）----
    fun pref(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun setPref(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    fun prefLong(key: String, def: Long): Long = prefs.getLong(key, def)
    fun setPrefLong(key: String, v: Long) = prefs.edit().putLong(key, v).apply()

    /**
     * 「已提醒」幂等键：存的是**上次真正提醒过的那一个提醒时刻**（Long，不是布尔）。
     * 这样把提醒时间改掉后，存值与新时刻不同 → 会重新提醒；没改则绝不重复。
     */
    fun remindDone(id: Long): Long = prefs.getLong("rem_done_$id", 0L)
    fun setRemindDone(id: Long, at: Long) = prefs.edit().putLong("rem_done_$id", at).apply()
    fun clearRemindDone(id: Long) = prefs.edit().remove("rem_done_$id").apply()

    /**
     * **提醒全量重排的唯一出口**：任何会改变提醒计划的写操作都必须调用它，否则会漏。
     */
    private fun refreshReminder() = AlarmScheduler.refresh(context)

    suspend fun incompleteNow(): List<HomeworkRecord> = db.recordDao().incompleteNow()

    /** IO调用：先完整复制和验证，再公开文件名；保留原图字节和实际格式。 */
    fun importImage(source: android.net.Uri): String? {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val temporary = File(dir, UUID.randomUUID().toString() + ".part")
        return try {
            val input = context.contentResolver.openInputStream(source) ?: return null
            input.use { stream -> temporary.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_IMAGE_BYTES) { "图片超过32MB" }
                    output.write(buffer, 0, read)
                }
            } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
            val extension = when (bounds.outMimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heif", "image/heic" -> "heic"
                "image/bmp", "image/x-ms-bmp" -> "bmp"
                else -> error("不支持的图片格式")
            }
            val file = File(dir, UUID.randomUUID().toString() + "." + extension)
            check(temporary.renameTo(file)) { "图片保存失败" }
            file.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            temporary.delete()
        }
    }

    private fun privateImageFile(path: String): File? = runCatching {
        File(path).canonicalFile.takeIf {
            it.parentFile == File(context.filesDir, "images").canonicalFile
        }
    }.getOrNull()

    private fun validateImagePaths(paths: List<String>) {
        require(paths.size <= 9 && paths.distinct().size == paths.size) { "每条作业最多9张图片" }
        require(paths.all { privateImageFile(it)?.isFile == true }) { "附件文件已丢失，请移除后重新添加" }
    }

    /** 清理时重新检查草稿与正式记录；晚到回调不能删掉已保存/重新加入的文件。 */
    suspend fun discardUnreferencedImages(paths: List<String>) = withContext(Dispatchers.IO) {
        imageMutex.withLock { removeUnreferencedImagesLocked(paths) }
    }

    /** 崩溃/进程终止遗留的导入副本，至少保留24小时且排除所有正式/草稿引用。 */
    suspend fun sweepOrphanImages(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val stale = File(context.filesDir, "images").listFiles().orEmpty()
            .filter { it.isFile && now - it.lastModified() > DraftStore.TTL_MS }.map { it.path }
        discardUnreferencedImages(stale)
        // 相机返回前进程被终止时留下的临时照片也按24小时回收。
        val cameraDir = File(context.cacheDir, "camera").canonicalFile
        cameraDir.listFiles().orEmpty().filter {
            it.isFile && it.name.startsWith("attachment_") && it.extension == "jpg" &&
                it.canonicalFile.parentFile == cameraDir && now - it.lastModified() > DraftStore.TTL_MS
        }.forEach { it.delete() }
    }

    private suspend fun removeUnreferencedImagesLocked(paths: List<String>) {
        val draftPaths = DraftStore(context).load()?.imagePaths.orEmpty().toSet()
        paths.distinct().filterNot { it in draftPaths }.forEach { path ->
            if (db.imageDao().referenceCount(path) == 0) privateImageFile(path)?.delete()
        }
    }

    companion object {
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
    }
}
