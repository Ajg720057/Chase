package com.chase.planboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** How much time a plan covers. */
enum class Scope(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
}

enum class PlanStatus(val label: String) {
    PLANNED("Planned"),
    IN_PROGRESS("In progress"),
    DONE("Done"),
    ;

    /** Tapping the status icon walks Planned → In progress → Done → Planned. */
    fun next(): PlanStatus = entries[(ordinal + 1) % entries.size]
}

/**
 * A plan for a day, a week or a month. Sub-plans are plans with a [parentId], so a
 * month goal can be broken into week plans, and those into day plans, to any depth.
 */
@Entity(
    tableName = "plans",
    indices = [Index("parentId"), Index("day")],
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val title: String = "",
    val details: String = "",
    val scope: Scope = Scope.DAY,
    /**
     * LocalDate.toEpochDay() of the first day the plan covers: the day itself,
     * the first day of the week, or the 1st of the month. See [Periods.align].
     */
    val day: Long,
    /** Minutes after midnight for a day plan with a set time, otherwise null. */
    val startMinute: Int? = null,
    /**
     * Optional end time, in minutes after midnight, for a day plan that has a start time.
     * An end at or before the start means the plan runs past midnight.
     */
    val endMinute: Int? = null,
    val status: PlanStatus = PlanStatus.PLANNED,
    /** When on, every to-do added to this plan is also sent to LifeBoard. */
    val linkToLifeBoard: Boolean = false,
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "todos",
    indices = [Index("planId")],
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val title: String = "",
    val done: Boolean = false,
    /** Epoch millis of the due date/time, or null. */
    val dueAt: Long? = null,
    /** The matching task's id in LifeBoard once this to-do has been sent there. */
    val lifeBoardTaskId: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One dated entry in a plan's progress log. */
@Entity(
    tableName = "plan_notes",
    indices = [Index("planId")],
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlanNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)
