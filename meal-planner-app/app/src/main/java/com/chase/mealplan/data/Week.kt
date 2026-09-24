package com.chase.mealplan.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Seven days starting on [start]. */
data class Week(val start: LocalDate) {
    val end: LocalDate get() = start.plusDays(6)
    val days: List<LocalDate> get() = (0L..6L).map { start.plusDays(it) }

    fun plusWeeks(n: Long) = Week(start.plusWeeks(n))

    val label: String
        get() {
            val short = DateTimeFormatter.ofPattern("MMM d")
            val endFmt = if (start.month == end.month) DateTimeFormatter.ofPattern("d") else short
            return "${start.format(short)} – ${end.format(endFmt)}"
        }

    companion object {
        fun containing(date: LocalDate, startsMonday: Boolean): Week {
            val first = if (startsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
            return Week(date.with(TemporalAdjusters.previousOrSame(first)))
        }
    }
}
