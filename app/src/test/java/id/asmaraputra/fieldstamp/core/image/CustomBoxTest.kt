package id.asmaraputra.fieldstamp.core.image

import id.asmaraputra.fieldstamp.core.model.BackdropStyle
import id.asmaraputra.fieldstamp.core.model.StampAnchor
import id.asmaraputra.fieldstamp.core.model.StampBox
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomBoxTest {
    @Test fun fitShrinksUntilTextFits() {
        // Height grows linearly with size: 5 lines × 1.2 line height.
        val size = StampLayout.fitTextSize(start = 40f, min = 10f, maxHeight = 120f) { it * 6f }
        assertTrue(size * 6f <= 120f)
        assertTrue(size > 17f)
    }

    @Test fun fitKeepsStartSizeWhenItAlreadyFits() {
        assertEquals(30f, StampLayout.fitTextSize(30f, 10f, 500f) { it * 6f })
    }

    @Test fun fitStopsAtMinimum() {
        assertEquals(10f, StampLayout.fitTextSize(40f, 10f, 5f) { it * 6f })
    }

    @Test fun boxIsClampedInsideCanvas() {
        val b = StampBox(left = 0.9f, top = -1f, width = 0.5f, height = 2f).normalized()
        assertEquals(0.9f, b.left)
        assertEquals(0f, b.top)
        assertTrue(b.left + b.width <= 1f + 1e-6f)
        assertTrue(b.top + b.height <= 1f + 1e-6f)
        val rect = StampLayout.boxRect(IntSize(1000, 2000), StampBox(0.25f, 0.5f, 0.5f, 0.25f))
        assertEquals(listOf(250f, 1000f, 750f, 1500f), rect.toList())
    }

    @Test fun dominantColorIgnoresTextAndTransparency() {
        val cream = 0xFFFDF6E3.toInt()
        val navyText = 0xFF1B2A6B.toInt()
        val region = IntArray(1000) { i -> when { i % 10 == 0 -> navyText; i % 17 == 0 -> 0x00000000; else -> cream } }
        val c = ColorSampler.dominantOpaqueColor(region)!!
        assertTrue(kotlin.math.abs(((c shr 16) and 0xFF) - 0xFD) <= 2)
        assertTrue(kotlin.math.abs((c and 0xFF) - 0xE3) <= 2)
        assertNull(ColorSampler.dominantOpaqueColor(IntArray(10) { 0 }))
    }

    @Test fun legacyConfigsKeepTheirBackdropBehaviour() {
        assertEquals(BackdropStyle.NONE, TemplateConfig.fromJson("""{"backdrop":false}""").effectiveBackdrop)
        assertEquals(BackdropStyle.SHADE, TemplateConfig.fromJson("""{"backdrop":true}""").effectiveBackdrop)
        assertEquals(StampAnchor.BOTTOM_LEFT, TemplateConfig.fromJson("""{"backdrop":true}""").anchor)
    }

    @Test fun customConfigRoundTrips() {
        val cfg = TemplateConfig(anchor = StampAnchor.CUSTOM, box = StampBox(0.3f, 0.73f, 0.45f, 0.2f), backdropStyle = BackdropStyle.SOLID, solidColor = 0xFFFDF6E3.toInt())
        assertEquals(cfg.normalized(), TemplateConfig.fromJson(cfg.toJson()))
    }
}
