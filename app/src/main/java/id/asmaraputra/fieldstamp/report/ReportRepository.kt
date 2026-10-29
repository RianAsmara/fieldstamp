package id.asmaraputra.fieldstamp.report

import androidx.room.withTransaction
import id.asmaraputra.fieldstamp.core.database.FieldStampDatabase
import id.asmaraputra.fieldstamp.core.database.ReportEntity
import id.asmaraputra.fieldstamp.core.database.ReportPhotoEntity
import id.asmaraputra.fieldstamp.core.database.toModel
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.Report
import id.asmaraputra.fieldstamp.core.model.ReportItem
import id.asmaraputra.fieldstamp.core.model.ReportLayout
import id.asmaraputra.fieldstamp.core.model.ReportStatus
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.data.PhotoFilter
import id.asmaraputra.fieldstamp.data.PhotoRepository
import id.asmaraputra.fieldstamp.data.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class ReportSummary(val report: Report, val photoCount: Int)

/** Editable report settings, applied together. */
data class ReportSettings(
    val title: String,
    val notes: String,
    val closingNotes: String,
    val preparedBy: String,
    val includeGps: Boolean,
    val includeSignatureBlock: Boolean,
    val layout: ReportLayout,
    val useBranding: Boolean,
    val useStampedPhotos: Boolean,
)

/**
 * Report persistence. Items reference photos; image binaries are never duplicated.
 * Any change to a report that already has a PDF marks that PDF as outdated; the PDF itself is left
 * untouched until the user explicitly regenerates it.
 */
