package com.chase.planboard

import com.chase.planboard.data.Periods
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.Scope
import com.chase.planboard.data.isIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class PeriodsTest {
    private val sunday = DayOfWeek.SUNDAY
    private val monday = DayOfWeek.MONDAY
    private val wed = LocalDate.of(2026, 9, 23)

    @Test
    fun weekStartFollowsFirstDayOfWeek() {
        assertEquals(LocalDate.of(2026, 9, 20), Periods.weekStart(wed, sunday))
        assertEquals(LocalDate.of(2026, 9, 21), Periods.weekStart(wed, monday))
        // A day that already is the first day of the week stays put.
        assertEquals(LocalDate.of(2026, 9, 21), Periods.weekStart(LocalDate.of(2026, 9, 21), monday))
    }

    @Test
    fun alignAndEndCoverWholePeriods() {
        assertEquals(wed, Periods.align(Scope.DAY, wed, monday))
        assertEquals(LocalDate.of(2026, 9, 1), Periods.align(Scope.MONTH, wed, monday))
        assertEquals(LocalDate.of(2026, 9, 27), Periods.end(Scope.WEEK, wed, monday))
        assertEquals(LocalDate.of(2026, 9, 30), Periods.end(Scope.MONTH, wed, monday))
        assertEquals(LocalDate.of(2028, 2, 29), Periods.end(Scope.MONTH, LocalDate.of(2028, 2, 10), monday))
    }

    @Test
    fun containsChecksThePeriodBounds() {
        val weekStart = LocalDate.of(2026, 9, 21)
        assertTrue(Periods.contains(Scope.WEEK, weekStart, LocalDate.of(2026, 9, 27), monday))
        assertFalse(Periods.contains(Scope.WEEK, weekStart, LocalDate.of(2026, 9, 28), monday))
        assertTrue(Periods.contains(Scope.MONTH, LocalDate.of(2026, 9, 1), wed, monday))
        assertFalse(Periods.contains(Scope.DAY, wed, wed.plusDays(1), monday))
    }

    @Test
    fun stepsBetweenCountsWholePeriods() {
        assertEquals(3L, Periods.stepsBetween(Scope.DAY, wed, wed.plusDays(3), monday))
        // Sunday to the following Monday is one week apart when weeks start on Monday.
        assertEquals(1L, Periods.stepsBetween(Scope.WEEK, LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 28), monday))
        assertEquals(0L, Periods.stepsBetween(Scope.WEEK, LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 28), sunday))
        assertEquals(4L, Periods.stepsBetween(Scope.MONTH, LocalDate.of(2026, 9, 30), LocalDate.of(2027, 1, 1), monday))
        assertEquals(-1L, Periods.stepsBetween(Scope.MONTH, wed, LocalDate.of(2026, 8, 31), monday))
    }

    @Test
    fun shiftClampsMonthEnds() {
        assertEquals(LocalDate.of(2026, 2, 28), Periods.shift(Scope.MONTH, LocalDate.of(2026, 1, 31), 1))
        assertEquals(LocalDate.of(2026, 10, 7), Periods.shift(Scope.WEEK, wed, 2))
    }

    @Test
    fun monthGridIsWholeWeeksCoveringTheMonth() {
        val grid = Periods.monthGrid(YearMonth.of(2026, 9), sunday)
        assertEquals(0, grid.size % 7)
        assertEquals(LocalDate.of(2026, 8, 30), grid.first())
        assertEquals(LocalDate.of(2026, 10, 3), grid.last())
        assertEquals(35, grid.size)

        // February 2026 starts on a Sunday and has exactly four weeks.
        assertEquals(28, Periods.monthGrid(YearMonth.of(2026, 2), sunday).size)
    }

    @Test
    fun planIsInItsOwnPeriodOnly() {
        val weekPlan = PlanEntity(scope = Scope.WEEK, day = LocalDate.of(2026, 9, 21).toEpochDay())
        assertTrue(weekPlan.isIn(Scope.WEEK, LocalDate.of(2026, 9, 27), monday))
        assertFalse(weekPlan.isIn(Scope.WEEK, LocalDate.of(2026, 9, 28), monday))
        assertFalse(weekPlan.isIn(Scope.DAY, LocalDate.of(2026, 9, 21), monday))

        val monthPlan = PlanEntity(scope = Scope.MONTH, day = LocalDate.of(2026, 9, 1).toEpochDay())
        assertTrue(monthPlan.isIn(Scope.MONTH, wed, monday))
        assertFalse(monthPlan.isIn(Scope.MONTH, LocalDate.of(2026, 10, 1), monday))
    }
}
