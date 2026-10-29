package id.asmaraputra.fieldstamp.core.image

import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.StampField
import id.asmaraputra.fieldstamp.core.model.StampFieldSetting
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StampLayoutTest {
    private val labels = StampLabels("Proyek", "Kegiatan", "Kategori", "Koordinat", "Lokasi")
    private val time = CaptureTime(1_790_000_000_000, "Asia/Jakarta")

    private fun data(caption: String = "Pengecoran", location: GeoPoint? = null, project: String? = "Jalan Desa") =
        StampData(time, project, "Pengecoran", caption, null, location)

    @Test fun missingOptionalValuesAreOmittedNotSubstituted() {
        val lines = StampLayout.lines(TemplateConfig(), data(caption = " ", location = null, project = null), labels)
        val fields = lines.map { it.field }
        assertTrue(StampField.DATE_TIME in fields)
        assertTrue(StampField.COORDINATES !in fields)
        assertTrue(StampField.CAPTION !in fields)
        assertTrue(StampField.PROJECT !in fields)
    }

    @Test fun coordinatesAppearOnlyWhenPresent() {
        val lines = StampLayout.lines(TemplateConfig(), data(location = GeoPoint(-6.2, 106.8, 5f, 0)), labels)
        assertEquals("Koordinat: -6.200000, 106.800000 (±5\u00A0m)", lines.first { it.field == StampField.COORDINATES }.text)
    }

    @Test fun placeNameAppearsOnlyWhenPresent() {
        val with = StampLayout.lines(TemplateConfig(), data().copy(locationName = "Kramat Jati, Jakarta Timur"), labels)
        assertEquals("Lokasi: Kramat Jati, Jakarta Timur", with.first { it.field == StampField.LOCATION_NAME }.text)
        val blank = StampLayout.lines(TemplateConfig(), data().copy(locationName = "  "), labels)
        assertTrue(blank.none { it.field == StampField.LOCATION_NAME })
    }

    @Test fun linesFollowConfiguredOrderAndVisibility() {
        val config = TemplateConfig(
            fields = listOf(
                StampFieldSetting(StampField.CAPTION, true),
                StampFieldSetting(StampField.DATE_TIME, true),
                StampFieldSetting(StampField.PROJECT, false),
            ),
        )
        val fields = StampLayout.lines(config, data(), labels).map { it.field }
        assertEquals(listOf(StampField.CAPTION, StampField.DATE_TIME), fields)
    }

    @Test fun canvasKeepsPhotoAspectWithoutTemplate() {
        val size = StampLayout.canvasSize(IntSize(4000, 3000), null, 2560)
        assertEquals(IntSize(2560, 1920), size)
    }

    @Test fun canvasTakesTemplateAspect() {
        val size = StampLayout.canvasSize(IntSize(4000, 3000), IntSize(1080, 1350), 2560)
        assertEquals(2560, size.height)
        assertEquals(1080f / 1350f, size.width.toFloat() / size.height, 0.002f)
    }

    @Test fun canvasNeverUpscalesSmallSources() {
        assertEquals(IntSize(800, 600), StampLayout.canvasSize(IntSize(800, 600), null, 2560))
    }

    @Test fun centerCropMatchesTargetAspectAndIsCentered() {
        val crop = StampLayout.centerCrop(IntSize(4000, 3000), IntSize(1080, 1350))
        assertEquals(0, crop.top)
        assertEquals(3000, crop.height)
        assertTrue(abs(crop.width.toFloat() / crop.height - 0.8f) < 0.002f)
        assertEquals(crop.left, 4000 - crop.right)
    }

    @Test fun centerCropOfTallSourceIntoWideTarget() {
        val crop = StampLayout.centerCrop(IntSize(3000, 4000), IntSize(1600, 900))
        assertEquals(3000, crop.width)
        assertTrue(abs(crop.width.toFloat() / crop.height - 16f / 9f) < 0.01f)
    }

    @Test fun sampleSizeNeverUndershootsRequestedSize() {
        val sample = StampLayout.sampleSize(IntSize(4000, 3000), 1000, 750)
        assertEquals(4, sample)
        assertTrue(4000 / sample >= 1000)
        assertEquals(1, StampLayout.sampleSize(IntSize(500, 400), 1000, 800))
    }
}
