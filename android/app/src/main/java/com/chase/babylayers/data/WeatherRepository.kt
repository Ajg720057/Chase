package com.chase.babylayers.data

import com.chase.babylayers.logic.Conditions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.min

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class Weather(
    val current: Conditions,
    val todayMinC: Double?,
    val todayMaxC: Double?,
    val tonightMinC: Double?,
)

/** Free, key-less weather + geocoding from Open-Meteo (https://open-meteo.com). */
class WeatherRepository {

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json")
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val parts = listOf(
                r.optString("name"),
                r.optString("admin1"),
                r.optString("country"),
            ).filter { it.isNotBlank() }.distinct()
            Place(parts.joinToString(", "), r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    suspend fun weather(place: Place): Weather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,uv_index,is_day" +
            "&hourly=temperature_2m" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&wind_speed_unit=kmh&timezone=auto&forecast_days=2"
        val json = get(url)
        val c = json.getJSONObject("current")
        val current = Conditions(
            tempC = c.getDouble("temperature_2m"),
            feelsLikeC = c.optDouble("apparent_temperature", c.getDouble("temperature_2m")),
            windKph = c.optDouble("wind_speed_10m", 0.0),
            precipitationMm = c.optDouble("precipitation", 0.0),
            uvIndex = c.optDouble("uv_index", 0.0).let { if (it.isNaN()) 0.0 else it },
            weatherCode = c.optInt("weather_code", 0),
            isDay = c.optInt("is_day", 1) == 1,
        )
        val daily = json.optJSONObject("daily")
        val min = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeUnless { it.isNaN() }
        val max = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeUnless { it.isNaN() }
        Weather(current, min, max, tonightMin(json, c.optString("time")))
    }

    /** Lowest forecast temperature between 7pm today and 7am tomorrow (local time). */
    private fun tonightMin(json: JSONObject, nowIso: String): Double? {
        val hourly = json.optJSONObject("hourly") ?: return null
        val times = hourly.optJSONArray("time") ?: return null
        val temps = hourly.optJSONArray("temperature_2m") ?: return null
        val today = nowIso.take(10)
        if (today.length < 10) return null
        var start = -1
        for (i in 0 until times.length()) {
            if (times.getString(i) == "${today}T19:00") { start = i; break }
        }
        if (start < 0) return null
        val end = min(start + 12, temps.length() - 1)
        return (start..end).map { temps.optDouble(it) }.filterNot { it.isNaN() }.minOrNull()
    }

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
