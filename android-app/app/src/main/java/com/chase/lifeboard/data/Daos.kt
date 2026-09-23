package com.chase.lifeboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, id")
    suspend fun children(parentId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM tasks WHERE parentId IS :parentId")
    suspend fun minSortOrder(parentId: Long?): Long

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks WHERE parentId IS :parentId")
    suspend fun maxSortOrder(parentId: Long?): Long

    @Query("SELECT * FROM tasks WHERE alarmEnabled = 1 AND completed = 0 AND dueAt IS NOT NULL")
    suspend fun withAlarms(): List<TaskEntity>

    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Long)

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface JournalDao {
    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY day DESC, createdAt DESC")
    fun observeAll(): Flow<List<JournalEntryWithPhotos>>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :id")
    fun observe(id: Long): Flow<JournalEntryWithPhotos?>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun get(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries")
    suspend fun allEntries(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_photos")
    suspend fun allPhotos(): List<JournalPhotoEntity>

    @Query("SELECT * FROM journal_photos WHERE entryId = :entryId")
    suspend fun photosFor(entryId: Long): List<JournalPhotoEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM journal_photos WHERE entryId = :entryId")
    suspend fun maxPhotoOrder(entryId: Long): Int

    @Insert
    suspend fun insert(entry: JournalEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<JournalEntryEntity>)

    @Update
    suspend fun update(entry: JournalEntryEntity)

    @Delete
    suspend fun delete(entry: JournalEntryEntity)

    @Insert
    suspend fun insertPhoto(photo: JournalPhotoEntity): Long

    @Insert
    suspend fun insertPhotos(photos: List<JournalPhotoEntity>)

    @Delete
    suspend fun deletePhoto(photo: JournalPhotoEntity)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}
