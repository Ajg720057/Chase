package com.chase.lifeboard.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Recurrences {
    private fun step(dt: LocalDateTime, r: Recurrence): LocalDateTime = when (r) {
        Recurrence.NONE -> dt
        Recurrence.DAILY -> dt.plusDays(1)
        Recurrence.WEEKDAYS -> {
            var next = dt.plusDays(1)
            while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                next = next.plusDays(1)
            }
            next
        }
        Recurrence.WEEKLY -> dt.plusWeeks(1)
        Recurrence.MONTHLY -> dt.plusMonths(1)
        Recurrence.YEARLY -> dt.plusYears(1)
    }

    /**
     * The next occurrence after [dueAt] that is also in the future, so an overdue
     * daily task jumps straight to its next upcoming day instead of yesterday.
     */
    fun next(dueAt: Long, r: Recurrence, now: Long = System.currentTimeMillis()): Long {
        if (r == Recurrence.NONE) return dueAt
        val zone = ZoneId.systemDefault()
        // Step from the original anchor so month-end dates don't drift (Jan 31 -> Feb 28 -> Mar 31).
        val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueAt), zone)
        var n = 1L
        var candidate = stepN(anchor, r, n)
        while (candidate.atZone(zone).toInstant().toEpochMilli() <= now) {
            n++
            candidate = stepN(anchor, r, n)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    private fun stepN(anchor: LocalDateTime, r: Recurrence, n: Long): LocalDateTime = when (r) {
        Recurrence.MONTHLY -> anchor.plusMonths(n)
        Recurrence.YEARLY -> anchor.plusYears(n)
        Recurrence.WEEKLY -> anchor.plusWeeks(n)
        Recurrence.DAILY -> anchor.plusDays(n)
        Recurrence.WEEKDAYS -> {
            var dt = anchor
            repeat(n.toInt()) { dt = step(dt, r) }
            dt
        }
        Recurrence.NONE -> anchor
    }

    /** All days in [from]..[to] on which a task due at [dueAt] with rule [r] occurs. */
    fun occurrencesBetween(dueAt: Long, r: Recurrence, from: LocalDate, to: LocalDate): List<LocalDate> {
        val zone = ZoneId.systemDefault()
        val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueAt), zone)
        val result = mutableListOf<LocalDate>()
        if (r == Recurrence.NONE) {
            val d = anchor.toLocalDate()
            if (!d.isBefore(from) && !d.isAfter(to)) result += d
            return result
        }
        var n = 0L
        var dt = anchor
        while (!dt.toLocalDate().isAfter(to) && result.size < 400) {
            if (!dt.toLocalDate().isBefore(from)) result += dt.toLocalDate()
            n++
            dt = if (r == Recurrence.WEEKDAYS) step(dt, r) else stepN(anchor, r, n)
        }
        return result
    }
}
