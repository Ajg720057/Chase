package com.chase.babylayers.logic

import kotlin.math.roundToInt

enum class TempUnit(val symbol: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0
fun fToC(f: Double): Double = (f - 32.0) * 5.0 / 9.0

fun formatTemp(c: Double, unit: TempUnit): String = when (unit) {
    TempUnit.CELSIUS -> "${c.roundToInt()}°C"
    TempUnit.FAHRENHEIT -> "${cToF(c).roundToInt()}°F"
}

fun formatAge(months: Int): String = when {
    months == 0 -> "Newborn (under 1 month)"
    months == 1 -> "1 month"
    months < 24 -> "$months months"
    months % 12 == 0 -> "${months / 12} years"
    else -> "${months / 12} years ${months % 12} months"
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "—"
}
