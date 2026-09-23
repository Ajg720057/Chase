package com.chase.lifeboard.data

import com.chase.lifeboard.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val dao: TaskDao,
    private val scheduler: AlarmScheduler,
) {
    val allTasks: Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun allTasksOnce(): List<TaskEntity> = dao.all()

    fun observe(id: Long): Flow<TaskEntity?> = dao.observe(id)

    suspend fun get(id: Long): TaskEntity? = dao.get(id)

    /** New top-level tasks go to the top of the list; new subtasks go to the bottom. */
    suspend fun create(
        parentId: Long? = null,
        title: String = "",
        dueAt: Long? = null,
    ): Long {
        val order = if (parentId == null) dao.minSortOrder(null) - 1 else dao.maxSortOrder(parentId) + 1
        return dao.insert(
            TaskEntity(parentId = parentId, title = title, dueAt = dueAt, sortOrder = order),
        )
    }

    suspend fun update(task: TaskEntity) {
        dao.update(task)
        scheduler.sync(task)
    }

    /**
     * Checks or unchecks a task. Checking a repeating task that has a date moves it
     * to its next occurrence instead (and resets its subtasks). Checking a parent
     * also checks everything beneath it.
     *
     * @return the new due time if a repeating task rolled forward, otherwise null.
     */
    suspend fun setCompleted(task: TaskEntity, completed: Boolean): Long? {
        if (completed && task.recurrence != Recurrence.NONE && task.dueAt != null) {
            val next = Recurrences.next(task.dueAt, task.recurrence)
            update(task.copy(dueAt = next, completed = false, completedAt = null))
            descendants(task.id).forEach { update(it.copy(completed = false, completedAt = null)) }
            return next
        }
        val now = System.currentTimeMillis()
        update(task.copy(completed = completed, completedAt = if (completed) now else null))
        if (completed) {
            descendants(task.id).filter { !it.completed }.forEach {
                update(it.copy(completed = true, completedAt = now))
            }
        }
        return null
    }

    suspend fun delete(task: TaskEntity) {
        scheduler.cancel(task.id)
        descendants(task.id).forEach { scheduler.cancel(it.id) }
        dao.delete(task) // children are removed by the foreign-key cascade
    }

    /** Persists a new manual order for a set of siblings. */
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> dao.setSortOrder(id, index.toLong()) }
    }

    suspend fun rescheduleAll() {
        dao.withAlarms().forEach { scheduler.sync(it) }
    }

    suspend fun cancelAllAlarms() {
        dao.withAlarms().forEach { scheduler.cancel(it.id) }
    }

    private suspend fun descendants(id: Long): List<TaskEntity> {
        val out = mutableListOf<TaskEntity>()
        val queue = ArrayDeque(dao.children(id))
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            out += t
            queue.addAll(dao.children(t.id))
        }
        return out
    }
}
