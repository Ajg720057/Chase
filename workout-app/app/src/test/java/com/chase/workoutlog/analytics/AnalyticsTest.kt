package com.chase.workoutlog.analytics

import com.chase.workoutlog.data.ExerciseType
import com.chase.workoutlog.data.SetEntry
import com.chase.workoutlog.data.WorkoutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {
    private val day0 = LocalDate.of(2026, 9, 1)
    private var nextId = 1L

    private fun bench(daysLater: Long, vararg sets: Pair<Int, Double>) = WorkoutEntry(
        id = nextId++,
        exerciseId = "bench_press",
        exerciseName = "Bench Press",
        type = ExerciseType.STRENGTH,
        date = day0.plusDays(daysLater),
        sets = sets.map { (reps, weight) -> SetEntry(reps = reps, weight = weight) },
    )

    private fun run(daysLater: Long, seconds: Int, miles: Double) = WorkoutEntry(
        id = nextId++,
        exerciseId = "running",
        exerciseName = "Running",
        type = ExerciseType.CARDIO,
        date = day0.plusDays(daysLater),
        durationSeconds = seconds,
        distanceMiles = miles,
    )

    @Test
    fun weightIncreaseIsReportedAsPercentMoreWeight() {
        val entries = listOf(
            bench(0, 10 to 100.0, 10 to 100.0),
            bench(7, 8 to 110.0),
            bench(14, 5 to 120.0),
        )
        val p = Analytics.progress(entries, Metric.TOP_WEIGHT)!!
        assertEquals(20.0, p.percentSinceStart, 1e-9)
        assertEquals(100.0 / 11, p.percentVsPrevious!!, 1e-9)
        assertEquals(120.0, p.best.value, 1e-9)
        assertEquals("20% more weight", Analytics.describe(Metric.TOP_WEIGHT, p.percentSinceStart))
    }

    @Test
    fun fasterPaceIsAPositiveImprovement() {
        // 30:00 for 3 mi (10:00/mi) -> 27:00 for 3 mi (9:00/mi) = 10% faster
        val entries = listOf(run(0, 1800, 3.0), run(3, 1620, 3.0))
        val p = Analytics.progress(entries, Metric.PACE)!!
        assertEquals(10.0, p.percentSinceStart, 1e-9)
        assertEquals("10% faster", Analytics.describe(Metric.PACE, p.percentSinceStart))
        assertEquals(1620.0 / 3, p.best.value, 1e-9)
    }

    @Test
    fun slowerPaceIsNegative() {
        val entries = listOf(run(0, 1620, 3.0), run(3, 1800, 3.0))
        val p = Analytics.progress(entries, Metric.PACE)!!
        assertTrue(p.percentSinceStart < 0)
        assertTrue(Analytics.describe(Metric.PACE, p.percentSinceStart).endsWith("slower"))
    }

    @Test
    fun cardioWithoutDistanceFallsBackToDuration() {
        val entries = listOf(run(0, 1200, 0.0), run(2, 1500, 0.0))
        assertEquals(Metric.DURATION, Analytics.primaryMetric(ExerciseType.CARDIO, entries))
        assertNull(Analytics.progress(entries, Metric.PACE))
        assertEquals(25.0, Analytics.progress(entries, Metric.DURATION)!!.percentSinceStart, 1e-9)
    }

    @Test
    fun seriesKeepsBestValuePerDay() {
        val entries = listOf(bench(0, 5 to 100.0), bench(0, 5 to 115.0), bench(1, 5 to 105.0))
        val s = Analytics.series(entries, Metric.TOP_WEIGHT)
        assertEquals(listOf(115.0, 105.0), s.map { it.value })
    }

    @Test
    fun epleyEstimate() {
        assertEquals(100.0, Analytics.epley(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 10 / 30.0), Analytics.epley(100.0, 10), 1e-9)
        assertEquals(0.0, Analytics.epley(0.0, 10), 1e-9)
    }

    @Test
    fun personalRecordNeedsHistoryAndMustBeatAll() {
        val old = listOf(bench(0, 5 to 100.0), bench(2, 5 to 110.0))
        assertTrue(Analytics.isPersonalRecord(bench(4, 5 to 115.0), old, Metric.TOP_WEIGHT))
        assertFalse(Analytics.isPersonalRecord(bench(4, 5 to 110.0), old, Metric.TOP_WEIGHT))
        assertFalse(Analytics.isPersonalRecord(bench(4, 5 to 115.0), emptyList(), Metric.TOP_WEIGHT))
    }

    @Test
    fun describeRoundsSmallChanges() {
        assertEquals("2.5% more reps", Analytics.describe(Metric.MAX_REPS, 2.5))
        assertEquals("no change", Analytics.describe(Metric.MAX_REPS, 0.01))
    }
}

class ConsistencyTest {
    private val today = LocalDate.of(2026, 9, 24) // a Thursday

    @Test
    fun currentStreakCountsBackFromTodayOrYesterday() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(3, Consistency.currentStreak(days, today))
        // Today not logged yet: the streak ending yesterday still counts.
        assertEquals(3, Consistency.currentStreak(setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3)), today))
        assertEquals(0, Consistency.currentStreak(setOf(today.minusDays(2)), today))
    }

    @Test
    fun longestStreak() {
        val days = setOf(
            today.minusDays(10), today.minusDays(9), today.minusDays(8), today.minusDays(7),
            today.minusDays(3), today,
        )
        assertEquals(4, Consistency.longestStreak(days))
        assertEquals(0, Consistency.longestStreak(emptySet()))
    }

    @Test
    fun weeklyCountsUseMondayWeeks() {
        val monday = LocalDate.of(2026, 9, 21)
        val days = setOf(monday, monday.plusDays(2), monday.minusDays(1), monday.minusDays(7))
        val weeks = Consistency.weeklyCounts(days, today, 3)
        assertEquals(listOf(monday.minusWeeks(2), monday.minusWeeks(1), monday), weeks.map { it.first })
        assertEquals(listOf(0, 2, 2), weeks.map { it.second })
    }
}

class FormatTest {
    @Test
    fun durations() {
        assertEquals("0:45", Format.duration(45))
        assertEquals("27:00", Format.duration(1620))
        assertEquals("1:02:05", Format.duration(3725))
        assertEquals("9:00 /mi", Format.pace(540.0))
    }

    @Test
    fun strengthSummaries() {
        fun e(vararg sets: SetEntry) = WorkoutEntry(1, "b", "Bench", ExerciseType.STRENGTH, LocalDate.of(2026, 1, 1), sets.toList())
        assertEquals("3 × 10 @ 135 lb", Format.summary(e(SetEntry(10, 135.0), SetEntry(10, 135.0), SetEntry(10, 135.0))))
        assertEquals("10, 8 reps @ 135 lb", Format.summary(e(SetEntry(10, 135.0), SetEntry(8, 135.0))))
        assertEquals("10×135, 8×142.5 lb", Format.summary(e(SetEntry(10, 135.0), SetEntry(8, 142.5))))
    }
}
