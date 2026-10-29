package id.asmaraputra.fieldstamp.core.database

import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.PhotoRecord
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.Report
import id.asmaraputra.fieldstamp.core.model.ReportPhoto
import id.asmaraputra.fieldstamp.core.model.ReportPhotoSnapshot
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val snapshotJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

fun ProjectEntity.toModel() = Project(id, name, description, createdAt, updatedAt, archivedAt)

fun ActivityEntity.toModel() = FieldActivity(id, projectId, name, description, createdAt, updatedAt)

fun PhotoEntity.toModel(): PhotoRecord {
    val location = if (latitude != null && longitude != null) {
        GeoPoint(latitude, longitude, locationAccuracyMeters, locationCapturedAtEpochMillis ?: capturedAtEpochMillis)
    } else {
        null
    }
    return PhotoRecord(
        id = id,
        projectId = projectId,
        activityId = activityId,
        source = source,
        originalPath = originalPath,
        renderedPath = renderedPath,
        thumbnailPath = thumbnailPath,
        captureTime = CaptureTime(capturedAtEpochMillis, captureTimeZoneId),
        captureLocalDate = LocalDate.ofEpochDay(captureEpochDay),
        location = location,
        locationName = locationName,
        caption = caption,
        category = category,
        templateId = templateId,
        widthPx = widthPx,
        heightPx = heightPx,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun TemplateEntity.toModel() = StampTemplate(
    id = id,
    name = name,
    sourcePath = sourcePath,
    widthPx = widthPx,
    heightPx = heightPx,
    config = TemplateConfig.fromJson(configurationJson),
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ReportEntity.toModel() = Report(
    id = id,
    title = title,
    projectId = projectId,
    activityId = activityId,
    periodStart = LocalDate.ofEpochDay(periodStartEpochDay),
    periodEnd = LocalDate.ofEpochDay(periodEndEpochDay),
    status = status,
    notes = notes,
    closingNotes = closingNotes,
    preparedBy = preparedBy,
    includeGps = includeGps,
    includeSignatureBlock = includeSignatureBlock,
    layout = layout,
    useBranding = useBranding,
    useStampedPhotos = useStampedPhotos,
    pdfPath = pdfPath,
    generatedAt = generatedAt,
    pdfOutdated = pdfOutdated,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ReportPhotoEntity.toModel() = ReportPhoto(
    id = id,
    reportId = reportId,
    photoRecordId = photoRecordId,
    reportDate = LocalDate.ofEpochDay(reportEpochDay),
    displayOrder = displayOrder,
    captionOverride = captionOverride,
    snapshot = decodeSnapshot(metadataSnapshot),
)

fun encodeSnapshot(snapshot: ReportPhotoSnapshot): String =
    snapshotJson.encodeToString(ReportPhotoSnapshot.serializer(), snapshot)

fun decodeSnapshot(value: String?): ReportPhotoSnapshot? =
    value?.let { runCatching { snapshotJson.decodeFromString(ReportPhotoSnapshot.serializer(), it) }.getOrNull() }
