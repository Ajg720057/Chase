package com.chase.planboard.data

import androidx.room.withTransaction
import com.chase.planboard.link.LifeBoardLink
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PlanRepository(
    private val db: AppDatabase,
    private val link: LifeBoardLink,
) {
    private val plans = db.planDao()
    private val todos = db.todoDao()
    private val notes = db.noteDao()

    val allPlans: Flow<List<PlanEntity>> = plans.observeAll()
    val allTodos: Flow<List<TodoEntity>> = todos.observeAll()

    fun notesFor(planId: Long): Flow<List<PlanNoteEntity>> = notes.observeFor(planId)

    suspend fun get(id: Long): PlanEntity? = plans.get(id)

    suspend fun create(scope: Scope, date: LocalDate, parentId: Long? = null, title: String = ""): Long =
        plans.insert(
            PlanEntity(
                parentId = parentId,
                title = title,
                scope = scope,
                day = Periods.align(scope, date).toEpochDay(),
                sortOrder = plans.maxSortOrder() + 1,
            ),
        )

    suspend fun update(plan: PlanEntity) = plans.update(plan)

    /** Deletes a plan, its sub-plans, and the LifeBoard tasks its to-dos created. */
    suspend fun delete(plan: PlanEntity) {
        (listOf(plan) + descendants(plan.id)).forEach { p ->
            todos.forPlan(p.id).forEach { t -> t.lifeBoardTaskId?.let { link.delete(it) } }
        }
        plans.delete(plan) // sub-plans, to-dos and notes go with it via foreign-key cascades
    }

    // --- To-dos ---

    /** @return false if the plan sends to-dos to LifeBoard and LifeBoard couldn't be reached. */
    suspend fun addTodo(planId: Long, title: String, dueAt: Long? = null): Boolean {
        val id = todos.insert(
            TodoEntity(planId = planId, title = title, dueAt = dueAt, sortOrder = todos.maxSortOrder(planId) + 1),
        )
        val plan = plans.get(planId) ?: return true
        if (!plan.linkToLifeBoard) return true
        return todos.get(id)?.let { sendToLifeBoard(it) } ?: true
    }

    suspend fun getTodo(id: Long): TodoEntity? = todos.get(id)

    suspend fun updateTodo(todo: TodoEntity) {
        todos.update(todo)
        todo.lifeBoardTaskId?.let { link.update(it, todo.title, todo.dueAt) }
    }

    suspend fun setTodoDone(todo: TodoEntity, done: Boolean) {
        todos.update(todo.copy(done = done))
        todo.lifeBoardTaskId?.let { link.setCompleted(it, done) }
    }

    suspend fun deleteTodo(todo: TodoEntity) {
        todo.lifeBoardTaskId?.let { link.delete(it) }
        todos.delete(todo)
    }

    /** Creates a matching task in LifeBoard. @return false if LifeBoard couldn't be reached. */
    suspend fun sendToLifeBoard(todo: TodoEntity): Boolean {
        if (todo.lifeBoardTaskId != null) return true
        val plan = plans.get(todo.planId)
        val notes = plan?.let { "From plan: ${it.title.ifBlank { "Untitled plan" }}" }.orEmpty()
        val remoteId = link.add(todo.title, notes, todo.dueAt) ?: return false
        if (todo.done) link.setCompleted(remoteId, true)
        todos.get(todo.id)?.let { todos.update(it.copy(lifeBoardTaskId = remoteId)) }
        return true
    }

    /** Stops syncing a to-do; the LifeBoard task is left as it is. */
    suspend fun unlink(todo: TodoEntity) = todos.update(todo.copy(lifeBoardTaskId = null))

    /**
     * Turns LifeBoard sending on or off for a plan. Turning it on sends every open to-do
     * that isn't linked yet. @return false if some couldn't be sent.
     */
    suspend fun setLinkToLifeBoard(plan: PlanEntity, on: Boolean): Boolean {
        plans.update(plan.copy(linkToLifeBoard = on))
        if (!on) return true
        var ok = true
        todos.forPlan(plan.id).filter { it.lifeBoardTaskId == null && !it.done }.forEach {
            ok = sendToLifeBoard(it) && ok
        }
        return ok
    }

    /**
     * Pulls check-offs, renames and deletions made in LifeBoard into linked to-dos.
     * A to-do whose LifeBoard task was deleted keeps its place here but is unlinked.
     */
    suspend fun syncWithLifeBoard() {
        val linked = todos.linked()
        if (linked.isEmpty()) return
        val remote = link.fetch(linked.mapNotNull { it.lifeBoardTaskId }) ?: return
        linked.forEach { todo ->
            val task = todo.lifeBoardTaskId?.let { remote[it] }
            val updated = when {
                task == null -> todo.copy(lifeBoardTaskId = null)
                else -> todo.copy(done = task.completed, title = task.title.ifBlank { todo.title })
            }
            if (updated != todo) todos.update(updated)
        }
    }

    // --- Progress log ---

    suspend fun addNote(planId: Long, text: String) {
        notes.insert(PlanNoteEntity(planId = planId, text = text))
    }

    suspend fun deleteNote(note: PlanNoteEntity) = notes.delete(note)

    // --- Duplicate / repeat ---

    /** Copies a plan with its sub-plans and to-dos so it starts in the period holding [target]. */
    suspend fun duplicate(planId: Long, target: LocalDate): Long? {
        val plan = plans.get(planId) ?: return null
        val n = Periods.stepsBetween(plan.scope, plan.startDate, target)
        return copyTree(plan, plan.scope, n)
    }

    /** Makes [times] more copies of a plan, each one [every] day/week/month after the last. */
    suspend fun repeat(planId: Long, every: Scope, times: Int) {
        val plan = plans.get(planId) ?: return
        for (i in 1..times) copyTree(plan, every, i.toLong())
    }

    private suspend fun copyTree(root: PlanEntity, unit: Scope, n: Long): Long {
        val zone = ZoneId.systemDefault()
        fun shiftDay(p: PlanEntity) = Periods.align(p.scope, Periods.shift(unit, p.startDate, n))
        fun shiftMillis(millis: Long): Long {
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            val moved = Periods.shift(unit, dt.toLocalDate(), n).atTime(dt.toLocalTime())
            return moved.atZone(zone).toInstant().toEpochMilli()
        }

        // A copy stays under the same parent only if it still falls inside the parent's period.
        val parent = root.parentId?.let { plans.get(it) }
        val rootDay = shiftDay(root)
        val keepParent = parent != null && Periods.contains(parent.scope, parent.startDate, rootDay)

        val toSend = mutableListOf<Long>()
        val newRootId = db.withTransaction {
            suspend fun copyPlan(p: PlanEntity, newParent: Long?): Long {
                val newId = plans.insert(
                    p.copy(
                        id = 0,
                        parentId = newParent,
                        day = shiftDay(p).toEpochDay(),
                        status = PlanStatus.PLANNED,
                        sortOrder = plans.maxSortOrder() + 1,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                todos.forPlan(p.id).forEach { t ->
                    val id = todos.insert(
                        t.copy(id = 0, planId = newId, done = false, lifeBoardTaskId = null, dueAt = t.dueAt?.let(::shiftMillis)),
                    )
                    if (p.linkToLifeBoard) toSend += id
                }
                plans.children(p.id).forEach { copyPlan(it, newId) }
                return newId
            }
            copyPlan(root, if (keepParent) root.parentId else null)
        }
        toSend.forEach { id -> todos.get(id)?.let { sendToLifeBoard(it) } }
        return newRootId
    }

    private suspend fun descendants(id: Long): List<PlanEntity> {
        val out = mutableListOf<PlanEntity>()
        val queue = ArrayDeque(plans.children(id))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            out += p
            queue.addAll(plans.children(p.id))
        }
        return out
    }
}
