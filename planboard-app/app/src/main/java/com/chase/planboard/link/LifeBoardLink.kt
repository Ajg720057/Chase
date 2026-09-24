package com.chase.planboard.link

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Talks to the LifeBoard to-do app through the task provider it exposes. LifeBoard only
 * answers apps signed with PlanBoard's key, so nothing else on the phone can use this.
 *
 * Every call returns null/false instead of throwing when LifeBoard is missing, too old to
 * have the provider, or refuses the call; callers show a message and carry on.
 */
class LifeBoardLink(private val context: Context) {
    data class RemoteTask(val id: Long, val title: String, val completed: Boolean)

    fun isInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** True when LifeBoard is installed and new enough to accept tasks. */
    fun isAvailable(): Boolean =
        runCatching { context.packageManager.resolveContentProvider(AUTHORITY, 0) != null }.getOrDefault(false)

    suspend fun add(title: String, notes: String, dueAt: Long?): Long? = call {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_NOTES, notes)
            if (dueAt != null) put(COL_DUE_AT, dueAt) else putNull(COL_DUE_AT)
        }
        context.contentResolver.insert(TASKS_URI, values)?.let { ContentUris.parseId(it) }
    }

    suspend fun setCompleted(id: Long, completed: Boolean): Boolean = call {
        val values = ContentValues().apply { put(COL_COMPLETED, completed) }
        context.contentResolver.update(ContentUris.withAppendedId(TASKS_URI, id), values, null, null) > 0
    } ?: false

    suspend fun update(id: Long, title: String, dueAt: Long?): Boolean = call {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            if (dueAt != null) put(COL_DUE_AT, dueAt) else putNull(COL_DUE_AT)
        }
        context.contentResolver.update(ContentUris.withAppendedId(TASKS_URI, id), values, null, null) > 0
    } ?: false

    suspend fun delete(id: Long): Boolean = call {
        context.contentResolver.delete(ContentUris.withAppendedId(TASKS_URI, id), null, null) > 0
    } ?: false

    /**
     * Current state of the given LifeBoard tasks. Ids missing from the result were deleted
     * in LifeBoard. Returns null when LifeBoard couldn't be asked at all.
     */
    suspend fun fetch(ids: Collection<Long>): Map<Long, RemoteTask>? {
        if (ids.isEmpty()) return emptyMap()
        return call {
            val uri = TASKS_URI.buildUpon().appendQueryParameter(PARAM_IDS, ids.joinToString(",")).build()
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(COL_ID)
                val titleCol = c.getColumnIndexOrThrow(COL_TITLE)
                val doneCol = c.getColumnIndexOrThrow(COL_COMPLETED)
                buildMap {
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        put(id, RemoteTask(id, c.getString(titleCol).orEmpty(), c.getInt(doneCol) != 0))
                    }
                }
            }
        }
    }

    /** Opens the task in LifeBoard, or LifeBoard's main screen if [taskId] is null. */
    fun open(taskId: Long?): Boolean {
        val intent = Intent()
            .setClassName(PACKAGE, "$PACKAGE.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (taskId != null) {
            intent.setData(Uri.parse("lifeboard://task/$taskId")).putExtra("open_task", taskId)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private suspend fun <T> call(block: () -> T?): T? = withContext(Dispatchers.IO) {
        runCatching(block).getOrNull()
    }

    companion object {
        const val PACKAGE = "com.chase.lifeboard"
        const val AUTHORITY = "com.chase.lifeboard.tasks"
        val TASKS_URI: Uri = Uri.parse("content://$AUTHORITY/tasks")
        const val PARAM_IDS = "ids"
        const val COL_ID = "_id"
        const val COL_TITLE = "title"
        const val COL_NOTES = "notes"
        const val COL_DUE_AT = "due_at"
        const val COL_COMPLETED = "completed"
    }
}
