package id.asmaraputra.fieldstamp.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import id.asmaraputra.fieldstamp.core.model.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToLong

sealed interface PlaceResult {
    data class Found(val name: String) : PlaceResult
    /** No network, or the geocoding service did not answer in time. */
    data object Offline : PlaceResult
    /** The device has no geocoding backend (e.g. no Google Play services). */
    data object NotSupported : PlaceResult
    /** The service answered but has no address for this point. */
    data object NotFound : PlaceResult
}

/**
 * Reverse geocoding through the platform [Geocoder]: free, no API key, no extra SDK. On devices with Google
 * Play services it is backed by Google's geocoding service, so coordinates are sent over the network.
 * Callers must only use it when the user has enabled automatic place names.
 */
class PlaceNameResolver(context: Context) {
    private val appContext = context.applicationContext
    private val cache = HashMap<Long, String>()

    val isSupported: Boolean get() = Geocoder.isPresent()

    suspend fun resolve(latitude: Double, longitude: Double, timeoutMillis: Long = 8_000): PlaceResult {
        if (!isSupported) return PlaceResult.NotSupported
        val key = cacheKey(latitude, longitude)
        synchronized(cache) { cache[key] }?.let { return PlaceResult.Found(it) }

        val addresses = withTimeoutOrNull(timeoutMillis) { lookup(latitude, longitude) } ?: return PlaceResult.Offline
        val name = addresses.getOrNull()?.firstNotNullOfOrNull { format(it) }
        return when {
            addresses.isFailure -> PlaceResult.Offline
            name == null -> PlaceResult.NotFound
            else -> {
                synchronized(cache) { cache[key] = name }
                PlaceResult.Found(name)
            }
        }
    }

    private suspend fun lookup(latitude: Double, longitude: Double): Result<List<Address>> {
        val geocoder = Geocoder(appContext, Formats.locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 3, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(Result.success(addresses)) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(Result.failure(IOException(errorMessage))) }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 3).orEmpty() }
            }
        }
    }

    private fun format(a: Address) = PlaceNameFormatter.format(
        street = a.thoroughfare,
        number = a.subThoroughfare,
        subLocality = a.subLocality,
        locality = a.locality,
        subAdminArea = a.subAdminArea,
        adminArea = a.adminArea,
        fallbackLine = a.getAddressLine(0),
    )

    /** ~11 m grid, so nearby photos reuse one lookup. */
    private fun cacheKey(lat: Double, lon: Double): Long =
        (lat * 10_000).roundToLong() * 4_000_000L + (lon * 10_000).roundToLong()
}

/** Builds a concise Indonesian-style place name: street, kelurahan/desa, kecamatan, kabupaten/kota, provinsi. */
object PlaceNameFormatter {
    const val MAX_LENGTH = 140
    private val plusCode = Regex("^[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}\\s*", RegexOption.IGNORE_CASE)
    private val postal = Regex("\\b\\d{5}\\b")

    fun format(
        street: String?,
        number: String?,
        subLocality: String?,
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        fallbackLine: String? = null,
    ): String? {
        val streetPart = street?.clean()?.let { s -> number?.clean()?.let { "$s No. $it" } ?: s }
        val parts = listOfNotNull(streetPart, subLocality?.clean(), locality?.clean(), subAdminArea?.clean(), adminArea?.clean())
            .distinctBy { it.lowercase(Locale.ROOT) }
        if (parts.size >= 2) return parts.joinToString(", ").take(MAX_LENGTH)
        // Sparse address data: fall back to the provider's formatted line without postal code or country.
        val line = fallbackLine
            ?.split(",")
            ?.mapNotNull { postal.replace(it, "").clean() }
            ?.filterNot { it.equals("Indonesia", ignoreCase = true) }
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }
        return (line ?: parts.firstOrNull())?.take(MAX_LENGTH)
    }

    /** Strips a leading plus code (e.g. "7Q5V+XX Gg. Melati" → "Gg. Melati") and drops empty or placeholder parts. */
    private fun String.clean(): String? = plusCode.replace(trim(), "").trim().takeIf { it.isNotEmpty() && it != "Unnamed Road" }
}
