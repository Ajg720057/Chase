package com.chase.planboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.planboard.PlanBoardApp
import com.chase.planboard.data.Periods
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.data.isIn
import com.chase.planboard.ui.common.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A plan plus the counts shown on its card. */
data class PlanSummary(
    val plan: PlanEntity,
    val todosDone: Int,
    val todosTotal: Int,
    val subPlans: Int,
    val parentTitle: String?,
)

data class DueTodo(val todo: TodoEntity, val plan: PlanEntity?)

/** Everything the Day, Week and Month tabs draw from, with lookups by period. */
data class Board(val plans: List<PlanSummary>, val todos: List<TodoEntity>) {
    private val planById = plans.associateBy { it.plan.id }

    fun plansIn(scope: Scope, date: LocalDate): List<PlanSummary> = plans.filter { it.plan.isIn(scope, date) }

    /** Day plans grouped by date, for the calendar's per-day counts. */
    val dayPlansByDate: Map<LocalDate, List<PlanSummary>> =
        plans.filter { it.plan.scope == Scope.DAY }.groupBy { LocalDate.ofEpochDay(it.plan.day) }

    fun todosDueOn(date: LocalDate): List<DueTodo> = todos
        .filter { t -> t.dueAt?.let { Format.toDate(it) } == date }
        .sortedBy { it.dueAt }
        .map { DueTodo(it, planById[it.planId]?.plan) }

    companion object {
        fun of(plans: List<PlanEntity>, todos: List<TodoEntity>): Board {
            val byId = plans.associateBy { it.id }
            val todosByPlan = todos.groupBy { it.planId }
            val childCount = plans.groupingBy { it.parentId }.eachCount()
            return Board(
                plans = plans.map { p ->
                    val own = todosByPlan[p.id].orEmpty()
                    PlanSummary(
                        plan = p,
                        todosDone = own.count { it.done },
                        todosTotal = own.size,
                        subPlans = childCount[p.id] ?: 0,
                        parentTitle = p.parentId?.let { byId[it] }?.title?.ifBlank { "Untitled plan" },
                    )
                },
                todos = todos,
            )
        }
    }
}

/** Shared by the three calendar tabs so they all look at the same date. */
class BoardViewModel(private val app: PlanBoardApp) : ViewModel() {
    private val repo = app.plans

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val board: StateFlow<Board?> = combine(repo.allPlans, repo.allTodos) { plans, todos -> Board.of(plans, todos) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun select(date: LocalDate) {
        _date.value = date
    }

    fun shift(unit: Scope, n: Long) {
        _date.value = Periods.shift(unit, _date.value, n)
    }

    fun cycleStatus(plan: PlanEntity) {
        app.appScope.launch {
            val fresh = repo.get(plan.id) ?: return@launch
            repo.update(fresh.copy(status = fresh.status.next()))
        }
    }

    fun setTodoDone(todo: TodoEntity, done: Boolean) {
        app.appScope.launch { repo.getTodo(todo.id)?.let { repo.setTodoDone(it, done) } }
    }
}
