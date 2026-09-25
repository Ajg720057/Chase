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

    private val _people = MutableStateFlow(loadPeople())
    /** Everyone in the household, for marking who's eating what. */
    val people: StateFlow<List<Person>> = _people

    fun setPeople(value: List<Person>) {
        val arr = org.json.JSONArray(value.map { org.json.JSONObject().put("id", it.id).put("name", it.name) })
        prefs.edit().putString(KEY_PEOPLE, arr.toString()).apply()
        _people.value = value
    }

    private fun loadPeople(): List<Person> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(KEY_PEOPLE, null)!!)
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Person(o.getInt("id"), o.getString("name")) } }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(Person(1, "Me"), Person(2, "Wife"))

    private fun parseDate(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()

    private companion object {
        const val KEY_MONDAY = "week_starts_monday"
        const val KEY_GROCERY_WEEK = "grocery_week"
        const val KEY_PEOPLE = "people"
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

data class Person(val id: Int, val name: String)

/** "Me", "Me & Wife", or null when it's everyone. */
fun eatersLabel(ids: Set<Int>?, people: List<Person>): String? {
    if (ids == null) return null
    val names = people.filter { it.id in ids }.map { it.name }
    if (names.isEmpty() || names.size == people.size) return null
    return names.joinToString(" & ")
}
