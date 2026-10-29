package id.asmaraputra.fieldstamp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatsTest {
    private fun at(zone: String) = ZonedDateTime.of(2026, 10, 5, 14, 32, 0, 0, ZoneId.of(zone))

    @Test fun indonesianZonesUseCustomaryLabels() {
        assertEquals("WIB", Formats.zoneLabel(at("Asia/Jakarta")))
        assertEquals("WIB", Formats.zoneLabel(at("Asia/Pontianak")))
        assertEquals("WITA", Formats.zoneLabel(at("Asia/Makassar")))
        assertEquals("WIT", Formats.zoneLabel(at("Asia/Jayapura")))
    }

    @Test fun otherZonesUseUtcOffset() {
        assertEquals("UTC+0", Formats.zoneLabel(at("UTC")))
        assertEquals("UTC+5:30", Formats.zoneLabel(at("Asia/Kolkata")))
        assertEquals("UTC-3", Formats.zoneLabel(at("America/Sao_Paulo")))
    }

    @Test fun stampDateTimeUsesCaptureZoneNotDeviceZone() {
        // 2026-10-05T07:32Z is 14:32 in Jakarta and 15:32 in Makassar.
        val millis = ZonedDateTime.of(2026, 10, 5, 7, 32, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("05/10/2026 14:32 WIB", Formats.stampDateTime(CaptureTime(millis, "Asia/Jakarta")))
        assertEquals("05/10/2026 15:32 WITA", Formats.stampDateTime(CaptureTime(millis, "Asia/Makassar")))
    }

    @Test fun coordinatesAreLocaleIndependent() {
        assertEquals("-6.175392, 106.827153", Formats.coordinates(-6.1753924, 106.8271528))
        assertEquals("-6.175392, 106.827153 (±8\u00A0m)", Formats.coordinatesWithAccuracy(GeoPoint(-6.1753924, 106.8271528, 8.4f, 0)))
        assertEquals("1.000000, 2.000000", Formats.coordinatesWithAccuracy(GeoPoint(1.0, 2.0, null, 0)))
    }

    @Test fun fullMonthPeriodShowsMonthName() {
        assertEquals("Februari 2028", Formats.period(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)))
        assertTrue(Formats.period(LocalDate.of(2028, 2, 3), LocalDate.of(2028, 2, 10)).contains("–"))
    }

    @Test fun captureLocalDateIsComputedInCaptureZone() {
        // 23:30 UTC on Oct 4 is already Oct 5 in Jakarta.
        val millis = ZonedDateTime.of(2026, 10, 4, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 5), CaptureTime(millis, "Asia/Jakarta").localDate)
        assertEquals(LocalDate.of(2026, 10, 4), CaptureTime(millis, "UTC").localDate)
    }

    @Test fun invalidZoneFallsBackWithoutCrashing() {
        CaptureTime(0, "Not/AZone").localDate
    }
}

class TemplateConfigTest {
    @Test fun jsonRoundTripPreservesConfiguration() {
        val config = TemplateConfig(anchor = StampAnchor.TOP_RIGHT, textSizePercent = 4.5f, textColor = StampTextColor.BLACK, backdrop = false, customText = "Dinas PU")
        assertEquals(config.normalized(), TemplateConfig.fromJson(config.toJson()))
    }

    @Test fun invalidJsonFallsBackToDefaults() {
        assertEquals(TemplateConfig(), TemplateConfig.fromJson("{not json"))
        assertEquals(TemplateConfig(), TemplateConfig.fromJson(null))
    }

    @Test fun unknownKeysAreIgnored() {
        val parsed = TemplateConfig.fromJson("""{"anchor":"TOP_LEFT","futureField":true}""")
        assertEquals(StampAnchor.TOP_LEFT, parsed.anchor)
    }

    @Test fun normalizeAddsMissingFieldsDisabledAndRemovesDuplicates() {
        val config = TemplateConfig(fields = listOf(StampFieldSetting(StampField.CAPTION, true), StampFieldSetting(StampField.CAPTION, false))).normalized()
        assertEquals(StampField.entries.size, config.fields.size)
        assertEquals(StampField.CAPTION, config.fields.first().field)
        assertTrue(config.fields.first().enabled)
        assertFalse(config.fields.drop(1).any { it.enabled })
    }

    @Test fun normalizeClampsSizes() {
        val config = TemplateConfig(textSizePercent = 50f, marginPercent = -3f).normalized()
        assertEquals(TemplateConfig.MAX_TEXT_PERCENT, config.textSizePercent)
        assertEquals(0f, config.marginPercent)
    }
}
