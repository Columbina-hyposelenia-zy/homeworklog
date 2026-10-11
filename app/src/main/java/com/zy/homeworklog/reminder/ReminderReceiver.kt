package com.zy.homeworklog.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zy.homeworklog.HomeworkApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * 闹钟到点接收器：发通知 + 立刻重排下一个提醒。
 * 用 goAsync() 把工作挪到协程里，避免超过广播的 10 秒上限。
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HomeworkApp ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                AlarmScheduler.fireDue(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Unable to deliver reminders", e)
            } finally {
                pending.finish()
            }
        }
    }
}
