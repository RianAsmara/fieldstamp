package id.asmaraputra.fieldstamp.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import id.asmaraputra.fieldstamp.core.database.PhotoDao
import id.asmaraputra.fieldstamp.core.database.PhotoEntity
import id.asmaraputra.fieldstamp.core.database.ReportDao
import id.asmaraputra.fieldstamp.core.database.toModel
import id.asmaraputra.fieldstamp.core.image.BitmapIO
import id.asmaraputra.fieldstamp.core.image.StampData
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.PhotoRecord
import id.asmaraputra.fieldstamp.core.model.PhotoSource
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import id.asmaraputra.fieldstamp.core.settings.SettingsRepository
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.core.storage.MissingFileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PhotoFilter(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val projectId: Long? = null,
    val activityId: Long? = null,
    val query: String = "",
    val missingCaptionOnly: Boolean = false,
    val missingGpsOnly: Boolean = false,
) {
    val isActive: Boolean get() = start != null || projectId != null || activityId != null || query.isNotBlank() || missingCaptionOnly || missingGpsOnly
}

/** Everything known about a capture before it is saved. */
data class CaptureDraft(
    /** Temp file holding the original; moved into app storage on save. */
    val originalTemp: File,
    val source: PhotoSource,
    val captureTime: CaptureTime,
    val location: GeoPoint?,
    val locationName: String?,
    val projectId: Long?,
    val activityId: Long?,
    val caption: String,
    val category: String?,
    val templateId: Long?,
)

/** Template to apply: a PNG overlay path (or none) plus the field configuration. */
data class ResolvedStamp(val templateId: Long?, val templatePath: String?, val config: TemplateConfig)

