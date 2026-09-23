package com.chase.lifeboard.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class Recurrence(val label: String) {
    NONE("Does not repeat"),
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

object Priority {
    const val NONE = 0
    const val LOW = 1
    const val MEDIUM = 2
    const val HIGH = 3

    fun label(p: Int) = when (p) {
        HIGH -> "High"
        MEDIUM -> "Medium"
        LOW -> "Low"
        else -> "None"
    }
}

/**
 * A task or subtask. Subtasks are just tasks with a [parentId], so they can be
 * nested to any depth and each can carry its own due date, alarm and priority.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("parentId"), Index("dueAt")],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val title: String = "",
    val notes: String = "",
    val priority: Int = Priority.NONE,
    /** Epoch millis of the due date/time, or null when the task has no date. */
    val dueAt: Long? = null,
    val alarmEnabled: Boolean = false,
    val recurrence: Recurrence = Recurrence.NONE,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "journal_entries", indices = [Index("day")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay() of the day the entry is about. */
    val day: Long,
    val title: String = "",
    val body: String = "",
    /** 1 (awful) .. 5 (great), or null when no mood was picked. */
    val mood: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "journal_photos",
    indices = [Index("entryId")],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class JournalPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    /** File name inside the app's private photos directory. */
    val fileName: String,
    val sortOrder: Int = 0,
)

data class JournalEntryWithPhotos(
    @Embedded val entry: JournalEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val photos: List<JournalPhotoEntity>,
)

object Moods {
    val all = listOf(1 to "😞", 2 to "🙁", 3 to "😐", 4 to "🙂", 5 to "😄")
    val labels = mapOf(1 to "Awful", 2 to "Bad", 3 to "Okay", 4 to "Good", 5 to "Great")
    fun emoji(mood: Int?) = all.firstOrNull { it.first == mood }?.second
}
