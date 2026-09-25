package com.chase.planboard.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Date math for days, weeks and months. Weeks start on the phone's locale default. */
object Periods {
    fun defaultFirstDay(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    fun weekStart(date: LocalDate, firstDay: DayOfWeek = defaultFirstDay()): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    /** The first day of the [scope]-sized period that contains [date]. */
    fun align(scope: Scope, date: LocalDate, firstDay: DayOfWeek = defaultFirstDay()): LocalDate = when (scope) {
        Scope.DAY -> date
        Scope.WEEK -> weekStart(date, firstDay)
        Scope.MONTH -> date.withDayOfMonth(1)
    }

    /** Last day of the [scope]-sized period starting at (or containing) [date]. */
    fun end(scope: Scope, date: LocalDate, firstDay: DayOfWeek = defaultFirstDay()): LocalDate = when (scope) {
        Scope.DAY -> date
        Scope.WEEK -> weekStart(date, firstDay).plusDays(6)
        Scope.MONTH -> YearMonth.from(date).atEndOfMonth()
    }

    fun contains(scope: Scope, start: LocalDate, date: LocalDate, firstDay: DayOfWeek = defaultFirstDay()): Boolean =
        !date.isBefore(align(scope, start, firstDay)) && !date.isAfter(end(scope, start, firstDay))

    /** Moves [date] by [n] days, weeks or months. */
    fun shift(unit: Scope, date: LocalDate, n: Long): LocalDate = when (unit) {
        Scope.DAY -> date.plusDays(n)
        Scope.WEEK -> date.plusWeeks(n)
        Scope.MONTH -> date.plusMonths(n)
    }

    /** Whole days, weeks or months from the period holding [from] to the one holding [to]. */
    fun stepsBetween(unit: Scope, from: LocalDate, to: LocalDate, firstDay: DayOfWeek = defaultFirstDay()): Long =
        when (unit) {
            Scope.DAY -> ChronoUnit.DAYS.between(from, to)
            Scope.WEEK -> ChronoUnit.WEEKS.between(weekStart(from, firstDay), weekStart(to, firstDay))
            Scope.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to))
        }

    /** Every day shown on a month calendar: whole weeks from the one holding the 1st. */
    fun monthGrid(month: YearMonth, firstDay: DayOfWeek = defaultFirstDay()): List<LocalDate> {
        val start = weekStart(month.atDay(1), firstDay)
        val end = weekStart(month.atEndOfMonth(), firstDay).plusDays(6)
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    fun weekDays(anyDay: LocalDate, firstDay: DayOfWeek = defaultFirstDay()): List<LocalDate> {
        val start = weekStart(anyDay, firstDay)
        return (0L until 7L).map { start.plusDays(it) }
    }
}

/** True when [plan] belongs on the [scope]-sized period that contains [date]. */
fun PlanEntity.isIn(scope: Scope, date: LocalDate, firstDay: DayOfWeek = Periods.defaultFirstDay()): Boolean =
    this.scope == scope && Periods.align(scope, LocalDate.ofEpochDay(day), firstDay) == Periods.align(scope, date, firstDay)

val PlanEntity.startDate: LocalDate get() = LocalDate.ofEpochDay(day)

/**
 * How long a plan with a start and end time lasts, in minutes. An end at or before the
 * start runs past midnight, so 10 PM to 1 AM is 3 hours. Null without both times.
 */
val PlanEntity.durationMinutes: Int?
    get() {
        val start = startMinute ?: return null
        val end = endMinute ?: return null
        val diff = Math.floorMod(end - start, 24 * 60)
        return if (diff == 0) 24 * 60 else diff
    }

/** True when the plan's end time falls on the day after it starts. */
val PlanEntity.endsNextDay: Boolean
    get() {
        val start = startMinute ?: return false
        val end = endMinute ?: return false
        return end <= start
    }
