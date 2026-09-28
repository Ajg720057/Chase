package com.chase.babylayers.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Device location via the platform LocationManager (no Google Play Services needed). */
class LocationProvider(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** Caller must have already been granted a location permission. */
    @SuppressLint("MissingPermission")
    suspend fun currentPlace(): Place? {
        val providers = manager.getProviders(true)
        val lastKnown = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val fresh = lastKnown?.takeIf { System.currentTimeMillis() - it.time < 30 * 60 * 1000 }
        val location = fresh ?: withTimeoutOrNull(15_000) { requestSingle(providers) } ?: lastKnown
            ?: return null
        return Place(nameFor(location), location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun requestSingle(providers: List<String>): Location? {
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> providers.firstOrNull() ?: return null
        }
        return suspendCancellableCoroutine<Location?> { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                manager.requestSingleUpdate(provider, listener, context.mainLooper)
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun nameFor(location: Location): String = withContext(Dispatchers.IO) {
        val fallback = String.format(Locale.US, "%.2f, %.2f", location.latitude, location.longitude)
        if (!Geocoder.isPresent()) return@withContext fallback
        runCatching {
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            listOfNotNull(address?.locality ?: address?.subAdminArea, address?.adminArea)
                .distinct()
                .joinToString(", ")
                .ifBlank { fallback }
        }.getOrDefault(fallback)
    }
}
