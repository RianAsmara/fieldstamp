package id.asmaraputra.fieldstamp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.asmaraputra.fieldstamp.core.image.StampRenderer
import id.asmaraputra.fieldstamp.core.model.PhotoSource
import id.asmaraputra.fieldstamp.core.model.ReportStatus
import id.asmaraputra.fieldstamp.core.settings.SettingsRepository
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.data.PhotoFilter
import id.asmaraputra.fieldstamp.data.PhotoProcessor
import id.asmaraputra.fieldstamp.data.PhotoRepository
import id.asmaraputra.fieldstamp.data.ProjectRepository
import id.asmaraputra.fieldstamp.data.TemplateRepository
import id.asmaraputra.fieldstamp.report.ReportPeriod
import id.asmaraputra.fieldstamp.report.ReportRepository
import id.asmaraputra.fieldstamp.report.ReportSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: FieldStampDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var photos: PhotoRepository
    private lateinit var reports: ReportRepository
    private val zone = ZoneId.of("Asia/Jakarta")

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FieldStampDatabase::class.java).allowMainThreadQueries().build()
        val files = FileStore(context)
        val settings = SettingsRepository(context)
        projects = ProjectRepository(db.projectDao(), db.activityDao())
        val templates = TemplateRepository(context, db.templateDao(), files)
        photos = PhotoRepository(context, db.photoDao(), db.reportDao(), projects, templates, settings, PhotoProcessor(files, StampRenderer(context)), files)
        reports = ReportRepository(db, photos, projects, files)
    }

    @After fun tearDown() = db.close()

    private suspend fun insertPhoto(day: LocalDate, hour: Int, projectId: Long? = null, activityId: Long? = null, caption: String = "Foto", lat: Double? = null): Long {
        val zoned = ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, 0, 0, 0, zone)
        return db.photoDao().insert(
            PhotoEntity(
                projectId = projectId, activityId = activityId, source = PhotoSource.CAMERA,
                originalPath = "photos/original/x$day$hour.jpg", renderedPath = null, thumbnailPath = null,
                capturedAtEpochMillis = zoned.toInstant().toEpochMilli(), captureTimeZoneId = zone.id, captureEpochDay = day.toEpochDay(),
                latitude = lat, longitude = lat?.let { 106.8 }, locationAccuracyMeters = null, locationCapturedAtEpochMillis = null,
                caption = caption, category = null, templateId = null, widthPx = 100, heightPx = 100, createdAt = 0, updatedAt = 0,
            ),
        )
    }

    @Test fun dateRangeQueryIsInclusiveAndExcludesNeighbouringMonths() = runTest {
        insertPhoto(LocalDate.of(2026, 9, 30), 23)
        insertPhoto(LocalDate.of(2026, 10, 1), 0)
        insertPhoto(LocalDate.of(2026, 10, 31), 23)
        insertPhoto(LocalDate.of(2026, 11, 1), 0)
        val oct = photos.observe(PhotoFilter(start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 31))).first()
        assertEquals(2, oct.size)
    }

    @Test fun projectAndMetadataFilters() = runTest {
        val p = projects.createProject("Jalan Desa", "")
        insertPhoto(LocalDate.of(2026, 10, 2), 9, projectId = p, caption = "", lat = -6.2)
        insertPhoto(LocalDate.of(2026, 10, 2), 10, caption = "Talud", lat = null)
        assertEquals(1, photos.observe(PhotoFilter(projectId = p)).first().size)
        assertEquals("Jalan Desa", photos.observe(PhotoFilter(projectId = p)).first().single().projectName)
        assertEquals(1, photos.observe(PhotoFilter(missingCaptionOnly = true)).first().size)
        assertEquals(1, photos.observe(PhotoFilter(missingGpsOnly = true)).first().size)
        assertEquals(1, photos.observe(PhotoFilter(query = "talud")).first().size)
        assertEquals(1, photos.observe(PhotoFilter(query = "desa")).first().size)
    }

    @Test fun deletingProjectKeepsPhotosAndClearsReference() = runTest {
        val p = projects.createProject("Sementara", "")
        val a = projects.createActivity(p, "Survei", "")
        val photo = insertPhoto(LocalDate.of(2026, 10, 3), 9, projectId = p, activityId = a)
        projects.deleteProject(p)
        val row = db.photoDao().get(photo)!!
        assertNull(row.projectId)
        assertEquals(a, row.activityId)
        assertNull(db.activityDao().get(a)!!.projectId)
    }

    @Test fun createReportPreselectsMatchingPhotosInChronologicalOrder() = runTest {
        val p = projects.createProject("A", "")
        val late = insertPhoto(LocalDate.of(2026, 10, 5), 15, projectId = p)
        val early = insertPhoto(LocalDate.of(2026, 10, 5), 8, projectId = p)
        insertPhoto(LocalDate.of(2026, 10, 5), 9) // other project
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), p, null, "", false)
        val items = reports.observeItems(id).first().sortedBy { it.entry.displayOrder }
        assertEquals(listOf(early, late), items.map { it.entry.photoRecordId })
        assertEquals(ReportStatus.DRAFT, reports.get(id)!!.status)
    }

    @Test fun excludingFromReportDoesNotDeletePhoto() = runTest {
        val photo = insertPhoto(LocalDate.of(2026, 10, 5), 8)
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        val item = reports.observeItems(id).first().single()
        reports.exclude(id, listOf(item.entry.id))
        assertTrue(reports.observeItems(id).first().isEmpty())
        assertTrue(db.photoDao().get(photo) != null)
    }

    @Test fun deletingPhotoLeavesVisibleMissingReportItem() = runTest {
        val photo = insertPhoto(LocalDate.of(2026, 10, 5), 8)
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        photos.delete(photo)
        val item = reports.observeItems(id).first().single()
        assertTrue(item.isMissingRecord)
        reports.removeMissing(id)
        assertTrue(reports.observeItems(id).first().isEmpty())
    }

    @Test fun deletingReportCascadesItemsButKeepsPhotos() = runTest {
        val photo = insertPhoto(LocalDate.of(2026, 10, 5), 8)
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        reports.delete(id)
        assertTrue(db.reportDao().items(id).isEmpty())
        assertTrue(db.photoDao().get(photo) != null)
    }

    @Test fun reportCaptionOverrideLeavesPhotoCaptionUntouched() = runTest {
        val photo = insertPhoto(LocalDate.of(2026, 10, 5), 8, caption = "Asli")
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        val item = reports.observeItems(id).first().single()
        reports.setCaption(id, item.entry.id, "Khusus laporan")
        assertEquals("Khusus laporan", reports.observeItems(id).first().single().effectiveCaption)
        assertEquals("Asli", db.photoDao().get(photo)!!.caption)
        reports.setCaption(id, item.entry.id, null)
        assertEquals("Asli", reports.observeItems(id).first().single().effectiveCaption)
    }

    @Test fun reportDateReassignmentPreservesCaptureTimestamp() = runTest {
        val photo = insertPhoto(LocalDate.of(2026, 10, 6), 0)
        val captured = db.photoDao().get(photo)!!.capturedAtEpochMillis
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        val item = reports.observeItems(id).first().single()
        reports.setReportDate(id, item.entry.id, LocalDate.of(2026, 10, 5))
        assertEquals(LocalDate.of(2026, 10, 5), reports.observeItems(id).first().single().entry.reportDate)
        assertEquals(captured, db.photoDao().get(photo)!!.capturedAtEpochMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reportDateMustStayInsidePeriod() = runTest {
        insertPhoto(LocalDate.of(2026, 10, 6), 9)
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        val item = reports.observeItems(id).first().single()
        reports.setReportDate(id, item.entry.id, LocalDate.of(2026, 11, 1))
    }

    @Test fun reorderSwapsWithinDayOnly() = runTest {
        val a = insertPhoto(LocalDate.of(2026, 10, 5), 8)
        val b = insertPhoto(LocalDate.of(2026, 10, 5), 9)
        val c = insertPhoto(LocalDate.of(2026, 10, 5), 10)
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        val itemC = reports.observeItems(id).first().first { it.entry.photoRecordId == c }
        reports.move(id, itemC.entry.id, -1)
        reports.move(id, itemC.entry.id, -1)
        reports.move(id, itemC.entry.id, -1) // already first: no-op
        val order = reports.observeItems(id).first().sortedBy { it.entry.displayOrder }.map { it.entry.photoRecordId }
        assertEquals(listOf(c, a, b), order)
    }

    @Test fun changesMarkGeneratedPdfOutdatedButNotDrafts() = runTest {
        insertPhoto(LocalDate.of(2026, 10, 5), 8)
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        val settings = ReportSettings("Okt", "", "", "", true, true, id.let { reports.get(it)!!.layout }, false, true)
        reports.updateSettings(id, settings)
        assertFalse(reports.get(id)!!.pdfOutdated)

        val entity = db.reportDao().get(id)!!
        db.reportDao().update(entity.copy(pdfPath = "reports/x.pdf", status = ReportStatus.GENERATED))
        reports.updateSettings(id, settings.copy(title = "Oktober"))
        assertTrue(reports.get(id)!!.pdfOutdated)
    }

    @Test fun includingAPhotoTwiceIsIdempotent() = runTest {
        insertPhoto(LocalDate.of(2026, 10, 5), 8)
        val id = reports.create("Okt", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        val photo = photos.observe(PhotoFilter()).first().single()
        reports.include(id, photo)
        assertEquals(1, reports.observeItems(id).first().size)
    }

    @Test fun samePhotoCanAppearInMultipleReports() = runTest {
        val photo = insertPhoto(LocalDate.of(2026, 10, 5), 8)
        reports.create("A", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        reports.create("B", ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)), null, null, "", false)
        assertEquals(2, photos.reportUsageCount(photo))
    }

    @Test fun emptyMonthCreatesEmptyDraft() = runTest {
        val id = reports.create("Kosong", ReportPeriod.month(YearMonth.of(2026, 2)), null, null, "", false)
        assertTrue(reports.observeItems(id).first().isEmpty())
    }

    @Test fun largeDatasetQueriesReturnEverything() = runTest {
        repeat(600) { i -> insertPhoto(LocalDate.of(2026, 10, 1 + i % 31), 6 + i % 12) }
        val id = reports.create("Besar", ReportPeriod.month(YearMonth.of(2026, 10)), null, null, "", false)
        assertEquals(600, reports.observeItems(id).first().size)
    }
}
