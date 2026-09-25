package com.chase.planboard.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.planboard.PlanBoardApp
import com.chase.planboard.data.Periods
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.PlanNoteEntity
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.data.startDate
import com.chase.planboard.ui.Board
import com.chase.planboard.ui.PlanSummary
import com.chase.planboard.ui.common.Format
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class PlanEditState(
    val plan: PlanEntity,
    /** Ancestors from the top-level plan down to the direct parent. */
    val path: List<PlanEntity>,
    val subPlans: List<PlanSummary>,
    val todos: List<TodoEntity>,
    val notes: List<PlanNoteEntity>,
)

/** A snackbar message, optionally with an "Open" action for a plan. */
data class Message(val text: String, val openPlanId: Long? = null)

/**
 * Edits save as you go. Writes are serialized through a mutex and always applied
 * to the freshest row, so quick successive edits of different fields never clobber each other.
 */
class PlanEditViewModel(
    private val app: PlanBoardApp,
    val planId: Long,
    private val isNew: Boolean,
) : ViewModel() {
    private val repo = app.plans
    private val writeLock = Mutex()
    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    val title = MutableStateFlow("")
    val details = MutableStateFlow("")
    private var textLoaded = false

    val lifeBoardInstalled = app.lifeBoard.isInstalled()
    val lifeBoardAvailable = app.lifeBoard.isAvailable()

    val state: StateFlow<PlanEditState?> =
        combine(repo.allPlans, repo.allTodos, repo.notesFor(planId)) { plans, todos, notes ->
            val byId = plans.associateBy { it.id }
            val plan = byId[planId] ?: return@combine null
            if (!textLoaded) {
                title.value = plan.title
                details.value = plan.details
                textLoaded = true
            }
            val path = generateSequence(plan.parentId?.let { byId[it] }) { p -> p.parentId?.let { byId[it] } }
                .toList()
                .reversed()
            val board = Board.of(plans, todos)
            PlanEditState(
                plan = plan,
                path = path,
                subPlans = board.plans.filter { it.plan.parentId == planId }.map { it.copy(parentTitle = null) },
                todos = todos.filter { it.planId == planId },
                notes = notes,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun mutate(transform: (PlanEntity) -> PlanEntity) {
        app.appScope.launch {
            writeLock.withLock {
                val current = repo.get(planId) ?: return@withLock
                val updated = transform(current)
                if (updated != current) repo.update(updated)
            }
        }
    }

    private fun say(text: String, openPlanId: Long? = null) {
        _messages.trySend(Message(text, openPlanId))
    }

    fun setTitle(value: String) {
        title.value = value
        mutate { it.copy(title = value) }
    }

    fun setDetails(value: String) {
        details.value = value
        mutate { it.copy(details = value) }
    }

    /** Changing scope keeps the plan in the period holding its current date. */
    fun setScope(scope: Scope) = mutate {
        it.copy(
            scope = scope,
            day = Periods.align(scope, it.startDate).toEpochDay(),
            startMinute = if (scope == Scope.DAY) it.startMinute else null,
            endMinute = if (scope == Scope.DAY) it.endMinute else null,
        )
    }

    fun setDate(date: LocalDate) = mutate { it.copy(day = Periods.align(it.scope, date).toEpochDay()) }

    /** Clearing the start time also clears the end time, which only makes sense after a start. */
    fun setTime(minuteOfDay: Int?) = mutate {
        it.copy(startMinute = minuteOfDay, endMinute = if (minuteOfDay == null) null else it.endMinute)
    }

    fun setEndTime(minuteOfDay: Int?) = mutate { it.copy(endMinute = if (it.startMinute == null) null else minuteOfDay) }

    fun setStatus(status: PlanStatus) = mutate { it.copy(status = status) }

    fun cycleSubPlanStatus(sub: PlanEntity) {
        app.appScope.launch {
            val fresh = repo.get(sub.id) ?: return@launch
            repo.update(fresh.copy(status = fresh.status.next()))
        }
    }

    fun setLinkToLifeBoard(on: Boolean) {
        app.appScope.launch {
            val ok = writeLock.withLock {
                val current = repo.get(planId) ?: return@withLock true
                repo.setLinkToLifeBoard(current, on)
            }
            if (!ok) say(CANT_REACH)
        }
    }

    // --- To-dos ---

    fun addTodo(title: String, dueAt: Long?) {
        if (title.isBlank()) return
        app.appScope.launch {
            if (!repo.addTodo(planId, title.trim(), dueAt)) say(CANT_REACH)
        }
    }

    fun setTodoDone(todo: TodoEntity, done: Boolean) {
        app.appScope.launch { repo.getTodo(todo.id)?.let { repo.setTodoDone(it, done) } }
    }

    fun editTodo(todo: TodoEntity, title: String, dueAt: Long?) {
        app.appScope.launch {
            repo.getTodo(todo.id)?.let { repo.updateTodo(it.copy(title = title.trim().ifBlank { it.title }, dueAt = dueAt)) }
        }
    }

    fun deleteTodo(todo: TodoEntity) {
        app.appScope.launch { repo.getTodo(todo.id)?.let { repo.deleteTodo(it) } }
    }

    fun sendTodo(todo: TodoEntity) {
        app.appScope.launch {
            val fresh = repo.getTodo(todo.id) ?: return@launch
            say(if (repo.sendToLifeBoard(fresh)) "Sent to LifeBoard" else CANT_REACH)
        }
    }

    fun unlinkTodo(todo: TodoEntity) {
        app.appScope.launch { repo.getTodo(todo.id)?.let { repo.unlink(it) } }
    }

    fun openInLifeBoard(todo: TodoEntity) {
        if (!app.lifeBoard.open(todo.lifeBoardTaskId)) say("Couldn't open LifeBoard.")
    }

    // --- Progress log ---

    fun addNote(text: String) {
        if (text.isBlank()) return
        app.appScope.launch { repo.addNote(planId, text.trim()) }
    }

    fun deleteNote(note: PlanNoteEntity) {
        app.appScope.launch { repo.deleteNote(note) }
    }

    // --- Duplicate / repeat / delete ---

    fun duplicate(target: LocalDate) {
        app.appScope.launch {
            val copyId = writeLock.withLock { repo.duplicate(planId, target) } ?: return@launch
            val copy = repo.get(copyId) ?: return@launch
            say("Copied to ${Format.period(copy)}", openPlanId = copyId)
        }
    }

    fun repeat(every: Scope, times: Int) {
        app.appScope.launch {
            writeLock.withLock { repo.repeat(planId, every, times) }
            say(if (times == 1) "Made 1 copy" else "Made $times copies")
        }
    }

    fun delete() {
        app.appScope.launch {
            writeLock.withLock { repo.get(planId)?.let { repo.delete(it) } }
        }
    }

    /** A brand-new plan left with no title and nothing inside it is thrown away. */
    fun onLeave() {
        if (!isNew) return
        val s = state.value
        app.appScope.launch {
            writeLock.withLock {
                val p = repo.get(planId) ?: return@withLock
                val empty = p.title.isBlank() && p.details.isBlank() &&
                    s?.subPlans.isNullOrEmpty() && s?.todos.isNullOrEmpty() && s?.notes.isNullOrEmpty()
                if (empty) repo.delete(p)
            }
        }
    }

    companion object {
        const val CANT_REACH = "Couldn't reach LifeBoard. Make sure the latest LifeBoard is installed."
    }
}
