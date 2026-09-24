package com.chase.mealplan.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chase.mealplan.MainActivity
import com.chase.mealplan.MealPlanApp
import com.chase.mealplan.R
import com.chase.mealplan.data.ReminderSettings
import com.chase.mealplan.data.Slot
import com.chase.mealplan.data.Week
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The weekly "time to plan next week" notification. */
object Reminders {
    private const val CHANNEL = "planning"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_ALARM = 10
    private const val REQUEST_OPEN = 11

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Weekly planning reminder", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Reminds you to plan next week's meals and grocery list" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Next time the reminder should go off, strictly after [now]. */
    fun nextTime(settings: ReminderSettings, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var at = now.toLocalDate().with(TemporalAdjusters.nextOrSame(settings.day)).atTime(settings.time)
        if (!at.isAfter(now)) at = at.plusWeeks(1)
        return at
    }

    /** Sets (or cancels) the alarm to match the current settings. */
    fun schedule(context: Context, settings: ReminderSettings) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_ALARM, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(pending)
        if (!settings.enabled) return
        val at = nextTime(settings).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact but allowed while the phone is idle: a few minutes' leeway is fine for this.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun show(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NEXT_WEEK, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MealPlanApp
        val done = goAsync()
        app.appScope.launch {
            try {
                val settings = app.settings.reminder.value
                val next = Week.containing(LocalDate.now().plusWeeks(1), app.settings.weekStartsMonday.value)
                val planned = app.repo.observeWeek(next).first()
                val filled = planned.map { it.entry.date to it.entry.slot }
                    .filter { it.second != Slot.SNACK }.distinct().size
                val text = if (filled == 0) {
                    "Nothing planned yet for ${next.label}. Tap to pick meals and build your grocery list."
                } else {
                    "$filled of 21 meals planned for ${next.label}. Tap to finish up and build your grocery list."
                }
                Reminders.show(context, "Plan next week's meals", text)
                Reminders.schedule(context, settings)
            } finally {
                done.finish()
            }
        }
    }
}

/** Alarms are forgotten on reboot, app update and clock changes, so set it again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MealPlanApp
        Reminders.schedule(context, app.settings.reminder.value)
    }
}
