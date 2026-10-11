package com.zy.homeworklog

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import com.zy.homeworklog.data.AppDatabase
import com.zy.homeworklog.data.DraftStore
import com.zy.homeworklog.data.HomeworkRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * 真机上的 Android / Room / 文件检查，使用独立的数据库、文件目录与偏好，不读写用户数据。
 * 由 testInstrumentationRunner 直接启动，需要连接设备（见 README 的测试说明）。
 */
class AttachmentSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val token = "attachment_smoke_${UUID.randomUUID()}"
        val root = File(targetContext.cacheDir, token).apply { mkdirs() }
        val isolated = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("${token}_$name", mode)
            override fun deleteSharedPreferences(name: String): Boolean =
                super.deleteSharedPreferences("${token}_$name")
        }
        val db = Room.inMemoryDatabaseBuilder(isolated, AppDatabase::class.java).build()
        val passed = mutableListOf<String>()
        try {
            runBlocking {
                val repo = HomeworkRepository(db, isolated)
                val source = File(root, "source.png")
                val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                fun import(): String = checkNotNull(repo.importImage(Uri.fromFile(source)))
                val first = import()
                check(File(first).readBytes().contentEquals(source.readBytes()))
                passed += "import preserves original bytes"
                val invalid = File(root, "invalid.txt").apply { writeText("invalid") }
                check(repo.importImage(Uri.fromFile(invalid)) == null)
                check(File(isolated.filesDir, "images").listFiles().orEmpty().none { it.extension == "part" })
                passed += "invalid import cleans partial file"
                val subject = repo.saveSubjectWithSwap(null, "测试科目", 0L, null, 0L)
                val deadline = System.currentTimeMillis() + 86_400_000L
                val id = repo.addRecord(subject, "纸质", "", deadline, null, listOf(first))
                check(repo.imagesFor(id).single().path == first && repo.record(id)?.content == "")
                passed += "image-only record persists"
                repo.updateRecord(id, subject, "纸质", "edited", deadline, null)
                check(repo.imagesFor(id).single().path == first)
                repo.discardUnreferencedImages(listOf(first))
                check(File(first).isFile)
                passed += "legacy edit and referenced cleanup preserve attachment"
                val second = import()
                val draft = DraftStore.Draft(subject, null, "", deadline, true, null, imagePaths = listOf(second))
                val store = DraftStore(isolated)
                store.save(draft)
                check(DraftStore(isolated).load() == draft)
                repo.discardUnreferencedImages(listOf(second))
                check(File(second).isFile)
                passed += "draft restore protects image"
                val failed = runCatching {
                    repo.updateRecord(id, Long.MAX_VALUE, "纸质", "failed", deadline, null, listOf(second))
                }
                check(failed.isFailure && repo.imagesFor(id).single().path == first && File(first).isFile)
                passed += "failed transaction preserves old image"
                store.clear()
                repo.setRemindDone(id, deadline - 86_400_000L)
                repo.updateRecord(id, subject, "纸质", "edited", deadline, null, listOf(second))
                check(!File(first).exists() && File(second).isFile)
                check(repo.remindDone(id) == deadline - 86_400_000L)
                passed += "replacement cleans old image without duplicate reminder"
                repo.complete(id)
                check(repo.imagesFor(id).single().path == second && File(second).isFile)
                passed += "completed history keeps attachment"
                val orphan = import()
                File(orphan).setLastModified(System.currentTimeMillis() - DraftStore.TTL_MS - 1000)
                File(second).setLastModified(System.currentTimeMillis() - DraftStore.TTL_MS - 1000)
                repo.sweepOrphanImages()
                check(!File(orphan).exists() && File(second).isFile)
                passed += "orphan sweep excludes saved attachment"
                repo.delete(id)
                check(!File(second).exists() && repo.imagesFor(id).isEmpty())
                passed += "record deletion cleans attachment"
                val many = List(10) { import() }
                check(runCatching { repo.addRecord(subject, "纸质", "", deadline, null, many) }.isFailure)
                val nineId = repo.addRecord(subject, "纸质", "", deadline, null, many.take(9))
                check(repo.imagesFor(nineId).map { it.path } == many.take(9))
                passed += "nine-image limit and order"
                repo.delete(nineId)
                repo.discardUnreferencedImages(many)
                check(many.none { File(it).exists() })
                passed += "discarded imports are cleaned"
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "\n${passed.size} isolated Android checks passed:\n${passed.joinToString("\n")}\n") })
        } catch (failure: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "\nFAILED after ${passed.size}: ${failure.stackTraceToString()}\n") })
        } finally {
            db.close()
            isolated.deleteSharedPreferences("prefs")
            isolated.deleteSharedPreferences("add_draft")
            // This directory was uniquely created by this runner; never touches user files.
            root.deleteRecursively()
        }
    }
}
