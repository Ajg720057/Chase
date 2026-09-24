package com.chase.workoutlog.analytics

import com.chase.workoutlog.data.ExerciseType
import com.chase.workoutlog.data.WorkoutEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Something about a session that can be tracked over time. */
enum class Metric(val label: String, val lowerIsBetter: Boolean = false) {
    TOP_WEIGHT("Top weight"),
    EST_1RM("Est. 1-rep max"),
    VOLUME("Total volume"),
    MAX_REPS("Best set"),
    TOTAL_REPS("Total reps"),
    PACE("Pace", lowerIsBetter = true),
    DISTANCE("Distance"),
    DURATION("Duration"),
    LONGEST_HOLD("Longest hold"),
    TOTAL_TIME("Total time"),
}

data class DataPoint(val date: LocalDate, val value: Double)

data class Progress(
    val metric: Metric,
    val first: DataPoint,
    val latest: DataPoint,
    val best: DataPoint,
    /** Improvement from the first session to the latest; positive is always "better". */
    val percentSinceStart: Double,
    /** Improvement from the session before the latest; null with only one session. */
    val percentVsPrevious: Double?,
    val sessions: Int,
)

object Analytics {

    fun metricsFor(type: ExerciseType): List<Metric> = when (type) {
        ExerciseType.STRENGTH -> listOf(Metric.TOP_WEIGHT, Metric.EST_1RM, Metric.VOLUME)
        ExerciseType.BODYWEIGHT -> listOf(Metric.MAX_REPS, Metric.TOTAL_REPS)
        ExerciseType.CARDIO -> listOf(Metric.PACE, Metric.DISTANCE, Metric.DURATION)
        ExerciseType.TIMED -> listOf(Metric.LONGEST_HOLD, Metric.TOTAL_TIME)
    }

    /** The metric the headline "x% better" is based on. Cardio without distances falls back to duration. */
    fun primaryMetric(type: ExerciseType, entries: List<WorkoutEntry>): Metric = when (type) {
        ExerciseType.CARDIO ->
            if (entries.count { value(it, Metric.PACE) != null } >= 2 ||
                entries.all { value(it, Metric.PACE) != null }
            ) Metric.PACE else Metric.DURATION
        else -> metricsFor(type).first()
    }

    /** The metric's value for one entry, or null when the entry doesn't record it. */
    fun value(entry: WorkoutEntry, metric: Metric): Double? {
        val v: Double = when (metric) {
            Metric.TOP_WEIGHT -> entry.sets.maxOfOrNull { it.weight } ?: 0.0
            Metric.EST_1RM -> entry.sets.maxOfOrNull { epley(it.weight, it.reps) } ?: 0.0
            Metric.VOLUME -> entry.sets.sumOf { it.weight * it.reps }
            Metric.MAX_REPS -> (entry.sets.maxOfOrNull { it.reps } ?: 0).toDouble()
            Metric.TOTAL_REPS -> entry.sets.sumOf { it.reps }.toDouble()
            Metric.PACE ->
                if (entry.distanceMiles > 0 && entry.durationSeconds > 0) entry.durationSeconds / entry.distanceMiles
                else 0.0
            Metric.DISTANCE -> entry.distanceMiles
            Metric.DURATION -> entry.durationSeconds.toDouble()
            Metric.LONGEST_HOLD -> (entry.sets.maxOfOrNull { it.seconds } ?: 0).toDouble()
            Metric.TOTAL_TIME -> entry.sets.sumOf { it.seconds }.toDouble()
        }
        return v.takeIf { it > 0 }
    }

    /** Estimated one-rep max (Epley formula). */
    fun epley(weight: Double, reps: Int): Double = when {
        weight <= 0 || reps <= 0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1 + reps / 30.0)
    }

    private fun better(metric: Metric, a: Double, b: Double): Boolean =
        if (metric.lowerIsBetter) a < b else a > b

    /** One point per day (the day's best), oldest first. */
    fun series(entries: List<WorkoutEntry>, metric: Metric): List<DataPoint> =
        entries.mapNotNull { e -> value(e, metric)?.let { DataPoint(e.date, it) } }
            .groupBy { it.date }
            .map { (_, points) -> points.reduce { a, b -> if (better(metric, b.value, a.value)) b else a } }
            .sortedBy { it.date }

    /** Percent improvement from [from] to [to]; positive means better for this metric. */
    fun improvement(metric: Metric, from: Double, to: Double): Double {
        if (from == 0.0) return 0.0
        val raw = (to - from) / from * 100
        return if (metric.lowerIsBetter) -raw else raw
    }

    fun progress(entries: List<WorkoutEntry>, metric: Metric): Progress? {
        val points = series(entries, metric)
        if (points.isEmpty()) return null
        val first = points.first()
        val latest = points.last()
        val best = points.reduce { a, b -> if (better(metric, b.value, a.value)) b else a }
        val previous = points.getOrNull(points.size - 2)
        return Progress(
            metric = metric,
            first = first,
            latest = latest,
            best = best,
            percentSinceStart = improvement(metric, first.value, latest.value),
            percentVsPrevious = previous?.let { improvement(metric, it.value, latest.value) },
            sessions = points.size,
        )
    }

    /** True if [entry] beats every earlier-or-other entry in [history] on [metric]. */
    fun isPersonalRecord(entry: WorkoutEntry, history: List<WorkoutEntry>, metric: Metric): Boolean {
        val v = value(entry, metric) ?: return false
        val others = history.filter { it.id != entry.id }.mapNotNull { value(it, metric) }
        if (others.isEmpty()) return false
        return others.all { better(metric, v, it) }
    }

    /** Human phrase for an improvement, e.g. "12% faster" or "5% less weight". */
    fun describe(metric: Metric, percent: Double): String {
        val p = abs(percent)
        val pct = if (p >= 10 || p == Math.floor(p)) "${p.toInt()}%" else String.format(java.util.Locale.US, "%.1f%%", p)
        if (p < 0.05) return "no change"
        val up = percent > 0
        val phrase = when (metric) {
            Metric.PACE -> if (up) "faster" else "slower"
            Metric.TOP_WEIGHT, Metric.EST_1RM -> if (up) "more weight" else "less weight"
            Metric.VOLUME -> if (up) "more volume" else "less volume"
            Metric.MAX_REPS, Metric.TOTAL_REPS -> if (up) "more reps" else "fewer reps"
            Metric.DISTANCE -> if (up) "farther" else "shorter distance"
            Metric.DURATION, Metric.LONGEST_HOLD, Metric.TOTAL_TIME -> if (up) "longer" else "shorter"
        }
        return "$pct $phrase"
    }
}

object Consistency {

    /** Consecutive days with a workout, ending today (or yesterday, if today isn't logged yet). */
    fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
        var day = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun longestStreak(days: Set<LocalDate>): Int {
        var longest = 0
        for (d in days) {
            if (d.minusDays(1) in days) continue // only count from the start of a run
            var len = 0
            var cur = d
            while (cur in days) {
                len++
                cur = cur.plusDays(1)
            }
            if (len > longest) longest = len
        }
        return longest
    }

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Number of distinct workout days in each of the last [weeks] weeks (Mon–Sun), oldest first. */
    fun weeklyCounts(days: Set<LocalDate>, today: LocalDate, weeks: Int): List<Pair<LocalDate, Int>> {
        val thisWeek = weekStart(today)
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val end = start.plusDays(6)
            start to days.count { !it.isBefore(start) && !it.isAfter(end) }
        }
    }
}
