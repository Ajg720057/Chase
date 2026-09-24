package com.chase.workoutlog.data

import com.chase.workoutlog.data.Category.ARMS
import com.chase.workoutlog.data.Category.BACK
import com.chase.workoutlog.data.Category.CHEST
import com.chase.workoutlog.data.Category.CORE
import com.chase.workoutlog.data.Category.FULL_BODY
import com.chase.workoutlog.data.Category.LEGS
import com.chase.workoutlog.data.Category.SHOULDERS
import com.chase.workoutlog.data.ExerciseType.BODYWEIGHT
import com.chase.workoutlog.data.ExerciseType.CARDIO
import com.chase.workoutlog.data.ExerciseType.STRENGTH
import com.chase.workoutlog.data.ExerciseType.TIMED

/** The built-in exercises offered in the picker. Ids are stable; don't rename them. */
object ExerciseCatalog {
    private fun ex(id: String, name: String, category: Category, type: ExerciseType) =
        Exercise(id, name, category, type)

    val builtIn: List<Exercise> = listOf(
        // Chest
        ex("bench_press", "Bench Press", CHEST, STRENGTH),
        ex("incline_bench_press", "Incline Bench Press", CHEST, STRENGTH),
        ex("dumbbell_bench_press", "Dumbbell Bench Press", CHEST, STRENGTH),
        ex("dumbbell_fly", "Dumbbell Fly", CHEST, STRENGTH),
        ex("cable_crossover", "Cable Crossover", CHEST, STRENGTH),
        ex("push_ups", "Push-ups", CHEST, BODYWEIGHT),
        ex("dips", "Dips", CHEST, BODYWEIGHT),
        // Back
        ex("deadlift", "Deadlift", BACK, STRENGTH),
        ex("barbell_row", "Barbell Row", BACK, STRENGTH),
        ex("dumbbell_row", "Dumbbell Row", BACK, STRENGTH),
        ex("lat_pulldown", "Lat Pulldown", BACK, STRENGTH),
        ex("seated_cable_row", "Seated Cable Row", BACK, STRENGTH),
        ex("pull_ups", "Pull-ups", BACK, BODYWEIGHT),
        ex("chin_ups", "Chin-ups", BACK, BODYWEIGHT),
        // Legs
        ex("squat", "Squat", LEGS, STRENGTH),
        ex("front_squat", "Front Squat", LEGS, STRENGTH),
        ex("leg_press", "Leg Press", LEGS, STRENGTH),
        ex("romanian_deadlift", "Romanian Deadlift", LEGS, STRENGTH),
        ex("lunges", "Lunges", LEGS, STRENGTH),
        ex("leg_extension", "Leg Extension", LEGS, STRENGTH),
        ex("leg_curl", "Leg Curl", LEGS, STRENGTH),
        ex("calf_raise", "Calf Raise", LEGS, STRENGTH),
        ex("bodyweight_squats", "Bodyweight Squats", LEGS, BODYWEIGHT),
        // Shoulders
        ex("overhead_press", "Overhead Press", SHOULDERS, STRENGTH),
        ex("dumbbell_shoulder_press", "Dumbbell Shoulder Press", SHOULDERS, STRENGTH),
        ex("lateral_raise", "Lateral Raise", SHOULDERS, STRENGTH),
        ex("front_raise", "Front Raise", SHOULDERS, STRENGTH),
        ex("face_pull", "Face Pull", SHOULDERS, STRENGTH),
        ex("shrugs", "Shrugs", SHOULDERS, STRENGTH),
        // Arms
        ex("bicep_curl", "Bicep Curl", ARMS, STRENGTH),
        ex("hammer_curl", "Hammer Curl", ARMS, STRENGTH),
        ex("preacher_curl", "Preacher Curl", ARMS, STRENGTH),
        ex("tricep_pushdown", "Tricep Pushdown", ARMS, STRENGTH),
        ex("skull_crushers", "Skull Crushers", ARMS, STRENGTH),
        ex("overhead_tricep_extension", "Overhead Tricep Extension", ARMS, STRENGTH),
        // Core
        ex("plank", "Plank", CORE, TIMED),
        ex("side_plank", "Side Plank", CORE, TIMED),
        ex("crunches", "Crunches", CORE, BODYWEIGHT),
        ex("sit_ups", "Sit-ups", CORE, BODYWEIGHT),
        ex("hanging_leg_raise", "Hanging Leg Raise", CORE, BODYWEIGHT),
        ex("russian_twist", "Russian Twist", CORE, BODYWEIGHT),
        ex("ab_wheel", "Ab Wheel Rollout", CORE, BODYWEIGHT),
        // Full body
        ex("kettlebell_swing", "Kettlebell Swing", FULL_BODY, STRENGTH),
        ex("clean_and_press", "Clean and Press", FULL_BODY, STRENGTH),
        ex("burpees", "Burpees", FULL_BODY, BODYWEIGHT),
        ex("wall_sit", "Wall Sit", FULL_BODY, TIMED),
        // Cardio
        ex("running", "Running", Category.CARDIO, CARDIO),
        ex("treadmill", "Treadmill", Category.CARDIO, CARDIO),
        ex("walking", "Walking", Category.CARDIO, CARDIO),
        ex("hiking", "Hiking", Category.CARDIO, CARDIO),
        ex("cycling", "Cycling", Category.CARDIO, CARDIO),
        ex("stationary_bike", "Stationary Bike", Category.CARDIO, CARDIO),
        ex("rowing", "Rowing Machine", Category.CARDIO, CARDIO),
        ex("elliptical", "Elliptical", Category.CARDIO, CARDIO),
        ex("stair_climber", "Stair Climber", Category.CARDIO, CARDIO),
        ex("swimming", "Swimming", Category.CARDIO, CARDIO),
        ex("jump_rope", "Jump Rope", Category.CARDIO, CARDIO),
    )
}
