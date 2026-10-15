package com.lookup.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Works out which app is on screen from the system's usage events. Only package names
 * and timestamps are read; nothing about what is inside the app.
 */
class ForegroundTracker(context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var lastQueryEnd = System.currentTimeMillis() - LOOKBACK_MS
    private var current: String? = null

    fun foregroundPackage(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQueryEnd, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> current = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == current) current = null
            }
        }
        lastQueryEnd = now
        return current
    }

    private companion object {
        const val LOOKBACK_MS = 10 * 60 * 1000L
    }
}
