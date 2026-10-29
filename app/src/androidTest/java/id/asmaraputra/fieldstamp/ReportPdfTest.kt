package id.asmaraputra.fieldstamp

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.PhotoSource
import id.asmaraputra.fieldstamp.core.model.ReportLayout
import id.asmaraputra.fieldstamp.core.model.ReportStatus
import id.asmaraputra.fieldstamp.data.CaptureDraft
import id.asmaraputra.fieldstamp.report.GenerationFailure
import id.asmaraputra.fieldstamp.report.GenerationState
import id.asmaraputra.fieldstamp.report.ReportGenerator
import id.asmaraputra.fieldstamp.report.ReportPeriod
import id.asmaraputra.fieldstamp.report.ReportSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class ReportPdfTest {
    private lateinit var g: TestGraph
    private val zone = ZoneId.of("Asia/Jakarta")
    private val month = YearMonth.of(2026, 10)

    @Before fun setUp() { g = TestGraph(InstrumentationRegistry.getInstrumentation().targetContext) }
    @After fun tearDown() = g.close()

    private suspend fun savePhoto(day: Int, hour: Int, caption: String, gps: Boolean = true, projectId: Long? = null, w: Int = 1600, h: Int = 1200): Long {
        val millis = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        val temp = Fixtures.photo(g.files.dir(FileStore.Area.TMP), w, h)
        return g.photos.save(
            CaptureDraft(
                originalTemp = temp,
                source = PhotoSource.CAMERA,
                captureTime = CaptureTime(millis, zone.id),
                location = if (gps) GeoPoint(-6.2 + day / 1000.0, 106.8, 5f, millis) else null,
                locationName = if (gps) "Desa Contoh, Kecamatan Uji" else null,
                projectId = projectId,
                activityId = null,
                caption = caption,
                category = null,
                templateId = null,
            ),
        )
    }

    private fun generate(reportId: Long): GenerationState = runBlocking {
        assertTrue(g.generator.start(reportId, allowBranding = true, allowLayouts = true))
        withTimeout(120_000) { g.generator.state(reportId).first { it !is GenerationState.Running && it != GenerationState.Idle } }
    }

    private fun pageCount(path: String): Int =
        ParcelFileDescriptor.open(g.files.resolve(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(0).use { p ->
                    assertEquals(595, p.width)
                    assertEquals(841, p.height)
                }
                r.pageCount
            }
        }

    @Test fun generatesValidMultiPagePdfWithSameDayPhotos() = runBlocking {
        savePhoto(5, 8, "Pagi")
        savePhoto(5, 13, "Siang", gps = false)
        savePhoto(5, 16, "")
        (6..20).forEach { savePhoto(it, 9, "Hari $it") }
        val id = g.reports.create("Laporan Uji", ReportPeriod.month(month), null, null, "Petugas", false)

        assertEquals(GenerationState.Succeeded, generate(id))
        val report = g.reports.get(id)!!
        assertEquals(ReportStatus.GENERATED, report.status)
        val path = report.pdfPath!!
        assertTrue(ReportGenerator.isValidPdf(g.files.resolve(path)))
        assertTrue("18 photos at 6 per page need several pages", pageCount(path) >= 3)
        // Snapshots were frozen for every item.
        assertTrue(g.reports.observeItems(id).first().all { it.entry.snapshot != null })
        assertEquals("Desa Contoh, Kecamatan Uji", g.reports.observeItems(id).first().first { it.entry.snapshot!!.caption == "Pagi" }.entry.snapshot!!.locationName)
    }

    @Test fun allLayoutsRenderAndPreserveSelection() = runBlocking {
        repeat(4) { savePhoto(3, 8 + it, "Foto $it", w = if (it % 2 == 0) 1200 else 1600, h = if (it % 2 == 0) 1600 else 900) }
        val id = g.reports.create("Tata letak", ReportPeriod.month(month), null, null, "", false)
        ReportLayout.entries.forEach { layout ->
            g.reports.updateSettings(id, ReportSettings("Tata letak", "Catatan", "Penutup", "Petugas", true, true, layout, false, true))
            assertEquals(GenerationState.Succeeded, generate(id))
            g.generator.acknowledge(id)
            assertTrue(pageCount(g.reports.get(id)!!.pdfPath!!) >= 1)
        }
    }

    @Test fun generatedPdfIsNotChangedByLaterEditsUntilRegenerated() = runBlocking {
        val photo = savePhoto(7, 10, "Sebelum")
        val id = g.reports.create("Snapshot", ReportPeriod.month(month), null, null, "", false)
        assertEquals(GenerationState.Succeeded, generate(id))
        g.generator.acknowledge(id)
        val first = g.reports.get(id)!!
        val firstBytes = g.files.resolve(first.pdfPath!!).readBytes()

        g.photos.update(photo, "Sesudah", null, null, null, null)
        val afterEdit = g.reports.get(id)!!
        assertTrue(afterEdit.pdfOutdated)
        assertEquals(first.pdfPath, afterEdit.pdfPath)
        assertTrue(firstBytes.contentEquals(g.files.resolve(afterEdit.pdfPath!!).readBytes()))
        assertEquals("Sebelum", g.reports.observeItems(id).first().single().entry.snapshot!!.caption)

        assertEquals(GenerationState.Succeeded, generate(id))
        val regenerated = g.reports.get(id)!!
        assertFalse(regenerated.pdfOutdated)
        assertNotEquals(first.pdfPath, regenerated.pdfPath)
        assertFalse("superseded PDF is removed", g.files.exists(first.pdfPath))
        assertEquals("Sesudah", g.reports.observeItems(id).first().single().entry.snapshot!!.caption)
    }

    @Test fun missingSourceBlocksGenerationWithoutPartialFile() = runBlocking {
        savePhoto(8, 9, "Ada")
        val gone = savePhoto(8, 10, "Akan dihapus")
        val id = g.reports.create("Hilang", ReportPeriod.month(month), null, null, "", false)
        g.photos.delete(gone)

        val state = generate(id)
        assertEquals(GenerationState.Failed(GenerationFailure.MISSING_SOURCES), state)
        val report = g.reports.get(id)!!
        assertEquals(ReportStatus.FAILED, report.status)
        assertEquals(null, report.pdfPath)
        assertTrue(g.files.dir(FileStore.Area.REPORT).listFiles().orEmpty().isEmpty())

        g.reports.removeMissing(id)
        g.generator.acknowledge(id)
        assertEquals(GenerationState.Succeeded, generate(id))
    }

    @Test fun emptyReportIsRejected() = runBlocking {
        val id = g.reports.create("Kosong", ReportPeriod.month(YearMonth.of(2026, 2)), null, null, "", false)
        assertEquals(GenerationState.Failed(GenerationFailure.EMPTY), generate(id))
    }

    @Test fun cancellationLeavesPreviousStateIntact() = runBlocking {
        repeat(30) { savePhoto(1 + it % 28, 9, "Foto $it") }
        val id = g.reports.create("Batal", ReportPeriod.month(month), null, null, "", false)
        assertTrue(g.generator.start(id, allowBranding = false, allowLayouts = false))
        assertTrue("duplicate start is rejected", !g.generator.start(id, false, false))
        // Cancel immediately: 30 full renders cannot finish first, so the outcome is deterministic.
        g.generator.cancel(id)
        val end = withTimeout(60_000) { g.generator.state(id).first { it !is GenerationState.Running } }
        assertEquals(GenerationState.Cancelled, end)
        assertEquals(null, g.reports.get(id)!!.pdfPath)
        assertTrue(g.files.dir(FileStore.Area.REPORT).listFiles().orEmpty().isEmpty())
    }

    @Test fun longCaptionsDoNotBreakGeneration() = runBlocking {
        savePhoto(9, 9, "Keterangan sangat panjang. ".repeat(40))
        val id = g.reports.create("Panjang", ReportPeriod.month(month), null, null, "", false)
        assertEquals(GenerationState.Succeeded, generate(id))
        assertNotNull(g.reports.get(id)!!.pdfPath)
    }
}
