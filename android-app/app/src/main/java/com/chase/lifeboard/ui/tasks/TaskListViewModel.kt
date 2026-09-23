package com.chase.lifeboard.ui.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.data.TaskEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskSort(val label: String) {
    MANUAL("My order (drag)"),
    PRIORITY("Priority"),
    DUE("Due date"),
}

enum class TaskFilter { ACTIVE, DONE }

data class TaskListItem(val task: TaskEntity, val subtasksDone: Int, val subtasksTotal: Int)

data class TaskListState(
    val items: List<TaskListItem>,
    val activeCount: Int,
    val doneCount: Int,
    /** Every top-level task id in saved manual order, used to merge a reorder of a filtered view. */
    val manualOrder: List<Long>,
)

/** Direct subtasks' progress for every parent task. */
fun subtaskProgress(all: List<TaskEntity>): Map<Long, Pair<Int, Int>> =
    all.filter { it.parentId != null }
        .groupBy { it.parentId!! }
        .mapValues { (_, kids) -> kids.count { it.completed } to kids.size }

class TaskListViewModel(private val app: LifeBoardApp) : ViewModel() {
    private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    val sort = MutableStateFlow(
        runCatching { TaskSort.valueOf(prefs.getString("task_sort", null)!!) }.getOrDefault(TaskSort.MANUAL),
    )
    val filter = MutableStateFlow(TaskFilter.ACTIVE)

    val state: StateFlow<TaskListState?> = combine(app.tasks.allTasks, sort, filter) { all, sort, filter ->
        val progress = subtaskProgress(all)
        val top = all.filter { it.parentId == null }
        val visible = top.filter { it.completed == (filter == TaskFilter.DONE) }
        val sorted = when {
            filter == TaskFilter.DONE -> visible.sortedByDescending { it.completedAt ?: 0 }
            sort == TaskSort.PRIORITY -> visible.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder },
            )
            sort == TaskSort.DUE -> visible.sortedWith(
                compareBy<TaskEntity> { it.dueAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.priority }
                    .thenBy { it.sortOrder },
            )
            else -> visible // already in sortOrder from the query
        }
        TaskListState(
            items = sorted.map {
                val (done, total) = progress[it.id] ?: (0 to 0)
                TaskListItem(it, done, total)
            },
            activeCount = top.count { !it.completed },
            doneCount = top.count { it.completed },
            manualOrder = top.map { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(s: TaskSort) {
        sort.value = s
        prefs.edit().putString("task_sort", s.name).apply()
    }

    fun setFilter(f: TaskFilter) {
        filter.value = f
    }

    fun toggle(task: TaskEntity, done: Boolean, onRolledForward: (Long) -> Unit) {
        app.appScope.launch {
            app.tasks.setCompleted(task, done)?.let { next -> viewModelScope.launch { onRolledForward(next) } }
        }
    }

    /** Saves a drag-reorder of the visible tasks, keeping hidden (e.g. completed) tasks in their slots. */
    fun reorder(visibleIds: List<Long>) {
        val full = state.value?.manualOrder ?: return
        val visibleSet = visibleIds.toSet()
        val iter = visibleIds.iterator()
        val merged = full.map { if (it in visibleSet && iter.hasNext()) iter.next() else it }
        app.appScope.launch { app.tasks.reorder(merged) }
    }
}
