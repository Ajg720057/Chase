package com.chase.planboard.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports every plan, to-do and progress note to a single .json file the user can keep
 * in Drive/Downloads, and restores from one. Restore replaces all current data.
 */
class Backup(private val context: Context, private val db: AppDatabase) {
    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("app", "PlanBoard")
            put("version", 1)
            put("plans", JSONArray(db.planDao().all().map { it.toJson() }))
            put("todos", JSONArray(db.todoDao().all().map { it.toJson() }))
            put("notes", JSONArray(db.noteDao().all().map { it.toJson() }))
        }
        val out = context.contentResolver.openOutputStream(target) ?: error("Could not open file")
        out.bufferedWriter().use { it.write(json.toString(2)) }
    }

    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: error("Could not open file")
        val data = runCatching { JSONObject(input.bufferedReader().use { it.readText() }) }.getOrNull()
        if (data == null || data.optString("app") != "PlanBoard") error("Not a PlanBoard backup")

        db.withTransaction {
            db.planDao().deleteAll() // to-dos and notes go with their plans
            // Parents must exist before children because of the foreign key.
            val all = data.getJSONArray("plans").objects().map { it.toPlan() }
            val byId = all.associateBy { it.id }
            val inserted = mutableSetOf<Long>()
            val ordered = mutableListOf<PlanEntity>()
            fun visit(p: PlanEntity) {
                if (p.id in inserted) return
                inserted += p.id
                p.parentId?.let { parent -> byId[parent]?.let { visit(it) } }
                ordered += if (p.parentId == null || p.parentId in byId) p else p.copy(parentId = null)
            }
            all.forEach { visit(it) }
            db.planDao().insertAll(ordered)
            db.todoDao().insertAll(data.getJSONArray("todos").objects().map { it.toTodo() }.filter { it.planId in inserted })
            db.noteDao().insertAll(data.getJSONArray("notes").objects().map { it.toNote() }.filter { it.planId in inserted })
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optLongOrNull(key: String) = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optIntOrNull(key: String) = if (!has(key) || isNull(key)) null else getInt(key)

    private fun PlanEntity.toJson() = JSONObject().apply {
        put("id", id); put("parentId", parentId ?: JSONObject.NULL); put("title", title)
        put("details", details); put("scope", scope.name); put("day", day)
        put("startMinute", startMinute ?: JSONObject.NULL); put("endMinute", endMinute ?: JSONObject.NULL); put("status", status.name)
        put("linkToLifeBoard", linkToLifeBoard); put("sortOrder", sortOrder); put("createdAt", createdAt)
    }

    private fun JSONObject.toPlan() = PlanEntity(
        id = getLong("id"), parentId = optLongOrNull("parentId"), title = optString("title"),
        details = optString("details"),
        scope = runCatching { Scope.valueOf(optString("scope")) }.getOrDefault(Scope.DAY),
        day = getLong("day"), startMinute = optIntOrNull("startMinute"),
        endMinute = optIntOrNull("endMinute"),
        status = runCatching { PlanStatus.valueOf(optString("status")) }.getOrDefault(PlanStatus.PLANNED),
        linkToLifeBoard = optBoolean("linkToLifeBoard"), sortOrder = optLong("sortOrder"),
        createdAt = optLong("createdAt"),
    )

    private fun TodoEntity.toJson() = JSONObject().apply {
        put("id", id); put("planId", planId); put("title", title); put("done", done)
        put("dueAt", dueAt ?: JSONObject.NULL); put("lifeBoardTaskId", lifeBoardTaskId ?: JSONObject.NULL)
        put("sortOrder", sortOrder); put("createdAt", createdAt)
    }

    private fun JSONObject.toTodo() = TodoEntity(
        id = getLong("id"), planId = getLong("planId"), title = optString("title"), done = optBoolean("done"),
        dueAt = optLongOrNull("dueAt"), lifeBoardTaskId = optLongOrNull("lifeBoardTaskId"),
        sortOrder = optLong("sortOrder"), createdAt = optLong("createdAt"),
    )

    private fun PlanNoteEntity.toJson() = JSONObject().apply {
        put("id", id); put("planId", planId); put("text", text); put("createdAt", createdAt)
    }

    private fun JSONObject.toNote() = PlanNoteEntity(
        id = getLong("id"), planId = getLong("planId"), text = optString("text"), createdAt = optLong("createdAt"),
    )
}
