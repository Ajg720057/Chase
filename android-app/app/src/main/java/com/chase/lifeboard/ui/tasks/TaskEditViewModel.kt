package com.chase.lifeboard.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.data.Recurrence
import com.chase.lifeboard.data.TaskEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TaskEditState(
    val task: TaskEntity,
    /** Ancestors from the top-level task down to the direct parent. */
    val path: List<TaskEntity>,
    val subtasks: List<TaskListItem>,
)

/**
 * Edits save as you go. Writes are serialized through a mutex and always applied
 * to the freshest row, so quick successive edits of different fields never clobber each other.
 */
class TaskEditViewModel(
    private val app: LifeBoardApp,
    val taskId: Long,
    private val isNew: Boolean,
) : ViewModel() {
    private val repo = app.tasks
    private val writeLock = Mutex()

    val title = MutableStateFlow("")
    val notes = MutableStateFlow("")
    private var textLoaded = false

    val state: StateFlow<TaskEditState?> = repo.allTasks.map { all ->
        val byId = all.associateBy { it.id }
        val task = byId[taskId] ?: return@map null
        if (!textLoaded) {
            title.value = task.title
            notes.value = task.notes
            textLoaded = true
        }
        val path = generateSequence(task.parentId?.let { byId[it] }) { t -> t.parentId?.let { byId[it] } }
            .toList()
            .reversed()
        val progress = subtaskProgress(all)
        val subtasks = all.filter { it.parentId == taskId }.map {
            val (done, total) = progress[it.id] ?: (0 to 0)
            TaskListItem(it, done, total)
        }
        TaskEditState(task, path, subtasks)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun mutate(transform: (TaskEntity) -> TaskEntity) {
        app.appScope.launch {
            writeLock.withLock {
                val current = repo.get(taskId) ?: return@withLock
                val updated = transform(current)
                if (updated != current) repo.update(updated)
            }
        }
    }

    fun setTitle(value: String) {
        title.value = value
        mutate { it.copy(title = value) }
    }

    fun setNotes(value: String) {
        notes.value = value
        mutate { it.copy(notes = value) }
    }

    fun setPriority(p: Int) = mutate { it.copy(priority = p) }

    fun setDue(millis: Long?) = mutate {
        if (millis == null) {
            it.copy(dueAt = null, alarmEnabled = false, recurrence = Recurrence.NONE)
        } else {
            it.copy(dueAt = millis)
        }
    }

    fun setAlarm(enabled: Boolean) = mutate { it.copy(alarmEnabled = enabled && it.dueAt != null) }

    fun setRecurrence(r: Recurrence) = mutate { it.copy(recurrence = r) }

    fun toggle(task: TaskEntity, done: Boolean, onRolledForward: (Long) -> Unit) {
        app.appScope.launch {
            writeLock.withLock {
                val fresh = repo.get(task.id) ?: return@withLock
                repo.setCompleted(fresh, done)?.let { next -> viewModelScope.launch { onRolledForward(next) } }
            }
        }
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        app.appScope.launch { repo.create(parentId = taskId, title = title.trim()) }
    }

    fun reorderSubtasks(ids: List<Long>) {
        app.appScope.launch { repo.reorder(ids) }
    }

    fun delete() {
        app.appScope.launch {
            writeLock.withLock { repo.get(taskId)?.let { repo.delete(it) } }
        }
    }

    /** A brand-new task left with no title and nothing inside it is thrown away. */
    fun onLeave() {
        if (!isNew) return
        app.appScope.launch {
            writeLock.withLock {
                val t = repo.get(taskId) ?: return@withLock
                if (t.title.isBlank() && t.notes.isBlank() && state.value?.subtasks.isNullOrEmpty()) repo.delete(t)
            }
        }
    }
}
