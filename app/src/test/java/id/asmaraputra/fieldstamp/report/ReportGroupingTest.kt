package id.asmaraputra.fieldstamp.report

import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.PhotoRecord
import id.asmaraputra.fieldstamp.core.model.PhotoSource
import id.asmaraputra.fieldstamp.core.model.ReportItem
import id.asmaraputra.fieldstamp.core.model.ReportPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class ReportGroupingTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")

    private fun millis(day: Int, hour: Int, minute: Int = 0, month: Int = 10) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, jakarta).toInstant().toEpochMilli()

    private fun photo(id: Long, millis: Long, caption: String = "ok", location: GeoPoint? = GeoPoint(0.0, 0.0, 1f, 0)): PhotoRecord {
        val time = CaptureTime(millis, jakarta.id)
        return PhotoRecord(id, null, null, PhotoSource.CAMERA, "photos/original/$id.jpg", "photos/rendered/$id.jpg", null, time, time.localDate, location, null, caption, null, null, 100, 100, 0, 0)
    }

    private fun item(id: Long, photo: PhotoRecord?, reportDate: LocalDate = photo!!.captureLocalDate, order: Int = 0, override: String? = null) =
        ReportItem(ReportPhoto(id, 1, photo?.id, reportDate, order, override, null), photo?.let { PhotoListItem(it, null, null) })

    @Test fun monthPeriodHandlesLengthsAndLeapYears() {
        assertEquals(LocalDate.of(2028, 2, 29), ReportPeriod.month(YearMonth.of(2028, 2)).end)
        assertEquals(LocalDate.of(2026, 2, 28), ReportPeriod.month(YearMonth.of(2026, 2)).end)
        assertEquals(29, ReportPeriod.month(YearMonth.of(2028, 2)).days)
        assertEquals(31, ReportPeriod.month(YearMonth.of(2026, 12)).days)
    }

    @Test fun periodBoundariesAreInclusive() {
        val p = ReportPeriod.month(YearMonth.of(2026, 10))
        assertTrue(LocalDate.of(2026, 10, 1) in p)
        assertTrue(LocalDate.of(2026, 10, 31) in p)
        assertFalse(LocalDate.of(2026, 9, 30) in p)
        assertFalse(LocalDate.of(2026, 11, 1) in p)
    }

    @Test(expected = IllegalArgumentException::class)
    fun periodRejectsInvertedRange() { ReportPeriod(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)) }

    @Test fun multiplePhotosOnSameDayAreGroupedChronologically() {
        val a = photo(1, millis(5, 15))
        val b = photo(2, millis(5, 8))
        val c = photo(3, millis(7, 9))
        val groups = ReportGrouping.groupItems(listOf(item(10, a), item(11, b), item(12, c)))
        assertEquals(listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)), groups.map { it.date })
        assertEquals(listOf(2L, 1L), groups[0].items.map { it.photo!!.photo.id })
    }

    @Test fun explicitDisplayOrderOverridesCaptureTime() {
        val early = photo(1, millis(5, 8))
        val late = photo(2, millis(5, 15))
        val groups = ReportGrouping.groupItems(listOf(item(10, early, order = 1), item(11, late, order = 0)))
        assertEquals(listOf(2L, 1L), groups.single().items.map { it.photo!!.photo.id })
    }

    @Test fun orderingIsDeterministicForIdenticalTimestamps() {
        val t = millis(5, 10)
        val groups = ReportGrouping.groupItems(listOf(item(12, photo(3, t)), item(10, photo(1, t)), item(11, photo(2, t))))
        assertEquals(listOf(10L, 11L, 12L), groups.single().items.map { it.entry.id })
    }

    @Test fun daysWithoutPhotosAreNotListed() {
        val groups = ReportGrouping.groupItems(listOf(item(1, photo(1, millis(1, 9))), item(2, photo(2, millis(30, 9)))))
        assertEquals(2, groups.size)
    }

    @Test fun crossMidnightReassignmentKeepsCaptureTimestamp() {
        val afterMidnight = photo(1, millis(6, 0, 40))
        val reassigned = item(10, afterMidnight, reportDate = LocalDate.of(2026, 10, 5))
        val groups = ReportGrouping.groupItems(listOf(reassigned))
        assertEquals(LocalDate.of(2026, 10, 5), groups.single().date)
        assertEquals(LocalDate.of(2026, 10, 6), groups.single().items.single().photo!!.photo.captureLocalDate)
    }

    @Test fun chronologicalOrderIndexesWithinEachDay() {
        val photos = listOf(photo(1, millis(5, 15)), photo(2, millis(5, 8)), photo(3, millis(6, 9)))
        val order = ReportGrouping.chronologicalOrder(photos, { it.captureLocalDate }, { it.captureTime.epochMillis }, { it.id })
        assertEquals(mapOf(2L to 0, 1L to 1, 3L to 0), order)
    }

    @Test fun issuesFlagMissingMetadataWithoutBlocking() {
        val p = photo(1, millis(5, 9), caption = "", location = null)
        val issues = ReportGrouping.issues(item(1, p), { true }) { it.photo!!.photo.displayPath }
        assertEquals(setOf(ItemIssue.NO_CAPTION, ItemIssue.NO_GPS), issues)
        assertFalse(issues.any(ReportGrouping::isBlocking))
    }

    @Test fun reportCaptionOverrideSatisfiesCaptionCheck() {
        val p = photo(1, millis(5, 9), caption = "")
        val issues = ReportGrouping.issues(item(1, p, override = "Keterangan laporan"), { true }) { it.photo!!.photo.displayPath }
        assertFalse(ItemIssue.NO_CAPTION in issues)
    }

    @Test fun deletedPhotoAndMissingFileAreBlocking() {
        val deleted = ReportGrouping.issues(item(1, null, reportDate = LocalDate.of(2026, 10, 5)), { true }) { null }
        assertEquals(setOf(ItemIssue.MISSING_RECORD), deleted)
        val missingFile = ReportGrouping.issues(item(2, photo(2, millis(5, 9))), { false }) { it.photo!!.photo.displayPath }
        assertTrue(ItemIssue.MISSING_FILE in missingFile)
        assertTrue(missingFile.any(ReportGrouping::isBlocking))
    }

    @Test fun largePhotoCountGroupsEveryPhoto() {
        val items = (0 until 1200).map { i -> item(i.toLong(), photo(i.toLong(), millis(1 + i % 31, 8 + (i / 31) % 10, i % 60))) }
        val groups = ReportGrouping.groupItems(items)
        assertEquals(31, groups.size)
        assertEquals(1200, groups.sumOf { it.items.size })
    }
}
