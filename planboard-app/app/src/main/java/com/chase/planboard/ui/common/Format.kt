package com.chase.planboard.ui.common

import com.chase.planboard.data.Periods
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.Scope
import com.chase.planboard.data.startDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Format {
    private val zone get() = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayShort = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val dayWithYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val monthDay = DateTimeFormatter.ofPattern("MMM d")
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val stamp = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun toDateTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun toDate(millis: Long): LocalDate = toDateTime(millis).toLocalDate()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    fun relativeDay(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> if (date.year == today.year) date.format(dayShort) else date.format(dayWithYear)
        }
    }

    fun longDay(date: LocalDate): String = date.format(dayLong)
    fun month(month: YearMonth): String = month.format(monthYear)

    /** "Sep 21 – 27" or "Sep 28 – Oct 4". */
    fun week(anyDay: LocalDate): String {
        val start = Periods.weekStart(anyDay)
        val end = start.plusDays(6)
        val endText = if (end.month == start.month) end.dayOfMonth.toString() else end.format(monthDay)
        val year = if (end.year != LocalDate.now().year) ", ${end.year}" else ""
        return "${start.format(monthDay)} – $endText$year"
    }

    /** What period a plan covers, e.g. "Today", "Week of Sep 21", "September 2026". */
    fun period(scope: Scope, date: LocalDate): String = when (scope) {
        Scope.DAY -> relativeDay(date)
        Scope.WEEK -> "Week of ${Periods.weekStart(date).format(monthDay)}"
        Scope.MONTH -> month(YearMonth.from(date))
    }

    fun period(plan: PlanEntity): String = period(plan.scope, plan.startDate)

    fun minuteOfDay(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(time)

    fun time(millis: Long): String = toDateTime(millis).format(time)

    fun due(millis: Long): String {
        val dt = toDateTime(millis)
        return "${relativeDay(dt.toLocalDate())}, ${dt.format(time)}"
    }

    fun stamp(millis: Long): String {
        val dt = toDateTime(millis)
        return "${dt.format(stamp)}, ${dt.format(time)}"
    }
}
