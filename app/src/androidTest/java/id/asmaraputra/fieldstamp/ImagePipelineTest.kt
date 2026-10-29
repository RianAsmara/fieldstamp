package id.asmaraputra.fieldstamp

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.asmaraputra.fieldstamp.core.image.BitmapIO
import id.asmaraputra.fieldstamp.core.image.StampData
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.StampField
import id.asmaraputra.fieldstamp.core.model.StampFieldSetting
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import id.asmaraputra.fieldstamp.data.TemplateImportException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePipelineTest {
    private lateinit var g: TestGraph
    private val time = CaptureTime(1_791_000_000_000, "Asia/Jakarta")
    private val point = GeoPoint(-6.2, 106.8, 6f, 1_791_000_000_000)

    @Before fun setUp() { g = TestGraph(InstrumentationRegistry.getInstrumentation().targetContext) }
    @After fun tearDown() = g.close()

    private fun data(location: GeoPoint? = point) = StampData(time, "Proyek Uji", "Kegiatan Uji", "Keterangan uji", null, location)

    @Test fun transparentRegionsRevealPhotoAndFrameStaysOpaque() = runTest {
        val photo = Fixtures.photo(g.root)
        val frame = Fixtures.framePng(g.root)
        val textOff = TemplateConfig(fields = listOf(StampFieldSetting(StampField.DATE_TIME, false)))
        val out = g.renderer.render(photo, frame, textOff, data())
        try {
            assertEquals(1080f / 1350f, out.width.toFloat() / out.height, 0.005f)
            assertTrue("center should show the photo", Fixtures.similar(out.getPixel(out.width / 2, out.height / 2), Fixtures.PHOTO_COLOR))
            assertTrue("frame should cover the edge", Fixtures.similar(out.getPixel(5, out.height / 2), Fixtures.FRAME_COLOR))
        } finally {
            out.recycle()
        }
    }

    @Test fun customSolidBoxCoversTemplatePlaceholders() = runTest {
        val photo = Fixtures.photo(g.root)
        val frame = Fixtures.framePng(g.root) // red border 90 px on 1080×1350
        val cream = 0xFFFDF6E3.toInt()
        // Box over the bottom border, where a template would print placeholder text.
        val cfg = TemplateConfig(
            anchor = id.asmaraputra.fieldstamp.core.model.StampAnchor.CUSTOM,
            box = id.asmaraputra.fieldstamp.core.model.StampBox(left = 0.2f, top = 0.9f, width = 0.6f, height = 0.1f),
            backdropStyle = id.asmaraputra.fieldstamp.core.model.BackdropStyle.SOLID,
            solidColor = cream,
            textColor = id.asmaraputra.fieldstamp.core.model.StampTextColor.BLACK,
        )
        val out = g.renderer.render(photo, frame, cfg, data())
        try {
            // Right edge of the box, away from the left-aligned text: solid fill hides the red border.
            assertTrue(Fixtures.similar(out.getPixel((out.width * 0.78f).toInt(), (out.height * 0.985f).toInt()), cream))
            // Outside the box the template is unchanged.
            assertTrue(Fixtures.similar(out.getPixel((out.width * 0.1f).toInt(), (out.height * 0.985f).toInt()), Fixtures.FRAME_COLOR))
            // Text was drawn inside the box (dark pixels present near its top-left).
            val x0 = (out.width * 0.2f).toInt(); val y0 = (out.height * 0.9f).toInt()
            var dark = 0
            for (y in y0 until y0 + out.height / 25) for (x in x0 until x0 + out.width / 4) if (Color.red(out.getPixel(x, y)) < 90) dark++
            assertTrue("expected text pixels, found $dark", dark > 20)
        } finally {
            out.recycle()
        }
    }

    @Test fun exifRotationIsAppliedBeforeCompositing() = runTest {
        val photo = Fixtures.photo(g.root, width = 1600, height = 1200, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val size = BitmapIO.orientedSize(photo)
        assertEquals(1200, size.width)
        assertEquals(1600, size.height)
        val noText = TemplateConfig(fields = TemplateConfig.DEFAULT_FIELDS.map { it.copy(enabled = false) })
        val out = g.renderer.render(photo, null, noText, data())
        try {
            assertTrue("portrait output", out.height > out.width)
            // Raw top-left marker ends up at the top-right after a 90° clockwise rotation.
            assertTrue(Fixtures.similar(out.getPixel(out.width - 5, 5), Color.BLUE, 60))
            assertTrue(Fixtures.similar(out.getPixel(5, 5), Fixtures.PHOTO_COLOR))
        } finally {
            out.recycle()
        }
    }

    @Test fun textBlockIsDrawnWhenFieldsAreEnabled() = runTest {
        val photo = Fixtures.photo(g.root)
        val out = g.renderer.render(photo, null, TemplateConfig(), data())
        try {
            // Bottom-left default anchor: the backdrop darkens the photo there.
            val sample = out.getPixel((out.width * 0.08f).toInt(), (out.height * 0.92f).toInt())
            assertTrue(!Fixtures.similar(sample, Fixtures.PHOTO_COLOR, 10))
            assertTrue(Fixtures.similar(out.getPixel(out.width - 10, 10), Fixtures.PHOTO_COLOR))
        } finally {
            out.recycle()
        }
    }

    @Test fun renderedJpegIsValidAndCarriesExif() = runTest {
        val photo = Fixtures.photo(g.root)
        val rel = g.files.relativize(photo)
        val rendered = g.processor.render(rel, null, TemplateConfig(), data(), embedGps = true)
        val file = g.files.resolve(rendered.renderedPath)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertEquals(rendered.widthPx, bounds.outWidth)
        val exif = ExifInterface(file)
        val expected = java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss").format(time.zoned)
        assertEquals(expected, exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+07:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertNotNull(exif.latLong)
        assertEquals(-6.2, exif.latLong!![0], 0.0001)
        assertTrue(g.files.exists(rendered.thumbnailPath))
        // Original is never modified.
        assertNull(ExifInterface(photo).getAttribute(ExifInterface.TAG_SOFTWARE))
    }

    @Test fun gpsIsOmittedWhenUnavailableOrDisabled() = runTest {
        val photo = Fixtures.photo(g.root)
        val rel = g.files.relativize(photo)
        val noFix = g.processor.render(rel, null, TemplateConfig(), data(location = null), embedGps = true)
        assertNull(ExifInterface(g.files.resolve(noFix.renderedPath)).latLong)
        val disabled = g.processor.render(rel, null, TemplateConfig(), data(), embedGps = false)
        assertNull(ExifInterface(g.files.resolve(disabled.renderedPath)).latLong)
    }

    @Test fun templateImportValidatesTransparency() = runTest {
        val good = g.templates.stageImport(Uri.fromFile(Fixtures.framePng(g.root)))
        assertEquals(1080, good.width)
        val id = g.templates.save(good, "Bingkai", TemplateConfig())
        val saved = g.templates.get(id)!!
        assertTrue(g.templates.isSourceAvailable(saved))
        assertTrue(saved.isDefault)
        // Opaque PNG and JPG are not rejected outright: they are offered the white-key conversion.
        try {
            g.templates.stageImport(Uri.fromFile(Fixtures.opaquePng(g.root)))
            fail("opaque PNG must need conversion")
        } catch (_: TemplateImportException.NeedsWhiteKey) {
        }
        try {
            g.templates.stageImport(Uri.fromFile(Fixtures.photo(g.root)))
            fail("JPG must need conversion")
        } catch (_: TemplateImportException.NeedsWhiteKey) {
        }
    }

    @Test fun jpgFrameWithWhiteWindowBecomesTransparentTemplate() = runTest {
        val jpg = Fixtures.whiteWindowJpg(g.root)
        val candidate = try {
            g.templates.stageImport(Uri.fromFile(jpg)); fail("JPG must need conversion"); return@runTest
        } catch (e: TemplateImportException.NeedsWhiteKey) { e.candidate }
        val staged = g.templates.stageWhiteKeyed(candidate)
        val bmp = BitmapFactory.decodeFile(staged.file.path)
        try {
            assertEquals(0, Color.alpha(bmp.getPixel(bmp.width / 2, bmp.height / 2)))      // window cleared
            assertEquals(255, Color.alpha(bmp.getPixel(10, bmp.height / 2)))                 // frame kept
            assertEquals(255, Color.alpha(bmp.getPixel(Fixtures.TEXT_X, Fixtures.TEXT_Y)))  // white text kept
        } finally {
            bmp.recycle()
        }
        assertTrue("original JPG left untouched", jpg.isFile)
        // The converted PNG composes like any transparent template.
        val out = g.renderer.render(Fixtures.photo(g.root), staged.file, TemplateConfig(fields = TemplateConfig.DEFAULT_FIELDS.map { it.copy(enabled = false) }), data())
        try {
            assertTrue(Fixtures.similar(out.getPixel(out.width / 2, out.height / 2), Fixtures.PHOTO_COLOR))
        } finally {
            out.recycle()
        }
    }

    @Test fun grayWindowIsReportedAsNoWhiteArea() = runTest {
        val candidate = try {
            g.templates.stageImport(Uri.fromFile(Fixtures.whiteWindowJpg(g.root, window = Color.GRAY))); fail("expected conversion"); return@runTest
        } catch (e: TemplateImportException.NeedsWhiteKey) { e.candidate }
        try {
            g.templates.stageWhiteKeyed(candidate)
            fail("gray window must not be keyed")
        } catch (_: TemplateImportException.NoWhiteArea) {
        }
    }

    @Test fun importedTemplateBytesAreStoredUnchanged() = runTest {
        val source = Fixtures.framePng(g.root)
        val staged = g.templates.stageImport(Uri.fromFile(source))
        val id = g.templates.save(staged, "Asli", TemplateConfig())
        val stored = g.templates.sourceFile(g.templates.get(id)!!)
        assertTrue(source.readBytes().contentEquals(stored.readBytes()))
    }
}
