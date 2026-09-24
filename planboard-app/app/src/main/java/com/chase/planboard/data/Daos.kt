package com.chase.planboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY day, startMinute IS NULL, startMinute, sortOrder, id")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun get(id: Long): PlanEntity?

    @Query("SELECT * FROM plans WHERE parentId = :parentId ORDER BY day, sortOrder, id")
    suspend fun children(parentId: Long): List<PlanEntity>

    @Query("SELECT * FROM plans")
    suspend fun all(): List<PlanEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM plans")
    suspend fun maxSortOrder(): Long

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Insert
    suspend fun insertAll(plans: List<PlanEntity>)

    @Update
    suspend fun update(plan: PlanEntity)

    @Delete
    suspend fun delete(plan: PlanEntity)

    @Query("DELETE FROM plans")
    suspend fun deleteAll()
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: Long): TodoEntity?

    @Query("SELECT * FROM todos WHERE planId = :planId ORDER BY sortOrder, id")
    suspend fun forPlan(planId: Long): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE lifeBoardTaskId IS NOT NULL")
    suspend fun linked(): List<TodoEntity>

    @Query("SELECT * FROM todos")
    suspend fun all(): List<TodoEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM todos WHERE planId = :planId")
    suspend fun maxSortOrder(planId: Long): Long

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Insert
    suspend fun insertAll(todos: List<TodoEntity>)

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM plan_notes WHERE planId = :planId ORDER BY createdAt DESC, id DESC")
    fun observeFor(planId: Long): Flow<List<PlanNoteEntity>>

    @Query("SELECT * FROM plan_notes")
    suspend fun all(): List<PlanNoteEntity>

    @Insert
    suspend fun insert(note: PlanNoteEntity): Long

    @Insert
    suspend fun insertAll(notes: List<PlanNoteEntity>)

    @Delete
    suspend fun delete(note: PlanNoteEntity)
}
