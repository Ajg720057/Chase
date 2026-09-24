package com.chase.mealplan.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

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

    private fun parseDate(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()

    private companion object {
        const val KEY_MONDAY = "week_starts_monday"
        const val KEY_GROCERY_WEEK = "grocery_week"
    }
}
