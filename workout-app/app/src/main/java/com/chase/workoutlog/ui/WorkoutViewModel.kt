package com.chase.workoutlog.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chase.workoutlog.analytics.Analytics
import com.chase.workoutlog.data.AppData
import com.chase.workoutlog.data.Category
import com.chase.workoutlog.data.Exercise
import com.chase.workoutlog.data.ExerciseCatalog
import com.chase.workoutlog.data.ExerciseType
import com.chase.workoutlog.data.WorkoutEntry
import com.chase.workoutlog.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = WorkoutRepository(app)

    val data: StateFlow<AppData> = repo.data

    val exercises: StateFlow<List<Exercise>> = repo.data
        .map { ExerciseCatalog.builtIn + it.customExercises }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExerciseCatalog.builtIn + repo.data.value.customExercises)

    fun exercise(id: String): Exercise? =
        ExerciseCatalog.builtIn.firstOrNull { it.id == id } ?: data.value.customExercises.firstOrNull { it.id == id }

    fun entriesFor(exerciseId: String): List<WorkoutEntry> =
        data.value.entries.filter { it.exerciseId == exerciseId }.sortedWith(compareBy({ it.date }, { it.id }))

    fun lastEntryFor(exerciseId: String): WorkoutEntry? = entriesFor(exerciseId).lastOrNull()

    /** Saves the entry and returns a short confirmation, calling out a new personal record. */
    fun save(entry: WorkoutEntry, onSaved: (String) -> Unit) {
        val history = entriesFor(entry.exerciseId)
        val metric = Analytics.primaryMetric(entry.type, history + entry)
        val pr = Analytics.isPersonalRecord(entry, history, metric)
        viewModelScope.launch {
            repo.saveEntry(entry)
            onSaved(
                if (pr) "New personal record! ${entry.exerciseName} — best ${metric.label.lowercase()} yet"
                else "${entry.exerciseName} saved"
            )
        }
    }

    fun delete(entry: WorkoutEntry) {
        viewModelScope.launch { repo.deleteEntry(entry.id) }
    }

    fun addCustomExercise(name: String, category: Category, type: ExerciseType): Exercise {
        val exercise = Exercise(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            category = category,
            type = type,
            isCustom = true,
        )
        viewModelScope.launch { repo.addCustomExercise(exercise) }
        return exercise
    }

    fun deleteCustomExercise(exercise: Exercise) {
        viewModelScope.launch { repo.deleteCustomExercise(exercise.id) }
    }
}
