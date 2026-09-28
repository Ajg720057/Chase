package com.chase.babylayers.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chase.babylayers.data.LocationProvider
import com.chase.babylayers.data.Place
import com.chase.babylayers.data.Weather
import com.chase.babylayers.data.WeatherRepository
import com.chase.babylayers.logic.Conditions
import com.chase.babylayers.logic.Outing
import com.chase.babylayers.logic.TempUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("baby_layers", Context.MODE_PRIVATE)
    private val repo = WeatherRepository()
    private val locations = LocationProvider(app)
    private var weatherJob: Job? = null

    var ageMonths by mutableStateOf(prefs.getInt("age_months", 3))
        private set
    var unit by mutableStateOf(
        prefs.getString("unit", null)?.let { TempUnit.valueOf(it) } ?: defaultUnit()
    )
        private set
    var outing by mutableStateOf(
        prefs.getString("outing", null)?.let { runCatching { Outing.valueOf(it) }.getOrNull() } ?: Outing.STROLLER
    )
        private set
    /** Always stored in °C. */
    var roomTempC by mutableStateOf(prefs.getFloat("room_temp_c", 19f).toDouble())
        private set

    var place by mutableStateOf(loadPlace())
        private set
    var weather by mutableStateOf<Weather?>(null)
        private set
    /** Outdoor temperature the user typed in, used when there's no live weather. Stored in °C. */
    var manualTempC by mutableStateOf<Double?>(null)
        private set

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var searchResults by mutableStateOf<List<Place>>(emptyList())
        private set

    /** The conditions the recommendation is based on (live weather wins over a manual entry). */
    val conditions: Conditions?
        get() = manualTempC?.let { Conditions(tempC = it) } ?: weather?.current

    init {
        place?.let { refreshWeather(it) }
    }

    fun updateAge(months: Int) {
        ageMonths = months
        prefs.edit().putInt("age_months", months).apply()
    }

    fun updateUnit(newUnit: TempUnit) {
        unit = newUnit
        prefs.edit().putString("unit", newUnit.name).apply()
    }

    fun updateOuting(newOuting: Outing) {
        outing = newOuting
        prefs.edit().putString("outing", newOuting.name).apply()
    }

    fun updateRoomTemp(c: Double) {
        roomTempC = c.coerceIn(10.0, 32.0)
        prefs.edit().putFloat("room_temp_c", roomTempC.toFloat()).apply()
    }

    fun updateManualTemp(c: Double?) {
        manualTempC = c
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { repo.search(query) }
                .onSuccess {
                    searchResults = it
                    if (it.isEmpty()) error = "No places found for \"$query\"."
                }
                .onFailure { error = "Couldn't search — check your internet connection." }
            loading = false
        }
    }

    fun choosePlace(newPlace: Place) {
        searchResults = emptyList()
        place = newPlace
        savePlace(newPlace)
        refreshWeather(newPlace)
    }

    fun useDeviceLocation() {
        viewModelScope.launch {
            loading = true
            error = null
            val found = runCatching { locations.currentPlace() }.getOrNull()
            if (found == null) {
                loading = false
                error = "Couldn't get your location. Make sure location is turned on, or search for your city."
            } else {
                choosePlace(found)
            }
        }
    }

    fun locationPermissionDenied() {
        error = "Location permission denied — search for your city instead."
    }

    fun refreshWeather(target: Place? = place) {
        target ?: return
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            loading = true
            error = null
            runCatching { repo.weather(target) }
                .onSuccess {
                    weather = it
                    manualTempC = null
                }
                .onFailure { error = "Couldn't load the weather — check your connection, or enter the temperature manually." }
            loading = false
        }
    }

    private fun loadPlace(): Place? {
        val name = prefs.getString("place_name", null) ?: return null
        return Place(
            name,
            prefs.getFloat("place_lat", 0f).toDouble(),
            prefs.getFloat("place_lon", 0f).toDouble(),
        )
    }

    private fun savePlace(p: Place) {
        prefs.edit()
            .putString("place_name", p.name)
            .putFloat("place_lat", p.latitude.toFloat())
            .putFloat("place_lon", p.longitude.toFloat())
            .apply()
    }

    private fun defaultUnit(): TempUnit =
        if (Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) {
            TempUnit.FAHRENHEIT
        } else {
            TempUnit.CELSIUS
        }
}