class PhotoRepository(
    private val context: Context,
    private val photos: PhotoDao,
    private val reports: ReportDao,
    private val projects: ProjectRepository,
    private val templates: TemplateRepository,
    private val settings: SettingsRepository,
    private val processor: PhotoProcessor,
    private val files: FileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(filter: PhotoFilter): Flow<List<PhotoListItem>> = combine(
        photos.observeFiltered(
            startDay = filter.start?.toEpochDay(),
            endDay = filter.end?.toEpochDay(),
            projectId = filter.projectId,
            activityId = filter.activityId,
            query = filter.query.trim(),
            missingCaptionOnly = filter.missingCaptionOnly,
            missingGpsOnly = filter.missingGpsOnly,
        ),
        projects.observeProjects(),
        projects.observeActivities(),
    ) { rows, projectList, activityList -> withNames(rows.map { it.toModel() }, projectList, activityList) }

    fun observeDay(date: LocalDate): Flow<List<PhotoListItem>> = combine(
        photos.observeDay(date.toEpochDay()),
        projects.observeProjects(),
        projects.observeActivities(),
    ) { rows, projectList, activityList -> withNames(rows.map { it.toModel() }, projectList, activityList) }

    fun observeCount(): Flow<Int> = photos.observeCount()

    fun observeItem(id: Long): Flow<PhotoListItem?> = combine(
        photos.observe(id),
        projects.observeProjects(),
        projects.observeActivities(),
    ) { row, projectList, activityList -> row?.let { withNames(listOf(it.toModel()), projectList, activityList).first() } }

    suspend fun get(id: Long): PhotoRecord? = photos.get(id)?.toModel()

    fun fileExists(relativePath: String?): Boolean = files.exists(relativePath)

    fun resolveFile(relativePath: String): File = files.resolve(relativePath)

    suspend fun resolveStamp(templateId: Long?): ResolvedStamp {
        val template = templateId?.let { templates.get(it) }
        return if (template != null && templates.isSourceAvailable(template)) {
            ResolvedStamp(template.id, template.sourcePath, template.config)
        } else {
            ResolvedStamp(null, null, settings.current().basicStampConfig)
        }
    }

    suspend fun stampData(
        captureTime: CaptureTime,
        projectId: Long?,
        activityId: Long?,
        caption: String,
        category: String?,
        location: GeoPoint?,
        locationName: String? = null,
    ): StampData {
        val projectName = projectId?.let { id -> projects.observeProjects().first().firstOrNull { it.id == id }?.name }
        val activityName = activityId?.let { id -> projects.observeActivities().first().firstOrNull { it.id == id }?.name }
        return StampData(captureTime, projectName, activityName, caption, category, location, locationName?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** Saves a capture: moves the original into place, renders the stamped copy, then inserts the record. */
    suspend fun save(draft: CaptureDraft): Long = withContext(Dispatchers.IO) {
        if (!draft.originalTemp.isFile) throw MissingFileException()
        files.ensureSpace(files.dir(FileStore.Area.ORIGINAL))
        val originalPath = files.newRelativePath(FileStore.Area.ORIGINAL, "jpg")
        files.moveInto(draft.originalTemp, originalPath)
        try {
            val stamp = resolveStamp(draft.templateId)
            val data = stampData(draft.captureTime, draft.projectId, draft.activityId, draft.caption, draft.category, draft.location, draft.locationName)
            val rendered = processor.render(originalPath, stamp.templatePath, stamp.config, data, settings.current().embedGpsInExif)
            val now = clock()
            photos.insert(
                PhotoEntity(
                    projectId = draft.projectId,
                    activityId = draft.activityId,
                    source = draft.source,
                    originalPath = originalPath,
                    renderedPath = rendered.renderedPath,
                    thumbnailPath = rendered.thumbnailPath,
                    capturedAtEpochMillis = draft.captureTime.epochMillis,
                    captureTimeZoneId = draft.captureTime.zoneId,
                    captureEpochDay = draft.captureTime.localDate.toEpochDay(),
                    latitude = draft.location?.latitude,
                    longitude = draft.location?.longitude,
                    locationAccuracyMeters = draft.location?.accuracyMeters,
                    locationCapturedAtEpochMillis = draft.location?.capturedAtEpochMillis,
                    locationName = draft.locationName?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_PLACE),
                    caption = draft.caption.trim(),
                    category = draft.category?.trim()?.takeIf { it.isNotEmpty() },
                    templateId = stamp.templateId,
                    widthPx = rendered.widthPx,
                    heightPx = rendered.heightPx,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } catch (t: Throwable) {
            // Nothing is recorded as saved unless every step succeeded.
            files.delete(originalPath)
            throw t
        }
    }

    /** Updates metadata and re-renders the stamped copy from the untouched original. */
    suspend fun update(
        id: Long,
        caption: String,
        category: String?,
        projectId: Long?,
        activityId: Long?,
        templateId: Long?,
        locationName: String? = null,
    ) = withContext(Dispatchers.IO) {
        val existing = photos.get(id) ?: return@withContext
        val record = existing.toModel()
        val stamp = resolveStamp(templateId)
        val place = locationName?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_PLACE)
        val data = stampData(record.captureTime, projectId, activityId, caption.trim(), category, record.location, place)
        val rendered = processor.render(existing.originalPath, stamp.templatePath, stamp.config, data, settings.current().embedGpsInExif)
        val now = clock()
        photos.update(
            existing.copy(
                caption = caption.trim(),
                category = category?.trim()?.takeIf { it.isNotEmpty() },
                projectId = projectId,
                activityId = activityId,
                templateId = stamp.templateId,
                locationName = place,
                renderedPath = rendered.renderedPath,
                thumbnailPath = rendered.thumbnailPath,
                widthPx = rendered.widthPx,
                heightPx = rendered.heightPx,
                updatedAt = now,
            ),
        )
        files.delete(existing.renderedPath)
        files.delete(existing.thumbnailPath)
        reports.markOutdatedForPhoto(id, now)
    }

    suspend fun reportUsageCount(id: Long): Int = photos.reportUsageCount(id)

    /** Deletes the record and its files. Reports that used it keep a visible "missing photo" entry. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val existing = photos.get(id) ?: return@withContext
        reports.markOutdatedForPhoto(id, clock())
        photos.delete(id)
        files.delete(existing.originalPath)
        files.delete(existing.renderedPath)
        files.delete(existing.thumbnailPath)
    }

    /**
     * Copies a picked image into a temp file and reads its own EXIF capture time and GPS, if present.
     * When the file has no capture time, the import time is used and flagged so the UI can say so.
     */
    suspend fun stageImport(uri: Uri): ImportedImage = withContext(Dispatchers.IO) {
        val temp = files.newTempFile("jpg")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Unreadable")
            input.use { src -> temp.outputStream().use { src.copyTo(it) } }
            BitmapIO.rawSize(temp)
            val exif = runCatching { ExifInterface(temp) }.getOrNull()
            val time = exif?.let { readCaptureTime(it) }
            val location = exif?.latLong?.let { (lat, lon) ->
                GeoPoint(lat, lon, null, time?.epochMillis ?: clock())
            }
            ImportedImage(temp, time ?: CaptureTime.now(), location, timeFromFile = time != null)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun readCaptureTime(exif: ExifInterface): CaptureTime? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        val local = runCatching { LocalDateTime.parse(raw, EXIF_FORMAT) }.getOrNull() ?: return null
        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
        // Without an offset tag, EXIF time is local wall-clock time; assume the device's current zone.
        val zone: ZoneId = ZoneId.systemDefault()
        val instant = if (offset != null) local.toInstant(offset) else local.atZone(zone).toInstant()
        val zoneForRecord = if (offset != null && zone.rules.getOffset(instant) != offset) offset else zone
        return CaptureTime(instant.toEpochMilli(), zoneForRecord.id)
    }

    /** Removes files in app storage that no record references (e.g. after a crash mid-save). */
    suspend fun pruneOrphanFiles(): Int = withContext(Dispatchers.IO) {
        val referenced = photos.allReferencedPaths().toSet()
        var removed = 0
        listOf(FileStore.Area.ORIGINAL, FileStore.Area.RENDERED, FileStore.Area.THUMB).forEach { area ->
            files.dir(area).listFiles()?.forEach { file ->
                val rel = files.relativize(file)
                if (rel !in referenced && clock() - file.lastModified() > ORPHAN_AGE_MILLIS) {
                    if (file.delete()) removed++
                }
            }
        }
        removed
    }

    private fun withNames(rows: List<PhotoRecord>, projectList: List<Project>, activityList: List<FieldActivity>): List<PhotoListItem> {
        val projectNames = projectList.associate { it.id to it.name }
        val activityNames = activityList.associate { it.id to it.name }
        return rows.map { PhotoListItem(it, it.projectId?.let(projectNames::get), it.activityId?.let(activityNames::get)) }
    }

    companion object {
        private val EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
        private const val ORPHAN_AGE_MILLIS = 60 * 60 * 1000L
        const val MAX_PLACE = 160
    }
}

data class ImportedImage(
    val file: File,
    val captureTime: CaptureTime,
    val location: GeoPoint?,
    /** False when the image had no EXIF capture time and the import time was used instead. */
    val timeFromFile: Boolean,
)
