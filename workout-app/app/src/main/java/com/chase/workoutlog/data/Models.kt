package com.chase.workoutlog.data

import java.time.LocalDate

/** How an exercise is measured; decides which questions the log screen asks. */
enum class ExerciseType(val label: String, val description: String) {
    STRENGTH("Weights", "Sets × reps × weight"),
    BODYWEIGHT("Bodyweight", "Sets × reps"),
    CARDIO("Cardio", "Time and distance"),
    TIMED("Timed hold", "Sets × seconds"),
}

enum class Category(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    LEGS("Legs"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    CORE("Core"),
    FULL_BODY("Full body"),
    CARDIO("Cardio"),
}

data class Exercise(
    val id: String,
    val name: String,
    val category: Category,
    val type: ExerciseType,
    val isCustom: Boolean = false,
)

/** One set. Weight is in pounds; only the fields relevant to the exercise type are used. */
data class SetEntry(
    val reps: Int = 0,
    val weight: Double = 0.0,
    val seconds: Int = 0,
)

/** One logged exercise on one day. */
data class WorkoutEntry(
    val id: Long,
    val exerciseId: String,
    /** Kept so history still reads correctly if a custom exercise is later deleted. */
    val exerciseName: String,
    val type: ExerciseType,
    val date: LocalDate,
    val sets: List<SetEntry> = emptyList(),
    val durationSeconds: Int = 0,
    val distanceMiles: Double = 0.0,
    val notes: String = "",
)
