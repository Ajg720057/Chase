package com.chase.planboard.export

import com.chase.planboard.data.Periods
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.PlanNoteEntity
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.data.startDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What to put in an exported itinerary. [start] and [end] are both included. */
data class ExportOptions(
    val start: LocalDate,
    val end: LocalDate,
    val includeTodos: Boolean = true,
    val includeDetails: Boolean = true,
    val includeNotes: Boolean = false,
    val includeDone: Boolean = true,
    val includeEmptyDays: Boolean = true,
) {
    val dayCount: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    companion object {
        /** Longest range one PDF covers, to keep files a sensible size. */
        const val MAX_DAYS = 366L
    }
}

data class ItineraryPlan(
    val plan: PlanEntity,
    val todos: List<TodoEntity>,
    val notes: List<PlanNoteEntity>,
    val parentTitle: String?,
)

data class ItineraryDay(val date: LocalDate, val plans: List<ItineraryPlan>)

data class Itinerary(
    val start: LocalDate,
    val end: LocalDate,
    val months: List<ItineraryPlan>,
    val weeks: List<ItineraryPlan>,
    val days: List<ItineraryDay>,
) {
    val isEmpty: Boolean get() = months.isEmpty() && weeks.isEmpty() && days.all { it.plans.isEmpty() }
}

object ItineraryBuilder {
    /**
     * Collects the month and week plans whose period overlaps the range, and each day's
     * plans in time order, with their to-dos and progress notes as the options ask.
     */
    fun build(
        plans: List<PlanEntity>,
        todos: List<TodoEntity>,
        notes: List<PlanNoteEntity>,
        options: ExportOptions,
        firstDay: DayOfWeek = Periods.defaultFirstDay(),
    ): Itinerary {
        val start = minOf(options.start, options.end)
        val end = maxOf(options.start, options.end)
        val byId = plans.associateBy { it.id }
        val todosByPlan = if (options.includeTodos) todos.groupBy { it.planId } else emptyMap()
        val notesByPlan = if (options.includeNotes) notes.groupBy { it.planId } else emptyMap()

        fun entry(p: PlanEntity) = ItineraryPlan(
            plan = if (options.includeDetails) p else p.copy(details = ""),
            todos = todosByPlan[p.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id })),
            notes = notesByPlan[p.id].orEmpty().sortedWith(compareBy({ it.createdAt }, { it.id })),
            parentTitle = p.parentId?.let { byId[it] }?.title?.ifBlank { "Untitled plan" },
        )

        val shown = plans.filter { options.includeDone || it.status != PlanStatus.DONE }
        fun overlaps(p: PlanEntity): Boolean {
            val from = Periods.align(p.scope, p.startDate, firstDay)
            val to = Periods.end(p.scope, p.startDate, firstDay)
            return !to.isBefore(start) && !from.isAfter(end)
        }
        val order = compareBy<PlanEntity>({ Periods.align(it.scope, it.startDate, firstDay) }, { it.sortOrder }, { it.id })

        val months = shown.filter { it.scope == Scope.MONTH && overlaps(it) }.sortedWith(order).map(::entry)
        val weeks = shown.filter { it.scope == Scope.WEEK && overlaps(it) }.sortedWith(order).map(::entry)

        val dayPlans = shown.filter { it.scope == Scope.DAY }.groupBy { it.startDate }
        val days = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val sorted = dayPlans[date].orEmpty().sortedWith(
                    compareBy<PlanEntity>({ it.startMinute == null }, { it.startMinute }, { it.sortOrder }, { it.id }),
                )
                ItineraryDay(date, sorted.map(::entry))
            }
            .filter { options.includeEmptyDays || it.plans.isNotEmpty() }
            .toList()

        return Itinerary(start, end, months, weeks, days)
    }
}
