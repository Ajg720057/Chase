package com.chase.workoutlog.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class AppData(
    val entries: List<WorkoutEntry> = emptyList(),
    val customExercises: List<Exercise> = emptyList(),
)

/**
 * Stores everything in a single JSON file in the app's private storage.
 * Workout logs are small, so the whole file is loaded once and rewritten on each change.
 */
class WorkoutRepository(context: Context) {
    private val file = File(context.filesDir, "workouts.json")
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    suspend fun saveEntry(entry: WorkoutEntry) = mutate { data ->
        val others = data.entries.filterNot { it.id == entry.id }
        data.copy(entries = others + entry)
    }

    suspend fun deleteEntry(id: Long) = mutate { data ->
        data.copy(entries = data.entries.filterNot { it.id == id })
    }

    suspend fun addCustomExercise(exercise: Exercise) = mutate { data ->
        data.copy(customExercises = data.customExercises + exercise)
    }

    suspend fun deleteCustomExercise(id: String) = mutate { data ->
        data.copy(customExercises = data.customExercises.filterNot { it.id == id })
    }

    private suspend fun mutate(change: (AppData) -> AppData) {
        writeLock.withLock {
            _data.update(change)
            val json = encode(_data.value)
            withContext(Dispatchers.IO) {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(file)
            }
        }
    }

    private fun load(): AppData =
        try {
            if (file.exists()) decode(file.readText()) else AppData()
        } catch (e: Exception) {
            // Keep the unreadable file around rather than silently overwriting it.
            file.copyTo(File(file.parentFile, "workouts-corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
            AppData()
        }

    companion object {
        fun encode(data: AppData): String {
            val root = JSONObject()
            root.put("version", 1)
            root.put("entries", JSONArray().apply {
                data.entries.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("exerciseId", e.exerciseId)
                        put("exerciseName", e.exerciseName)
                        put("type", e.type.name)
                        put("date", e.date.toString())
                        put("durationSeconds", e.durationSeconds)
                        put("distanceMiles", e.distanceMiles)
                        put("notes", e.notes)
                        put("sets", JSONArray().apply {
                            e.sets.forEach { s ->
                                put(JSONObject().apply {
                                    put("reps", s.reps)
                                    put("weight", s.weight)
                                    put("seconds", s.seconds)
                                })
                            }
                        })
                    })
                }
            })
            root.put("customExercises", JSONArray().apply {
                data.customExercises.forEach { x ->
                    put(JSONObject().apply {
                        put("id", x.id)
                        put("name", x.name)
                        put("category", x.category.name)
                        put("type", x.type.name)
                    })
                }
            })
            return root.toString()
        }

        fun decode(text: String): AppData {
            val root = JSONObject(text)
            val entries = root.optJSONArray("entries") ?: JSONArray()
            val customs = root.optJSONArray("customExercises") ?: JSONArray()
            return AppData(
                entries = (0 until entries.length()).map { i ->
                    val o = entries.getJSONObject(i)
                    val sets = o.optJSONArray("sets") ?: JSONArray()
                    WorkoutEntry(
                        id = o.getLong("id"),
                        exerciseId = o.getString("exerciseId"),
                        exerciseName = o.optString("exerciseName", o.getString("exerciseId")),
                        type = ExerciseType.valueOf(o.getString("type")),
                        date = LocalDate.parse(o.getString("date")),
                        durationSeconds = o.optInt("durationSeconds"),
                        distanceMiles = o.optDouble("distanceMiles", 0.0),
                        notes = o.optString("notes"),
                        sets = (0 until sets.length()).map { j ->
                            val s = sets.getJSONObject(j)
                            SetEntry(
                                reps = s.optInt("reps"),
                                weight = s.optDouble("weight", 0.0),
                                seconds = s.optInt("seconds"),
                            )
                        },
                    )
                },
                customExercises = (0 until customs.length()).map { i ->
                    val o = customs.getJSONObject(i)
                    Exercise(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        category = Category.valueOf(o.getString("category")),
                        type = ExerciseType.valueOf(o.getString("type")),
                        isCustom = true,
                    )
                },
            )
        }
    }
}
