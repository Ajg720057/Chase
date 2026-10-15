package com.lookup.app

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Everything the app stores: settings plus today's running total. All local. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("lookup", Context.MODE_PRIVATE)

    var monitoringEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    var thresholdMinutes: Int
        get() = sp.getInt(KEY_THRESHOLD, 10)
        set(v) = sp.edit().putInt(KEY_THRESHOLD, v).apply()

    var trackedPackages: Set<String>
        get() = sp.getStringSet(KEY_TRACKED, null) ?: SocialApps.DEFAULT_PACKAGES
        set(v) = sp.edit().putStringSet(KEY_TRACKED, v.toSet()).apply()

    /**
     * End of the current enforced break (epoch ms). Saved so a restart can't skip it.
     * Opening a tracked app before then brings the quote straight back.
     */
    var cooldownUntil: Long
        get() = sp.getLong(KEY_COOLDOWN_UNTIL, 0)
        set(v) = sp.edit().putLong(KEY_COOLDOWN_UNTIL, v).apply()

    /** Milliseconds spent in tracked apps today. Resets at midnight. */
    val todayMs: Long
        get() = if (sp.getString(KEY_DAY, null) == today()) sp.getLong(KEY_TODAY_MS, 0) else 0

    val todayNudges: Int
        get() = if (sp.getString(KEY_DAY, null) == today()) sp.getInt(KEY_TODAY_NUDGES, 0) else 0

    fun addToday(ms: Long) {
        rollDay()
        sp.edit().putLong(KEY_TODAY_MS, sp.getLong(KEY_TODAY_MS, 0) + ms).apply()
    }

    fun countNudge() {
        rollDay()
        sp.edit().putInt(KEY_TODAY_NUDGES, sp.getInt(KEY_TODAY_NUDGES, 0) + 1).apply()
    }

    private fun rollDay() {
        val d = today()
        if (sp.getString(KEY_DAY, null) != d) {
            sp.edit().putString(KEY_DAY, d).putLong(KEY_TODAY_MS, 0).putInt(KEY_TODAY_NUDGES, 0).apply()
        }
    }

    private fun today() = LocalDate.now().toString()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_THRESHOLD = "threshold_min"
        const val KEY_TRACKED = "tracked"
        const val KEY_DAY = "day"
        const val KEY_TODAY_MS = "today_ms"
        const val KEY_TODAY_NUDGES = "today_nudges"
        const val KEY_COOLDOWN_UNTIL = "cooldown_until"
    }
}
