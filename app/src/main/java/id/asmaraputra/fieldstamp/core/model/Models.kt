package id.asmaraputra.fieldstamp.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class Project(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class FieldActivity(
    val id: Long,
    val projectId: Long?,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Where the original image came from. Imported images keep their own capture time when EXIF provides one. */
enum class PhotoSource { CAMERA, IMPORT }

/** A device-provided location fix. Never fabricated; absent when unavailable or not permitted. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtEpochMillis: Long,
)

/**
 * Capture time as reported by the device clock, plus the zone it was recorded in.
 * The local date is fixed at capture time so a later change of time zone does not move photos between days.
 */
data class CaptureTime(
    val epochMillis: Long,
    val zoneId: String,
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    val zoned: ZonedDateTime get() = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val localDate: LocalDate get() = zoned.toLocalDate()

    companion object {
        fun now(): CaptureTime = CaptureTime(System.currentTimeMillis(), ZoneId.systemDefault().id)
    }
}

data class PhotoRecord(
    val id: Long,
    val projectId: Long?,
    val activityId: Long?,
    val source: PhotoSource,
    /** App-relative path of the untouched original capture or imported copy. */
    val originalPath: String,
    /** App-relative path of the stamped output, if rendered. */
    val renderedPath: String?,
    val thumbnailPath: String?,
    val captureTime: CaptureTime,
    val captureLocalDate: LocalDate,
    val location: GeoPoint?,
    /** Human-readable place (reverse-geocoded or typed by the user). Independent of coordinates. */
    val locationName: String?,
    val caption: String,
    val category: String?,
    val templateId: Long?,
    val widthPx: Int,
    val heightPx: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val hasCaption: Boolean get() = caption.isNotBlank()
    val displayPath: String get() = renderedPath ?: originalPath
}

/** Photo with resolved project/activity names, as shown in lists. */
data class PhotoListItem(
    val photo: PhotoRecord,
    val projectName: String?,
    val activityName: String?,
)

data class StampTemplate(
    val id: Long,
    val name: String,
    /** App-relative path of the imported PNG overlay. Never modified after import. */
    val sourcePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val config: TemplateConfig,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ReportStatus { DRAFT, GENERATED, FAILED }

enum class ReportLayout(val columns: Int, val requiresPro: Boolean) {
    STANDARD(2, false),
    LARGE(1, true),
    COMPACT(3, true),
}

data class Report(
    val id: Long,
    val title: String,
    val projectId: Long?,
    val activityId: Long?,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val status: ReportStatus,
    val notes: String,
    val closingNotes: String,
    val preparedBy: String,
    val includeGps: Boolean,
    val includeSignatureBlock: Boolean,
    val layout: ReportLayout,
    val useBranding: Boolean,
    /** Use the stamped copy (true) or the clean original (false) in the PDF. */
    val useStampedPhotos: Boolean,
    /** App-relative path to the last successfully generated PDF. */
    val pdfPath: String?,
    val generatedAt: Long?,
    /** True when the selection or settings changed after the current PDF was generated. */
    val pdfOutdated: Boolean,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Values frozen into a report item when the PDF is generated.
 * Later edits to the photo record do not alter these until the user explicitly regenerates.
 */
@kotlinx.serialization.Serializable
data class ReportPhotoSnapshot(
    val caption: String,
    val capturedAtEpochMillis: Long,
    val zoneId: String,
    val projectName: String? = null,
    val activityName: String? = null,
    val category: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locationName: String? = null,
    val imagePath: String,
)

data class ReportPhoto(
    val id: Long,
    val reportId: Long,
    /** Null when the underlying photo record was deleted after being added. */
    val photoRecordId: Long?,
    val reportDate: LocalDate,
    val displayOrder: Int,
    /** Report-only caption. Null means "use the photo's caption". */
    val captionOverride: String?,
    val snapshot: ReportPhotoSnapshot?,
)

/** A report item joined with its live photo, if the photo still exists. */
data class ReportItem(
    val entry: ReportPhoto,
    val photo: PhotoListItem?,
) {
    val isMissingRecord: Boolean get() = photo == null
    val effectiveCaption: String get() = entry.captionOverride ?: photo?.photo?.caption.orEmpty()
}
