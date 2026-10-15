package com.lookup.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Checks every few seconds whether a tracked app is on screen and adds up the time.
 * Once a sitting reaches the threshold, a quote card pops up over the app.
 * A sitting ends after a few minutes away from all tracked apps.
 */
class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var tracker: ForegroundTracker
    private lateinit var overlay: QuoteOverlay

    private var lastTick = 0L
    private var lastSocialAt = 0L
    private var sessionMs = 0L
    private var nextNudgeAtMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        tracker = ForegroundTracker(this)
        overlay = QuoteOverlay(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = statusNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_STATUS, n)
        }
        if (lastTick == 0L) {
            lastTick = System.currentTimeMillis()
            nextNudgeAtMs = thresholdMs()
            handler.post(tick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        overlay.dismiss()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun thresholdMs() = prefs.thresholdMinutes * 60_000L

    private fun onTick() {
        val now = System.currentTimeMillis()
        // Cap the step so a long pause in ticking (deep sleep) isn't counted as usage.
        val dt = (now - lastTick).coerceIn(0, TICK_MS * 3)
        lastTick = now

        val screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        val pkg = if (screenOn) tracker.foregroundPackage() else null
        val onSocial = pkg != null && pkg in prefs.trackedPackages

        if (onSocial) {
            sessionMs += dt
            lastSocialAt = now
            prefs.addToday(dt)
            if (sessionMs >= nextNudgeAtMs && !overlay.isShowing) {
                nudge(pkg!!)
                // If they keep going without choosing, nudge again after the snooze time.
                nextNudgeAtMs = sessionMs + SNOOZE_MS
            }
        } else if (sessionMs > 0 && now - lastSocialAt > BREAK_RESET_MS) {
            sessionMs = 0
            nextNudgeAtMs = thresholdMs()
        }
    }

    private fun nudge(pkg: String) {
        prefs.countNudge()
        val quote = Quotes.random()
        val minutes = (sessionMs / 60_000).toInt()
        val appName = appLabel(pkg)
        if (Settings.canDrawOverlays(this)) {
            overlay.show(quote, minutes, appName, onSnooze = {
                nextNudgeAtMs = sessionMs + SNOOZE_MS
            })
        } else {
            quoteNotification(quote, minutes, appName)
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        "social media"
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, getString(R.string.channel_status), NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_QUOTE, getString(R.string.channel_quotes), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    private fun statusNotification(): Notification =
        NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.status_title))
            .setContentText(getString(R.string.status_text, prefs.thresholdMinutes))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .build()

    private fun quoteNotification(quote: Quote, minutes: Int, appName: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(this, CH_QUOTE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.nudge_header, minutes, appName))
            .setContentText(quote.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("“${quote.text}”\n— ${quote.author}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_QUOTE, n)
        } catch (e: SecurityException) {
            // Notification permission revoked; nothing else to do.
        }
    }

    companion object {
        private const val TICK_MS = 5_000L
        private const val SNOOZE_MS = 5 * 60_000L
        private const val BREAK_RESET_MS = 3 * 60_000L
        private const val CH_STATUS = "status"
        private const val CH_QUOTE = "quotes"
        private const val NOTIF_STATUS = 1
        private const val NOTIF_QUOTE = 2

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