class ReportRepository(
    private val db: FieldStampDatabase,
    private val photos: PhotoRepository,
    private val projects: ProjectRepository,
    private val files: FileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.reportDao()

    fun observeSummaries(): Flow<List<ReportSummary>> =
        dao.observeWithCounts().map { rows -> rows.map { ReportSummary(it.report.toModel(), it.photoCount) } }

    fun observe(id: Long): Flow<Report?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: Long): Report? = dao.get(id)?.toModel()

    fun observeItems(reportId: Long): Flow<List<ReportItem>> = combine(
        dao.observeItems(reportId),
        projects.observeProjects(),
        projects.observeActivities(),
    ) { rows, projectList, activityList ->
        val projectNames = projectList.associate { it.id to it.name }
        val activityNames = activityList.associate { it.id to it.name }
        rows.map { row ->
            val photo = row.photo?.toModel()?.let { PhotoListItem(it, it.projectId?.let(projectNames::get), it.activityId?.let(activityNames::get)) }
            ReportItem(row.entry.toModel(), photo)
        }
    }

    /** Photos that match the report's period and filters (the pool the user selects from). */
    fun observeCandidates(report: Report): Flow<List<PhotoListItem>> = photos.observe(
        PhotoFilter(start = report.periodStart, end = report.periodEnd, projectId = report.projectId, activityId = report.activityId),
    )

    /**
     * Creates a draft and pre-selects every matching photo in chronological order. The user then
     * reviews and excludes what does not belong.
     */
    suspend fun create(
        title: String,
        period: ReportPeriod,
        projectId: Long?,
        activityId: Long?,
        preparedBy: String,
        useBranding: Boolean,
    ): Long = db.withTransaction {
        val now = clock()
        val id = dao.insert(
            ReportEntity(
                title = title.trim(),
                projectId = projectId,
                activityId = activityId,
                periodStartEpochDay = period.start.toEpochDay(),
                periodEndEpochDay = period.end.toEpochDay(),
                status = ReportStatus.DRAFT,
                preparedBy = preparedBy,
                useBranding = useBranding,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val matches = db.photoDao().inRange(period.start.toEpochDay(), period.end.toEpochDay(), projectId, activityId)
        val order = ReportGrouping.chronologicalOrder(matches, { LocalDate.ofEpochDay(it.captureEpochDay) }, { it.capturedAtEpochMillis }, { it.id })
        dao.insertItems(
            matches.map {
                ReportPhotoEntity(
                    reportId = id,
                    photoRecordId = it.id,
                    reportEpochDay = it.captureEpochDay,
                    displayOrder = order.getValue(it.id),
                    captionOverride = null,
                    metadataSnapshot = null,
                )
            },
        )
        id
    }

    suspend fun include(reportId: Long, photo: PhotoListItem) = mutate(reportId) {
        val day = photo.photo.captureLocalDate
        val existing = dao.items(reportId)
        if (existing.any { it.entry.photoRecordId == photo.photo.id }) return@mutate
        val nextOrder = (existing.filter { it.entry.reportEpochDay == day.toEpochDay() }.maxOfOrNull { it.entry.displayOrder } ?: -1) + 1
        dao.insertItems(listOf(ReportPhotoEntity(reportId = reportId, photoRecordId = photo.photo.id, reportEpochDay = day.toEpochDay(), displayOrder = nextOrder, captionOverride = null, metadataSnapshot = null)))
    }

    /** Removes the item from the report only. The photo record and files are not touched. */
    suspend fun exclude(reportId: Long, itemIds: List<Long>) = mutate(reportId) { dao.deleteItems(itemIds) }

    suspend fun removeMissing(reportId: Long) = mutate(reportId) { dao.deleteMissingItems(reportId) }

    /** Swaps the item with its neighbour in the same report day. */
    suspend fun move(reportId: Long, itemId: Long, direction: Int) = mutate(reportId) {
        val items = dao.items(reportId).map { it.entry }
        val item = items.firstOrNull { it.id == itemId } ?: return@mutate
        val day = items.filter { it.reportEpochDay == item.reportEpochDay }.sortedWith(compareBy({ it.displayOrder }, { it.id }))
        val index = day.indexOfFirst { it.id == itemId }
        val target = index + direction
        if (target !in day.indices) return@mutate
        val reordered = day.toMutableList().apply { add(target, removeAt(index)) }
        dao.updateItems(reordered.mapIndexed { i, e -> e.copy(displayOrder = i) })
    }

    /** Report-only caption; the photo's own caption is unchanged. Null restores the photo's caption. */
    suspend fun setCaption(reportId: Long, itemId: Long, caption: String?) = mutate(reportId) {
        val item = dao.getItem(itemId) ?: return@mutate
        dao.updateItems(listOf(item.copy(captionOverride = caption?.trim())))
    }

    /**
     * Assigns an item to another day of the report (e.g. work that ran past midnight). The photo's
     * capture timestamp is not modified; both values are kept.
     */
    suspend fun setReportDate(reportId: Long, itemId: Long, date: LocalDate) = mutate(reportId) {
        val report = dao.get(reportId) ?: return@mutate
        require(date.toEpochDay() in report.periodStartEpochDay..report.periodEndEpochDay) { "Date outside report period" }
        val item = dao.getItem(itemId) ?: return@mutate
        if (item.reportEpochDay == date.toEpochDay()) return@mutate
        val nextOrder = (dao.items(reportId).filter { it.entry.reportEpochDay == date.toEpochDay() }.maxOfOrNull { it.entry.displayOrder } ?: -1) + 1
        dao.updateItems(listOf(item.copy(reportEpochDay = date.toEpochDay(), displayOrder = nextOrder)))
    }

    suspend fun updateSettings(reportId: Long, settings: ReportSettings) = mutate(reportId) {
        val report = dao.get(reportId) ?: return@mutate
        dao.update(
            report.copy(
                title = settings.title.trim(),
                notes = settings.notes.trim(),
                closingNotes = settings.closingNotes.trim(),
                preparedBy = settings.preparedBy.trim(),
                includeGps = settings.includeGps,
                includeSignatureBlock = settings.includeSignatureBlock,
                layout = settings.layout,
                useBranding = settings.useBranding,
                useStampedPhotos = settings.useStampedPhotos,
            ),
        )
    }

    suspend fun delete(reportId: Long) {
        val report = dao.get(reportId) ?: return
        dao.delete(reportId)
        files.delete(report.pdfPath)
    }

    internal suspend fun rawItems(reportId: Long) = dao.items(reportId)

    internal suspend fun rawReport(reportId: Long) = dao.get(reportId)

    internal suspend fun saveGenerated(report: ReportEntity, items: List<ReportPhotoEntity>) = db.withTransaction {
        dao.updateItems(items)
        dao.update(report)
    }

    internal suspend fun saveReport(report: ReportEntity) = dao.update(report)

    private suspend fun mutate(reportId: Long, block: suspend () -> Unit) = db.withTransaction {
        block()
        val report = dao.get(reportId) ?: return@withTransaction
        dao.update(report.copy(updatedAt = clock(), pdfOutdated = report.pdfPath != null))
    }
}
