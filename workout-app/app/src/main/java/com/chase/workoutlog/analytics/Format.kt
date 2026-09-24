package com.chase.workoutlog.analytics

import com.chase.workoutlog.data.ExerciseType
import com.chase.workoutlog.data.WorkoutEntry
import java.util.Locale
import kotlin.math.roundToInt

object Format {

    fun number(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString()
        else String.format(Locale.US, "%.1f", v).removeSuffix(".0")

    fun weight(lb: Double): String = "${number(lb)} lb"

    fun miles(mi: Double): String = String.format(Locale.US, "%.2f mi", mi)

    /** 125 -> "2:05", 3725 -> "1:02:05". */
    fun duration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun seconds(s: Int): String = if (s >= 60) duration(s) else "${s}s"

    fun pace(secondsPerMile: Double): String = "${duration(secondsPerMile.roundToInt())} /mi"

    fun metric(metric: Metric, v: Double): String = when (metric) {
        Metric.TOP_WEIGHT -> weight(v)
        Metric.EST_1RM, Metric.VOLUME -> weight(v.roundToInt().toDouble())
        Metric.MAX_REPS, Metric.TOTAL_REPS -> "${v.roundToInt()} reps"
        Metric.PACE -> pace(v)
        Metric.DISTANCE -> miles(v)
        Metric.DURATION, Metric.LONGEST_HOLD, Metric.TOTAL_TIME -> seconds(v.roundToInt())
    }

    /** One-line description of what was done, e.g. "3 × 10 @ 135 lb". */
    fun summary(e: WorkoutEntry): String = when (e.type) {
        ExerciseType.STRENGTH -> {
            val sets = e.sets
            when {
                sets.isEmpty() -> "No sets"
                sets.all { it == sets.first() } ->
                    "${sets.size} × ${sets.first().reps} @ ${weight(sets.first().weight)}"
                sets.all { it.weight == sets.first().weight } ->
                    "${sets.joinToString(", ") { it.reps.toString() }} reps @ ${weight(sets.first().weight)}"
                else -> sets.joinToString(", ") { "${it.reps}×${number(it.weight)}" } + " lb"
            }
        }
        ExerciseType.BODYWEIGHT -> {
            val sets = e.sets
            when {
                sets.isEmpty() -> "No sets"
                sets.all { it.reps == sets.first().reps } -> "${sets.size} × ${sets.first().reps} reps"
                else -> sets.joinToString(", ") { it.reps.toString() } + " reps"
            }
        }
        ExerciseType.TIMED -> {
            val sets = e.sets
            when {
                sets.isEmpty() -> "No sets"
                sets.all { it.seconds == sets.first().seconds } -> "${sets.size} × ${seconds(sets.first().seconds)}"
                else -> sets.joinToString(", ") { seconds(it.seconds) }
            }
        }
        ExerciseType.CARDIO -> buildString {
            if (e.distanceMiles > 0) append(miles(e.distanceMiles)).append(" in ")
            append(duration(e.durationSeconds))
            Analytics.value(e, Metric.PACE)?.let { append(" · ").append(pace(it)) }
        }
    }
}
