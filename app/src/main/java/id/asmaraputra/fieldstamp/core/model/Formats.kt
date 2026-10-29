package id.asmaraputra.fieldstamp.core.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatting shared by the UI, the image stamp, and the PDF so all three always agree. */
object Formats {
    val locale: Locale = Locale.forLanguageTag("id-ID")

    private val dayHeading = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val stampDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val time24 = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val time12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)

    fun dayHeading(date: LocalDate): String = dayHeading.format(date)
    fun shortDate(date: LocalDate): String = shortDate.format(date)
    fun monthYear(month: YearMonth): String = monthYear.format(month)
    fun monthName(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
    fun fileStamp(time: ZonedDateTime): String = fileStamp.format(time)

    fun time(time: ZonedDateTime, use24Hour: Boolean = true): String =
        "${(if (use24Hour) time24 else time12).format(time)} ${zoneLabel(time)}"

    /** e.g. "05/10/2026 14:32 WIB" — compact enough for a stamp. */
    fun stampDateTime(capture: CaptureTime, use24Hour: Boolean = true): String {
        val zoned = capture.zoned
        return "${stampDate.format(zoned)} ${time(zoned, use24Hour)}"
    }

    fun period(start: LocalDate, end: LocalDate): String {
        val ym = YearMonth.from(start)
        return if (start == ym.atDay(1) && end == ym.atEndOfMonth()) {
            monthYear(ym)
        } else {
            "${shortDate(start)} – ${shortDate(end)}"
        }
    }

    /**
     * Indonesian zones get their customary abbreviations; everything else shows a UTC offset,
     * which is unambiguous and does not depend on the device's locale data.
     */
    fun zoneLabel(time: ZonedDateTime): String = zoneLabel(time.zone, time)

    fun zoneLabel(zone: ZoneId, at: ZonedDateTime): String = when (zone.id) {
        "Asia/Jakarta", "Asia/Pontianak" -> "WIB"
        "Asia/Makassar", "Asia/Ujung_Pandang" -> "WITA"
        "Asia/Jayapura" -> "WIT"
        else -> {
            val seconds = at.offset.totalSeconds
            val sign = if (seconds < 0) "-" else "+"
            val h = abs(seconds) / 3600
            val m = (abs(seconds) % 3600) / 60
            if (m == 0) "UTC$sign$h" else "UTC$sign$h:${m.toString().padStart(2, '0')}"
        }
    }

    /** Decimal degrees with 6 places (~0.1 m) using '.' regardless of locale, so values paste into maps. */
    fun coordinates(lat: Double, lon: Double): String =
        "${String.format(Locale.ROOT, "%.6f", lat)}, ${String.format(Locale.ROOT, "%.6f", lon)}"

    fun coordinatesWithAccuracy(point: GeoPoint): String {
        val base = coordinates(point.latitude, point.longitude)
        val acc = point.accuracyMeters ?: return base
        // Non-breaking space keeps "±5 m" together when the line wraps.
        return "$base (±${acc.roundToInt()}\u00A0m)"
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(locale, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(locale, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(locale, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
