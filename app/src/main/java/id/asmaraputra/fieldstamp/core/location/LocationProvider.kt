package id.asmaraputra.fieldstamp.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Fix(val point: GeoPoint) : LocationResult
    data object PermissionDenied : LocationResult
    data object ServicesDisabled : LocationResult
    data object Unavailable : LocationResult
}

/**
 * Foreground, one-shot location using the platform LocationManager (no Google Play services dependency,
 * no background access). A fix is returned only when the device actually provides one.
 */
class LocationProvider(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasFinePermission() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasCoarsePermission() = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasAnyPermission() = hasFinePermission() || hasCoarsePermission()

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(): Boolean = manager != null && LocationManagerCompat.isLocationEnabled(manager)

    /**
     * Returns a fix no older than [maxAgeMillis] if one is cached, otherwise requests a fresh one and
     * waits up to [timeoutMillis].
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(maxAgeMillis: Long = 2 * 60_000L, timeoutMillis: Long = 15_000L): LocationResult {
        if (!hasAnyPermission()) return LocationResult.PermissionDenied
        val lm = manager ?: return LocationResult.Unavailable
        if (!isEnabled()) return LocationResult.ServicesDisabled

        val providers = buildList {
            if (hasFinePermission() && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) add(0, LocationManager.FUSED_PROVIDER)
        }.distinct()
        if (providers.isEmpty()) return LocationResult.ServicesDisabled

        val now = System.currentTimeMillis()
        val cached = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time in 0..maxAgeMillis }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
        if (cached != null) return LocationResult.Fix(cached.toGeoPoint())

        val fresh = withTimeoutOrNull(timeoutMillis) { firstFix(lm, providers) }
        return fresh?.let { LocationResult.Fix(it.toGeoPoint()) } ?: LocationResult.Unavailable
    }

    /**
     * Asks every enabled provider at once and returns the first real fix. Relying on the fused provider
     * alone fails on devices where it is missing or idle (e.g. without Google Play services).
     */
    private suspend fun firstFix(lm: LocationManager, providers: List<String>): Location? = coroutineScope {
        val result = CompletableDeferred<Location?>()
        val requests = providers.map { provider ->
            launch { requestSingle(lm, provider)?.let { result.complete(it) } }
        }
        launch {
            requests.joinAll()
            result.complete(null)
        }
        try {
            result.await()
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingle(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, executor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderDisabled(provider: String) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    private fun Location.toGeoPoint() = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        capturedAtEpochMillis = time,
    )
}
