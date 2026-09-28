package com.chase.babylayers.logic

import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin clothing recommendation engine. No Android dependencies so it
 * can be unit tested on the JVM.
 *
 * Guidance is based on widely published advice (AAP "one more layer than an
 * adult", Lullaby Trust / NHS safe-sleep room temperature and TOG charts).
 * It is general guidance, not medical advice.
 */

enum class Outing(val label: String) {
    STROLLER("Stroller / pram"),
    CARRIER("Carrier / sling"),
    CAR("Car seat"),
    ACTIVE("Walking / playing"),
}

enum class TempBand(val label: String, val emoji: String) {
    EXTREME_COLD("Dangerously cold", "🥶"),
    FREEZING("Freezing", "❄️"),
    COLD("Cold", "🧊"),
    CHILLY("Chilly", "🌬️"),
    COOL("Cool", "🍂"),
    MILD("Mild", "🌤️"),
    WARM("Warm", "☀️"),
    HOT("Hot", "🔥"),
}

enum class Severity { INFO, CAUTION, DANGER }

data class Note(val text: String, val severity: Severity = Severity.INFO)

data class Conditions(
    val tempC: Double,
    val feelsLikeC: Double = tempC,
    val windKph: Double = 0.0,
    val precipitationMm: Double = 0.0,
    val uvIndex: Double = 0.0,
    val weatherCode: Int = 0,
    val isDay: Boolean = true,
) {
    val isSnowing: Boolean get() = weatherCode in 71..77 || weatherCode in 85..86
    val isRaining: Boolean
        get() = !isSnowing && (precipitationMm > 0.0 || weatherCode in 51..67 || weatherCode in 80..82 || weatherCode in 95..99)
}

data class OutdoorAdvice(
    val band: TempBand,
    val layers: Int,
    val clothing: List<String>,
    val accessories: List<String>,
    val notes: List<Note>,
)

data class SleepAdvice(
    val tog: Double,
    val sleepBag: String,
    val clothing: List<String>,
    val notes: List<Note>,
)

object Recommender {

    fun bandFor(feelsLikeC: Double): TempBand = when {
        feelsLikeC < -15 -> TempBand.EXTREME_COLD
        feelsLikeC < -5 -> TempBand.FREEZING
        feelsLikeC < 5 -> TempBand.COLD
        feelsLikeC < 12 -> TempBand.CHILLY
        feelsLikeC < 18 -> TempBand.COOL
        feelsLikeC < 24 -> TempBand.MILD
        feelsLikeC < 29 -> TempBand.WARM
        else -> TempBand.HOT
    }

    private fun baseLayers(band: TempBand): Int = when (band) {
        TempBand.HOT, TempBand.WARM -> 1
        TempBand.MILD -> 2
        TempBand.COOL, TempBand.CHILLY -> 3
        TempBand.COLD, TempBand.FREEZING, TempBand.EXTREME_COLD -> 4
    }

    /** The clothing to wear, from skin outwards, for a given number of layers. */
    private fun layerItems(band: TempBand, layers: Int, outing: Outing): List<String> {
        val hot = band == TempBand.HOT || band == TempBand.WARM
        val all = mutableListOf<String>()
        all += if (hot) {
            "Short-sleeve cotton bodysuit (or just a diaper in the shade if very hot)"
        } else {
            "Long-sleeve cotton bodysuit"
        }
        all += when {
            band == TempBand.MILD -> "Light long-sleeve outfit or footed sleepsuit"
            hot -> "Loose, light-coloured cotton or muslin outfit"
            else -> "Footed sleepsuit or leggings + top"
        }
        all += when (band) {
            TempBand.COOL -> "Light sweater, cardigan or thin jacket"
            else -> "Warm fleece or knit sweater"
        }
        all += if (outing == Outing.CAR) {
            "Blanket or your coat laid over the buckled harness (not a snowsuit worn in the seat)"
        } else {
            "Insulated snowsuit / pram suit (bunting)"
        }
        return all.take(layers)
    }

    fun outdoor(ageMonths: Int, conditions: Conditions, outing: Outing): OutdoorAdvice {
        val band = bandFor(conditions.feelsLikeC)
        val newborn = ageMonths < 3
        val underSix = ageMonths < 6
        val toddler = ageMonths >= 12
        val notes = mutableListOf<Note>()
        val accessories = mutableListOf<String>()

        var layers = baseLayers(band)

        // Newborns can't regulate temperature well and lose heat quickly.
        if (newborn && (band == TempBand.MILD || band == TempBand.COOL)) layers += 1
        // A carrier shares the adult's body heat, which counts as a layer.
        if (outing == Outing.CARRIER && band <= TempBand.MILD) layers -= 1
        // An actively moving toddler generates heat, so dress them like an adult.
        if (outing == Outing.ACTIVE && toddler && band <= TempBand.COOL) layers -= 1
        layers = min(4, max(1, layers))

        val cold = band <= TempBand.CHILLY
        val veryCold = band <= TempBand.COLD

        // Head, hands and feet.
        when {
            band == TempBand.HOT || band == TempBand.WARM ->
                accessories += "Wide-brim sun hat"
            veryCold -> accessories += listOf(
                "Warm hat that covers the ears",
                "Mittens",
                "Warm socks + booties",
            )
            cold -> accessories += listOf("Warm hat", "Socks (mittens if windy)")
            band == TempBand.COOL -> accessories += listOf("Light hat", "Socks")
            band == TempBand.MILD && newborn -> accessories += listOf("Light cotton hat", "Socks")
            band == TempBand.MILD -> accessories += "Socks"
        }

        when (outing) {
            Outing.STROLLER -> {
                if (cold) accessories += "Stroller footmuff or blanket (tucked, not over the face)"
                if (band == TempBand.HOT || band == TempBand.WARM) notes += Note(
                    "Never cover the stroller with a blanket or muslin — it traps heat. Use a clip-on parasol or a breathable sun shade instead.",
                    Severity.CAUTION,
                )
            }
            Outing.CARRIER -> {
                notes += Note("In a carrier your body heat is one layer. Zip your own coat (or a babywearing cover) over both of you when it's cold.")
                if (band == TempBand.HOT || band == TempBand.WARM) notes += Note(
                    "Carrying in the heat warms baby fast. Take breaks, and check their neck for sweat often.",
                    Severity.CAUTION,
                )
            }
            Outing.CAR -> {
                notes += Note(
                    "No puffy coats or snowsuits in the car seat — they compress in a crash and leave the harness too loose. Buckle up in thin layers, then add a blanket or coat over the straps.",
                    if (cold) Severity.DANGER else Severity.INFO,
                )
                if (band >= TempBand.WARM) notes += Note(
                    "Never leave a baby in a parked car, even for a minute. Cool the car before putting baby in.",
                    Severity.DANGER,
                )
            }
            Outing.ACTIVE -> if (!toddler) notes += Note(
                "Babies who aren't walking yet don't generate much heat from activity, so dress them as for the stroller.",
            )
        }

        // Temperature band specific guidance.
        when (band) {
            TempBand.HOT -> {
                notes += Note(
                    "It's hot. Stay in the shade, avoid 11am–3pm sun, and watch for signs of overheating (flushed skin, sweating, fast breathing, unusual sleepiness).",
                    Severity.DANGER,
                )
                notes += if (underSix) {
                    Note("Under 6 months: offer extra breast milk or formula (not water).")
                } else {
                    Note("Offer extra milk feeds plus small sips of water.")
                }
            }
            TempBand.WARM -> notes += Note("Keep to the shade and offer extra feeds.")
            TempBand.FREEZING -> notes += Note(
                "Keep trips outside short (10–20 minutes) and check baby's cheeks, nose and hands for cold or pale skin.",
                Severity.CAUTION,
            )
            TempBand.EXTREME_COLD -> notes += Note(
                "Risk of frostbite in minutes. Avoid taking baby outside unless it's essential, and keep any exposure very brief.",
                Severity.DANGER,
            )
            else -> Unit
        }

        if (newborn && cold) notes += Note(
            "Newborns lose heat quickly — keep outings short and check them often.",
            Severity.CAUTION,
        )

        // Weather modifiers.
        if (conditions.isSnowing && band <= TempBand.COOL) {
            accessories += "Waterproof snowsuit or stroller weather shield"
        } else if (conditions.isRaining) {
            accessories += if (outing == Outing.CARRIER) {
                "Waterproof babywearing cover"
            } else {
                "Rain cover / waterproof outer layer"
            }
            if (band <= TempBand.COOL) notes += Note("Wet clothing chills fast — change damp layers as soon as you're inside.")
        }
        if (conditions.windKph >= 20 && band <= TempBand.MILD) {
            accessories += "Windproof outer layer or stroller cover"
            notes += Note("It's windy (${conditions.windKph.toInt()} km/h), which makes it feel colder than the thermometer says.")
        }
        if (conditions.isDay && conditions.uvIndex >= 3) {
            if (band < TempBand.WARM) accessories += "Sun hat"
            notes += if (underSix) {
                Note(
                    "UV index ${conditions.uvIndex.toInt()}: under 6 months keep baby out of direct sun. Use shade and clothing; sunscreen only on small exposed areas if shade isn't possible.",
                    Severity.CAUTION,
                )
            } else {
                Note(
                    "UV index ${conditions.uvIndex.toInt()}: use broad-spectrum SPF 30+ baby sunscreen on exposed skin, and reapply every 2 hours.",
                    Severity.CAUTION,
                )
            }
        }

        return OutdoorAdvice(
            band = band,
            layers = layers,
            clothing = layerItems(band, layers, outing),
            accessories = accessories.distinct(),
            notes = notes,
        )
    }

    /** Sleep advice from the nursery room temperature (TOG chart). */
    fun sleep(ageMonths: Int, roomTempC: Double): SleepAdvice {
        val notes = mutableListOf<Note>()
        val shortVest = "Short-sleeve bodysuit"
        val longVest = "Long-sleeve bodysuit"
        val sleepsuit = "Footed sleepsuit (pyjamas)"

        val (tog, clothing) = when {
            roomTempC >= 27 -> 0.2 to listOf("Diaper only, or a $shortVest")
            roomTempC >= 24 -> 0.5 to listOf(shortVest)
            roomTempC >= 22 -> 1.0 to listOf(shortVest)
            roomTempC >= 20 -> 1.0 to listOf(longVest)
            roomTempC >= 18 -> 2.5 to listOf(shortVest)
            roomTempC >= 16 -> 2.5 to listOf(longVest)
            roomTempC >= 14 -> 2.5 to listOf(longVest, sleepsuit)
            else -> 3.5 to listOf(longVest, sleepsuit)
        }

        val sleepBag = when (tog) {
            0.2 -> "0.2 TOG sleep bag (or no sleep bag)"
            0.5 -> "0.5 TOG sleep bag"
            1.0 -> "1.0 TOG sleep bag"
            2.5 -> "2.5 TOG sleep bag"
            else -> "3.5 TOG sleep bag"
        }

        when {
            roomTempC > 24 -> notes += Note(
                "The room is warmer than the recommended 16–20°C (61–68°F). Overheating raises the risk of SIDS — ventilate the room or use a fan pointed away from baby.",
                Severity.DANGER,
            )
            roomTempC > 20 -> notes += Note(
                "Slightly warmer than the ideal 16–20°C (61–68°F). Dress lightly and check baby isn't sweaty.",
                Severity.CAUTION,
            )
            roomTempC < 14 -> notes += Note(
                "The room is quite cold. Use the 3.5 TOG sleep bag and consider gently heating the room toward 16–20°C (61–68°F).",
                Severity.CAUTION,
            )
            roomTempC < 16 -> notes += Note("A little below the ideal 16–20°C (61–68°F); the warmer layers above will keep baby comfortable.")
            else -> notes += Note("Great — 16–20°C (61–68°F) is the ideal room temperature for baby's sleep.")
        }

        notes += Note(
            "No hats, mittens or hoods for sleep — babies release heat through their head.",
            Severity.CAUTION,
        )
        if (ageMonths < 12) {
            notes += Note(
                "Under 12 months: no loose blankets, pillows or bumpers. A well-fitted sleep bag replaces blankets. Always place baby on their back to sleep.",
                Severity.CAUTION,
            )
        } else {
            notes += Note("Over 12 months a sleep bag is still great; a light blanket is also OK if your toddler prefers one.")
        }
        if (ageMonths < 4) {
            notes += Note("If you swaddle, stop as soon as baby shows signs of trying to roll (often around 2–4 months) and switch to an arms-out sleep bag.")
        }
        notes += Note("Don't add extra layers if baby has a fever or is unwell.")

        return SleepAdvice(tog = tog, sleepBag = sleepBag, clothing = clothing, notes = notes)
    }
}
