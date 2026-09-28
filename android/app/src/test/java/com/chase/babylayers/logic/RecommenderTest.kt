package com.chase.babylayers.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {

    @Test
    fun bandsFollowFeelsLikeTemperature() {
        assertEquals(TempBand.EXTREME_COLD, Recommender.bandFor(-20.0))
        assertEquals(TempBand.FREEZING, Recommender.bandFor(-10.0))
        assertEquals(TempBand.COLD, Recommender.bandFor(0.0))
        assertEquals(TempBand.CHILLY, Recommender.bandFor(8.0))
        assertEquals(TempBand.COOL, Recommender.bandFor(15.0))
        assertEquals(TempBand.MILD, Recommender.bandFor(20.0))
        assertEquals(TempBand.WARM, Recommender.bandFor(26.0))
        assertEquals(TempBand.HOT, Recommender.bandFor(32.0))
    }

    @Test
    fun layersIncreaseAsItGetsColder() {
        val hot = Recommender.outdoor(6, Conditions(tempC = 32.0), Outing.STROLLER)
        val mild = Recommender.outdoor(6, Conditions(tempC = 20.0), Outing.STROLLER)
        val cool = Recommender.outdoor(6, Conditions(tempC = 15.0), Outing.STROLLER)
        val cold = Recommender.outdoor(6, Conditions(tempC = 0.0), Outing.STROLLER)
        assertEquals(1, hot.layers)
        assertEquals(2, mild.layers)
        assertEquals(3, cool.layers)
        assertEquals(4, cold.layers)
        assertEquals(cold.layers, cold.clothing.size)
    }

    @Test
    fun feelsLikeIsUsedRatherThanAirTemperature() {
        val windy = Recommender.outdoor(6, Conditions(tempC = 10.0, feelsLikeC = 3.0, windKph = 30.0), Outing.STROLLER)
        assertEquals(TempBand.COLD, windy.band)
        assertTrue(windy.accessories.any { it.contains("Windproof") })
    }

    @Test
    fun newbornsGetAnExtraLayerInMildWeather() {
        val newborn = Recommender.outdoor(1, Conditions(tempC = 20.0), Outing.STROLLER)
        val older = Recommender.outdoor(8, Conditions(tempC = 20.0), Outing.STROLLER)
        assertEquals(older.layers + 1, newborn.layers)
        assertTrue(newborn.accessories.any { it.contains("hat", ignoreCase = true) })
    }

    @Test
    fun carrierCountsAsALayer() {
        val stroller = Recommender.outdoor(6, Conditions(tempC = 8.0), Outing.STROLLER)
        val carrier = Recommender.outdoor(6, Conditions(tempC = 8.0), Outing.CARRIER)
        assertEquals(stroller.layers - 1, carrier.layers)
    }

    @Test
    fun activeToddlerOnlyGetsFewerLayersWhenWalking() {
        val toddler = Recommender.outdoor(24, Conditions(tempC = 8.0), Outing.ACTIVE)
        val baby = Recommender.outdoor(6, Conditions(tempC = 8.0), Outing.ACTIVE)
        assertEquals(2, toddler.layers)
        assertEquals(3, baby.layers)
    }

    @Test
    fun carSeatNeverRecommendsASnowsuit() {
        val advice = Recommender.outdoor(6, Conditions(tempC = -3.0), Outing.CAR)
        assertTrue(advice.clothing.none { it.startsWith("Insulated snowsuit") })
        assertTrue(advice.notes.any { it.severity == Severity.DANGER && it.text.contains("car seat") })
    }

    @Test
    fun layersAreAlwaysBetweenOneAndFour() {
        for (age in 0..48) for (t in -30..45) for (o in Outing.entries) {
            val a = Recommender.outdoor(age, Conditions(tempC = t.toDouble()), o)
            assertTrue(a.layers in 1..4)
            assertEquals(a.layers, a.clothing.size)
        }
    }

    @Test
    fun sunAdviceDependsOnAge() {
        val sunny = Conditions(tempC = 25.0, uvIndex = 7.0)
        val young = Recommender.outdoor(3, sunny, Outing.STROLLER)
        val older = Recommender.outdoor(9, sunny, Outing.STROLLER)
        assertTrue(young.notes.any { it.text.contains("out of direct sun") })
        assertTrue(older.notes.any { it.text.contains("SPF 30") })
    }

    @Test
    fun sleepTogFollowsRoomTemperature() {
        assertEquals(0.2, Recommender.sleep(6, 28.0).tog, 0.0)
        assertEquals(0.5, Recommender.sleep(6, 25.0).tog, 0.0)
        assertEquals(1.0, Recommender.sleep(6, 23.0).tog, 0.0)
        assertEquals(1.0, Recommender.sleep(6, 21.0).tog, 0.0)
        assertEquals(2.5, Recommender.sleep(6, 19.0).tog, 0.0)
        assertEquals(2.5, Recommender.sleep(6, 15.0).tog, 0.0)
        assertEquals(3.5, Recommender.sleep(6, 12.0).tog, 0.0)
    }

    @Test
    fun infantSleepAdviceWarnsAgainstLooseBedding() {
        assertTrue(Recommender.sleep(6, 18.0).notes.any { it.text.contains("no loose blankets") })
        assertTrue(Recommender.sleep(18, 18.0).notes.none { it.text.contains("no loose blankets") })
    }
}
