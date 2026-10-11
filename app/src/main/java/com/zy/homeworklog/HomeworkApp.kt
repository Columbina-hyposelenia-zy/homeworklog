package com.zy.homeworklog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.zy.homeworklog.data.AppDatabase
import com.zy.homeworklog.data.HomeworkRepository
import com.zy.homeworklog.recurring.RecurringGenerator
import com.zy.homeworklog.reminder.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HomeworkApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db by lazy { AppDatabase.build(this) }
    val repo by lazy { HomeworkRepository(db, this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        appScope.launch {
            try {
                // 先清理超期实例，再判断本周是否需要生成，避免旧实例阻塞整周。
                repo.sweepExpired()
                repo.sweepOrphanImages()
                RecurringGenerator.generate(repo)
                AlarmScheduler.refreshNow(this@HomeworkApp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeworkStartup", "Startup maintenance failed", e)
                AlarmScheduler.refresh(this@HomeworkApp)
            }
        }
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_DEADLINE, "截止提醒", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    companion object {
        const val CHANNEL_DEADLINE = "deadline"
    }
}
