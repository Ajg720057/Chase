package com.chase.lifeboard.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chase.lifeboard.data.TaskEntity

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Makes the system alarm for [task] match its current state. */
    fun sync(task: TaskEntity) {
        val due = task.dueAt
        if (task.alarmEnabled && !task.completed && due != null && due > System.currentTimeMillis()) {
            set(due, pendingIntent(task.id, AlarmReceiver.ACTION_FIRE))
        } else {
            cancel(task.id)
        }
    }

    fun snooze(taskId: Long, at: Long) = set(at, pendingIntent(taskId, AlarmReceiver.ACTION_SNOOZE_FIRE))

    fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId, AlarmReceiver.ACTION_FIRE))
        alarmManager.cancel(pendingIntent(taskId, AlarmReceiver.ACTION_SNOOZE_FIRE))
    }

    private fun set(at: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(taskId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
