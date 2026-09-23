package com.chase.lifeboard.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports everything (tasks, journal, photos) to a single .zip the user can keep
 * in Drive/Downloads, and restores from one. Restore replaces all current data.
 */
class Backup(
    private val context: Context,
    private val db: AppDatabase,
    private val tasks: TaskRepository,
    private val journal: JournalRepository,
) {
    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val taskDao = db.taskDao()
        val journalDao = db.journalDao()
        val json = JSONObject().apply {
            put("version", 1)
            put("tasks", JSONArray(taskDao.all().map { it.toJson() }))
            put("journal", JSONArray(journalDao.allEntries().map { it.toJson() }))
            put("photos", JSONArray(journalDao.allPhotos().map { it.toJson() }))
        }
        val out = context.contentResolver.openOutputStream(target) ?: error("Could not open file")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.toString().toByteArray())
            zip.closeEntry()
            journal.photosDir.listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("photos/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        var json: JSONObject? = null
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val input = context.contentResolver.openInputStream(source) ?: error("Could not open file")
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when {
                    e.name == "data.json" -> json = JSONObject(zip.readBytes().decodeToString())
                    e.name.startsWith("photos/") && !e.isDirectory -> {
                        val name = File(e.name).name
                        File(staging, name).outputStream().use { zip.copyTo(it) }
                    }
                }
                e = zip.nextEntry
            }
        }
        val data = json ?: error("Not a LifeBoard backup")

        tasks.cancelAllAlarms()
        db.withTransaction {
            db.taskDao().deleteAll()
            db.journalDao().deleteAll()
            // Parents must exist before children because of the foreign key.
            val all = data.getJSONArray("tasks").objects().map { it.toTask() }
            val byId = all.associateBy { it.id }
            val inserted = mutableSetOf<Long>()
            fun insertOrder(t: TaskEntity, acc: MutableList<TaskEntity>) {
                if (t.id in inserted) return
                t.parentId?.let { p -> byId[p]?.let { insertOrder(it, acc) } }
                if (t.parentId == null || t.parentId in byId) {
                    inserted += t.id
                    acc += t
                }
            }
            val ordered = mutableListOf<TaskEntity>()
            all.forEach { insertOrder(it, ordered) }
            db.taskDao().insertAll(ordered)
            db.journalDao().insertAll(data.getJSONArray("journal").objects().map { it.toEntry() })
            db.journalDao().insertPhotos(data.getJSONArray("photos").objects().map { it.toPhoto() })
        }
        journal.photosDir.deleteRecursively()
        journal.photosDir.mkdirs()
        staging.listFiles()?.forEach { it.copyTo(File(journal.photosDir, it.name), overwrite = true) }
        staging.deleteRecursively()
        tasks.rescheduleAll()
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optLongOrNull(key: String) = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optIntOrNull(key: String) = if (isNull(key) || !has(key)) null else getInt(key)

    private fun TaskEntity.toJson() = JSONObject().apply {
        put("id", id); put("parentId", parentId ?: JSONObject.NULL); put("title", title)
        put("notes", notes); put("priority", priority); put("dueAt", dueAt ?: JSONObject.NULL)
        put("alarmEnabled", alarmEnabled); put("recurrence", recurrence.name)
        put("completed", completed); put("completedAt", completedAt ?: JSONObject.NULL)
        put("sortOrder", sortOrder); put("createdAt", createdAt)
    }

    private fun JSONObject.toTask() = TaskEntity(
        id = getLong("id"), parentId = optLongOrNull("parentId"), title = optString("title"),
        notes = optString("notes"), priority = optInt("priority"), dueAt = optLongOrNull("dueAt"),
        alarmEnabled = optBoolean("alarmEnabled"),
        recurrence = runCatching { Recurrence.valueOf(optString("recurrence")) }.getOrDefault(Recurrence.NONE),
        completed = optBoolean("completed"), completedAt = optLongOrNull("completedAt"),
        sortOrder = optLong("sortOrder"), createdAt = optLong("createdAt"),
    )

    private fun JournalEntryEntity.toJson() = JSONObject().apply {
        put("id", id); put("day", day); put("title", title); put("body", body)
        put("mood", mood ?: JSONObject.NULL); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toEntry() = JournalEntryEntity(
        id = getLong("id"), day = getLong("day"), title = optString("title"), body = optString("body"),
        mood = optIntOrNull("mood"), createdAt = optLong("createdAt"), updatedAt = optLong("updatedAt"),
    )

    private fun JournalPhotoEntity.toJson() = JSONObject().apply {
        put("id", id); put("entryId", entryId); put("fileName", fileName); put("sortOrder", sortOrder)
    }

    private fun JSONObject.toPhoto() = JournalPhotoEntity(
        id = getLong("id"), entryId = getLong("entryId"), fileName = getString("fileName"),
        sortOrder = optInt("sortOrder"),
    )
}
