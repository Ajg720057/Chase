package com.chase.mealplan.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _weekStartsMonday = MutableStateFlow(prefs.getBoolean(KEY_MONDAY, false))
    val weekStartsMonday: StateFlow<Boolean> = _weekStartsMonday

    fun setWeekStartsMonday(value: Boolean) {
        prefs.edit().putBoolean(KEY_MONDAY, value).apply()
        _weekStartsMonday.value = value
    }

    private val _groceryWeek = MutableStateFlow(prefs.getString(KEY_GROCERY_WEEK, null)?.let(::parseDate))
    /** First day of the week the grocery list was last built from. */
    val groceryWeek: StateFlow<LocalDate?> = _groceryWeek

    fun setGroceryWeek(start: LocalDate?) {
        prefs.edit().putString(KEY_GROCERY_WEEK, start?.toString()).apply()
        _groceryWeek.value = start
    }

    private val _reminder = MutableStateFlow(
        ReminderSettings(
            enabled = prefs.getBoolean(KEY_REMINDER_ON, true),
            day = runCatching { DayOfWeek.valueOf(prefs.getString(KEY_REMINDER_DAY, null) ?: "") }
                .getOrDefault(DayOfWeek.FRIDAY),
            minuteOfDay = prefs.getInt(KEY_REMINDER_TIME, 13 * 60),
        ),
    )
    /** The weekly "plan next week" reminder. On by default: Fridays at 1 PM. */
    val reminder: StateFlow<ReminderSettings> = _reminder

    fun setReminder(value: ReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_REMINDER_ON, value.enabled)
            .putString(KEY_REMINDER_DAY, value.day.name)
            .putInt(KEY_REMINDER_TIME, value.minuteOfDay)
            .apply()
        _reminder.value = value
    }

    private fun parseDate(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()

    private companion object {
        const val KEY_MONDAY = "week_starts_monday"
        const val KEY_GROCERY_WEEK = "grocery_week"
        const val KEY_REMINDER_ON = "reminder_on"
        const val KEY_REMINDER_DAY = "reminder_day"
        const val KEY_REMINDER_TIME = "reminder_minute_of_day"
    }
}

data class ReminderSettings(val enabled: Boolean, val day: DayOfWeek, val minuteOfDay: Int) {
    val time: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    /** "Fridays at 1:00 PM" */
    val label: String
        get() = "${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}s at " +
            time.format(DateTimeFormatter.ofPattern("h:mm a"))
}
