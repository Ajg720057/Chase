package com.chase.lifeboard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chase.lifeboard.LifeBoardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId < 0) return
        val app = context.applicationContext as LifeBoardApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE, ACTION_SNOOZE_FIRE -> {
                        val task = app.tasks.get(taskId)
                        if (task != null && !task.completed) Notifications.showTaskAlarm(context, task)
                    }
                    ACTION_DONE -> {
                        Notifications.cancel(context, taskId)
                        app.tasks.get(taskId)?.let { app.tasks.setCompleted(it, true) }
                    }
                    ACTION_SNOOZE -> {
                        Notifications.cancel(context, taskId)
                        app.alarmScheduler.snooze(taskId, System.currentTimeMillis() + SNOOZE_MILLIS)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val ACTION_FIRE = "com.chase.lifeboard.ALARM_FIRE"
        const val ACTION_SNOOZE_FIRE = "com.chase.lifeboard.ALARM_SNOOZE_FIRE"
        const val ACTION_DONE = "com.chase.lifeboard.ALARM_DONE"
        const val ACTION_SNOOZE = "com.chase.lifeboard.ALARM_SNOOZE"
        private const val SNOOZE_MILLIS = 10 * 60 * 1000L
    }
}
