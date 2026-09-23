package com.chase.lifeboard.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.data.JournalEntryWithPhotos
import com.chase.lifeboard.data.Recurrences
import com.chase.lifeboard.data.TaskEntity
import com.chase.lifeboard.ui.common.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** A task shown on a given day. [isProjected] marks a future repeat of a recurring task. */
data class DayTask(val task: TaskEntity, val parentTitle: String?, val isProjected: Boolean)

data class DayMarks(val tasks: Int, val overdue: Boolean, val journal: Int, val mood: Int?)

data class CalendarState(
    val month: YearMonth,
    val selected: LocalDate,
    val marks: Map<LocalDate, DayMarks>,
    val dayTasks: List<DayTask>,
    val dayEntries: List<JournalEntryWithPhotos>,
)

class CalendarViewModel(private val app: LifeBoardApp) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow(LocalDate.now())

    val state: StateFlow<CalendarState?> = combine(
        app.tasks.allTasks, app.journal.allEntries, month, selected,
    ) { tasks, entries, month, selected ->
        val byId = tasks.associateBy { it.id }
        // The grid shows a few days of the neighbouring months, so cover them too.
        val from = month.atDay(1).minusDays(7)
        val to = month.atEndOfMonth().plusDays(14)
        val rangeFrom = minOf(from, selected)
        val rangeTo = maxOf(to, selected)

        val taskDays = mutableMapOf<LocalDate, MutableList<DayTask>>()
        for (t in tasks) {
            val due = t.dueAt ?: continue
            val actualDay = Format.toDate(due)
            val days = if (t.completed) {
                listOf(actualDay).filter { !it.isBefore(rangeFrom) && !it.isAfter(rangeTo) }
            } else {
                Recurrences.occurrencesBetween(due, t.recurrence, rangeFrom, rangeTo)
            }
            val parentTitle = t.parentId?.let { byId[it]?.title?.ifBlank { "Untitled" } }
            days.forEach { d -> taskDays.getOrPut(d) { mutableListOf() } += DayTask(t, parentTitle, d != actualDay) }
        }
        val entriesByDay = entries.groupBy { LocalDate.ofEpochDay(it.entry.day) }

        val today = LocalDate.now()
        val marks = (taskDays.keys + entriesByDay.keys).associateWith { d ->
            val dt = taskDays[d].orEmpty()
            val de = entriesByDay[d].orEmpty()
            DayMarks(
                tasks = dt.count { !it.task.completed },
                overdue = d.isBefore(today) && dt.any { !it.task.completed && !it.isProjected },
                journal = de.size,
                mood = de.firstNotNullOfOrNull { it.entry.mood },
            )
        }
        CalendarState(
            month = month,
            selected = selected,
            marks = marks,
            dayTasks = taskDays[selected].orEmpty().sortedWith(
                compareBy<DayTask> { it.task.completed }.thenBy { Format.toDateTime(it.task.dueAt!!).toLocalTime() },
            ),
            dayEntries = entriesByDay[selected].orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(date: LocalDate) {
        selected.value = date
        month.value = YearMonth.from(date)
    }

    fun shiftMonth(delta: Long) {
        val m = month.value.plusMonths(delta)
        month.value = m
        selected.value = if (m == YearMonth.now()) LocalDate.now() else m.atDay(1)
    }

    fun toggle(task: TaskEntity, done: Boolean) {
        app.appScope.launch { app.tasks.setCompleted(task, done) }
    }
}
