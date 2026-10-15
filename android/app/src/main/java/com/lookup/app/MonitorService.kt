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
 *
 * - Once a sitting reaches the threshold, a quote card pops up over the app.
 * - The card offers "5 more minutes" up to [MAX_SNOOZES] times per sitting. After that the
 *   only button leaves the app.
 * - Leaving a tracked app after being nudged starts a [COOLDOWN_MS] break. Opening any
 *   tracked app during the break brings the card straight back, with no snooze option.
 * - A sitting without nudges ends after [BREAK_RESET_MS] away from all tracked apps; one
 *   with nudges ends when its break runs out.
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
    private var snoozesUsed = 0
    private var nudgesThisSitting = 0
    private var wasOnSocial = false
    private var lastBreakNoticeAt = 0L

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
            if (!overlay.isShowing) {
                if (now < prefs.cooldownUntil) {
                    breakReminder(now)
                } else if (sessionMs >= nextNudgeAtMs) {
                    nudge(pkg!!)
                    // If they keep going without choosing, nudge again after the snooze time.
                    nextNudgeAtMs = sessionMs + SNOOZE_MS
                }
            }
        } else {
            if (wasOnSocial && nudgesThisSitting > 0) {
                // Just left after being nudged: start (or restart) the break.
                prefs.cooldownUntil = now + COOLDOWN_MS
            }
            if (sessionMs > 0 && now - lastSocialAt > BREAK_RESET_MS && now >= prefs.cooldownUntil) {
                sessionMs = 0
                nextNudgeAtMs = thresholdMs()
                snoozesUsed = 0
                nudgesThisSitting = 0
            }
        }
        wasOnSocial = onSocial
    }

    private fun nudge(pkg: String) {
        prefs.countNudge()
        nudgesThisSitting++
        val quote = Quotes.random()
        val minutes = (sessionMs / 60_000).toInt()
        val header = getString(R.string.nudge_header, minutes, appLabel(pkg))
        val snoozesLeft = MAX_SNOOZES - snoozesUsed
        if (Settings.canDrawOverlays(this)) {
            overlay.show(
                quote, header,
                hint = if (snoozesLeft == 0) getString(R.string.out_of_snoozes) else null,
                snoozesLeft = snoozesLeft,
                onSnooze = {
                    snoozesUsed++
                    nextNudgeAtMs = sessionMs + SNOOZE_MS
                },
            )
        } else {
            quoteNotification(quote, header)
        }
    }

    /** They came back to a tracked app during the break. No snoozing out of this one. */
    private fun breakReminder(now: Long) {
        val overlayAllowed = Settings.canDrawOverlays(this)
        // Without the overlay there's nothing on screen to wait on, so don't re-notify every tick.
        if (!overlayAllowed && now - lastBreakNoticeAt < 60_000) return
        lastBreakNoticeAt = now
        prefs.countNudge()
        nudgesThisSitting++
        val quote = Quotes.random()
        val minsLeft = ((prefs.cooldownUntil - now + 59_999) / 60_000).toInt()
        val minsAgo = ((COOLDOWN_MS / 60_000) - minsLeft).toInt().coerceAtLeast(0)
        if (overlayAllowed) {
            overlay.show(
                quote, getString(R.string.cooldown_header),
                hint = getString(R.string.cooldown_hint, minsAgo, minsLeft),
                snoozesLeft = 0,
                onSnooze = {},
            )
        } else {
            quoteNotification(quote, getString(R.string.cooldown_header))
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

    private fun quoteNotification(quote: Quote, title: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(this, CH_QUOTE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
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
        private const val MAX_SNOOZES = 3
        private const val COOLDOWN_MS = 10 * 60_000L
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
