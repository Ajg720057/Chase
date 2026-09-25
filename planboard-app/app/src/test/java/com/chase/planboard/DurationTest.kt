package com.chase.planboard

import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.durationMinutes
import com.chase.planboard.data.endsNextDay
import com.chase.planboard.ui.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationTest {
    private fun plan(start: Int?, end: Int?) = PlanEntity(day = 0, startMinute = start, endMinute = end)

    @Test
    fun durationNeedsBothTimes() {
        assertNull(plan(9 * 60, null).durationMinutes)
        assertNull(plan(null, 10 * 60).durationMinutes)
        assertEquals(90, plan(9 * 60, 10 * 60 + 30).durationMinutes)
        assertFalse(plan(9 * 60, 10 * 60 + 30).endsNextDay)
    }

    @Test
    fun endBeforeStartRunsPastMidnight() {
        val p = plan(22 * 60, 60)
        assertEquals(180, p.durationMinutes)
        assertTrue(p.endsNextDay)
    }

    @Test
    fun durationText() {
        assertEquals("45 min", Format.duration(45))
        assertEquals("1 hr", Format.duration(60))
        assertEquals("1 hr 30 min", Format.duration(90))
        assertEquals("2 hrs 15 min", Format.duration(135))
        assertEquals("0 min", Format.duration(0))
    }
}
