package com.chase.planboard

import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.PlanNoteEntity
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.export.ExportOptions
import com.chase.planboard.export.ItineraryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ItineraryBuilderTest {
    private val monday = DayOfWeek.MONDAY
    private fun d(day: Int) = LocalDate.of(2026, 9, day)

    private val month = PlanEntity(id = 1, title = "September goals", scope = Scope.MONTH, day = d(1).toEpochDay())
    private val octMonth = PlanEntity(id = 2, title = "October", scope = Scope.MONTH, day = LocalDate.of(2026, 10, 1).toEpochDay())
    private val week = PlanEntity(id = 3, title = "Trip week", scope = Scope.WEEK, day = d(21).toEpochDay(), parentId = 1)
    private val lateWeek = PlanEntity(id = 4, title = "Next week", scope = Scope.WEEK, day = d(28).toEpochDay())
    private val morning = PlanEntity(id = 5, title = "Flight", scope = Scope.DAY, day = d(22).toEpochDay(), startMinute = 8 * 60, parentId = 3)
    private val anyTime = PlanEntity(id = 6, title = "Pack", scope = Scope.DAY, day = d(22).toEpochDay(), details = "Light bag", sortOrder = -1)
    private val evening = PlanEntity(id = 7, title = "Dinner", scope = Scope.DAY, day = d(22).toEpochDay(), startMinute = 19 * 60)
    private val finished = PlanEntity(id = 8, title = "Book hotel", scope = Scope.DAY, day = d(23).toEpochDay(), status = PlanStatus.DONE)
    private val plans = listOf(month, octMonth, week, lateWeek, morning, anyTime, evening, finished)

    private val todos = listOf(
        TodoEntity(id = 2, planId = 5, title = "Check in", sortOrder = 2),
        TodoEntity(id = 1, planId = 5, title = "Print pass", sortOrder = 1),
    )
    private val notes = listOf(PlanNoteEntity(id = 1, planId = 5, text = "Seat 12A", createdAt = 5))

    private fun build(options: ExportOptions) = ItineraryBuilder.build(plans, todos, notes, options, monday)

    @Test
    fun weekRangePicksOverlappingPlansAndEveryDay() {
        val it = build(ExportOptions(d(21), d(27)))
        assertEquals(listOf("September goals"), it.months.map { m -> m.plan.title })
        assertEquals(listOf("Trip week"), it.weeks.map { w -> w.plan.title })
        assertEquals(7, it.days.size)
        assertEquals("September goals", it.weeks.single().parentTitle)
    }

    @Test
    fun dayPlansAreInTimeOrderWithUntimedLast() {
        val day = build(ExportOptions(d(22), d(22))).days.single()
        assertEquals(listOf("Flight", "Dinner", "Pack"), day.plans.map { it.plan.title })
    }

    @Test
    fun todosAndNotesFollowOptions() {
        val flight = build(ExportOptions(d(22), d(22), includeNotes = true)).days.single().plans.first()
        assertEquals(listOf("Print pass", "Check in"), flight.todos.map { it.title })
        assertEquals(listOf("Seat 12A"), flight.notes.map { it.text })

        val bare = build(ExportOptions(d(22), d(22), includeTodos = false, includeDetails = false)).days.single()
        assertTrue(bare.plans.all { it.todos.isEmpty() && it.notes.isEmpty() && it.plan.details.isEmpty() })
    }

    @Test
    fun finishedPlansAndEmptyDaysCanBeLeftOut() {
        val it = build(ExportOptions(d(21), d(27), includeDone = false, includeEmptyDays = false))
        assertEquals(listOf(d(22)), it.days.map { day -> day.date })
    }

    @Test
    fun reversedRangeIsSwappedAndCrossesMonths() {
        val it = build(ExportOptions(LocalDate.of(2026, 10, 2), d(29)))
        assertEquals(d(29), it.start)
        assertEquals(listOf("September goals", "October"), it.months.map { m -> m.plan.title })
        assertEquals(listOf("Next week"), it.weeks.map { w -> w.plan.title })
        assertEquals(4, it.days.size)
    }
}
