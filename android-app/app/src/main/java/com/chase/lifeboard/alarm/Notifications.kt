package com.chase.lifeboard.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chase.lifeboard.MainActivity
import com.chase.lifeboard.R
import com.chase.lifeboard.data.Priority
import com.chase.lifeboard.data.TaskEntity

object Notifications {
    private const val CHANNEL_ALARMS = "task_alarms"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(CHANNEL_ALARMS, "Task alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarms for tasks with a date and time"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showTaskAlarm(context: Context, task: TaskEntity) {
        if (!canPost(context)) return
        val id = task.id.toInt()
        val open = PendingIntent.getActivity(
            context, id,
            MainActivity.openTaskIntent(context, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(action: String) = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            if (task.priority != Priority.NONE) append("${Priority.label(task.priority)} priority")
            if (task.notes.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(task.notes)
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title.ifBlank { "Task reminder" })
            .setContentText(text.ifBlank { "Tap to open" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifBlank { "Tap to open" }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Done", action(AlarmReceiver.ACTION_DONE))
            .addAction(0, "Snooze 10 min", action(AlarmReceiver.ACTION_SNOOZE))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(context: Context, taskId: Long) = NotificationManagerCompat.from(context).cancel(taskId.toInt())
}
