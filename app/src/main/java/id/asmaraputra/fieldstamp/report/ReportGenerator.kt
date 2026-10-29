package id.asmaraputra.fieldstamp.report

import android.content.Context
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.core.database.encodeSnapshot
import id.asmaraputra.fieldstamp.core.database.toModel
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.ReportPhotoSnapshot
import id.asmaraputra.fieldstamp.core.model.ReportStatus
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.settings.SettingsRepository
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.core.storage.InsufficientStorageException
import id.asmaraputra.fieldstamp.data.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime

sealed interface GenerationState {
    data object Idle : GenerationState
    data class Running(val done: Int, val total: Int) : GenerationState
    data object Succeeded : GenerationState
    data class Failed(val reason: GenerationFailure) : GenerationState
    data object Cancelled : GenerationState
}

enum class GenerationFailure { MISSING_SOURCES, EMPTY, STORAGE, IMAGE, UNKNOWN }

/**
 * Builds a PDF from a report. Runs in the application scope so navigating away does not abort it,
 * rejects duplicate starts for the same report, and never records a partial file as the result.
 *
 * Snapshot policy: generation freezes each item's caption, time, names, and coordinates into the item row.
 * Regenerating refreshes those snapshots from current photo data (the UI asks for confirmation first).
 */
class ReportGenerator(
    private val context: Context,
    private val reports: ReportRepository,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val files: FileStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val states = HashMap<Long, MutableStateFlow<GenerationState>>()
    private val jobs = HashMap<Long, Job>()

    @Synchronized
    fun state(reportId: Long): StateFlow<GenerationState> =
        states.getOrPut(reportId) { MutableStateFlow(GenerationState.Idle) }.asStateFlow()

    @Synchronized
    fun isRunning(reportId: Long) = jobs[reportId]?.isActive == true

    @Synchronized
    fun start(reportId: Long, allowBranding: Boolean, allowLayouts: Boolean): Boolean {
        if (isRunning(reportId)) return false
        val flow = states.getOrPut(reportId) { MutableStateFlow(GenerationState.Idle) }
        flow.value = GenerationState.Running(0, 0)
        val job = scope.launch {
            flow.value = try {
                generate(reportId, allowBranding, allowLayouts) { done, total -> flow.value = GenerationState.Running(done, total) }
                GenerationState.Succeeded
            } catch (e: CancellationException) {
                GenerationState.Cancelled
            } catch (e: GenerationException) {
                markFailed(reportId, e.failure)
                GenerationState.Failed(e.failure)
            } catch (e: InsufficientStorageException) {
                markFailed(reportId, GenerationFailure.STORAGE)
                GenerationState.Failed(GenerationFailure.STORAGE)
            } catch (e: Exception) {
                markFailed(reportId, GenerationFailure.UNKNOWN)
                GenerationState.Failed(GenerationFailure.UNKNOWN)
            }
        }
        // A job cancelled before its body starts never reaches the catch above; settle the state here.
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException && flow.value is GenerationState.Running) flow.value = GenerationState.Cancelled
        }
        jobs[reportId] = job
        return true
    }

    @Synchronized
    fun cancel(reportId: Long) { jobs[reportId]?.cancel() }

    fun acknowledge(reportId: Long) {
        val flow = synchronized(this) { states[reportId] } ?: return
        if (flow.value !is GenerationState.Running) flow.value = GenerationState.Idle
    }

    private suspend fun markFailed(reportId: Long, failure: GenerationFailure) {
        val report = reports.rawReport(reportId) ?: return
        // A previously generated PDF stays available; only the status reflects the failed attempt.
        reports.saveReport(report.copy(status = ReportStatus.FAILED, lastError = failure.name, updatedAt = clock()))
    }

    private suspend fun generate(reportId: Long, allowBranding: Boolean, allowLayouts: Boolean, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.Default) {
        val reportEntity = reports.rawReport(reportId) ?: throw GenerationException(GenerationFailure.UNKNOWN)
        val report = reportEntity.toModel()
        val rows = reports.rawItems(reportId)
        if (rows.isEmpty()) throw GenerationException(GenerationFailure.EMPTY)

        val appSettings = settings.current()
        val projectNames = projects.observeProjects().first().associate { it.id to it.name }
        val activityNames = projects.observeActivities().first().associate { it.id to it.name }

        // 1. Validate and snapshot every item. Missing sources stop generation; nothing is silently dropped.
        val snapshots = rows.map { row ->
            val photo = row.photo ?: throw GenerationException(GenerationFailure.MISSING_SOURCES)
            val image = if (report.useStampedPhotos && files.exists(photo.renderedPath)) photo.renderedPath!! else photo.originalPath
            if (!files.exists(image)) throw GenerationException(GenerationFailure.MISSING_SOURCES)
            row.entry to ReportPhotoSnapshot(
                caption = row.entry.captionOverride ?: photo.caption,
                capturedAtEpochMillis = photo.capturedAtEpochMillis,
                zoneId = photo.captureTimeZoneId,
                projectName = photo.projectId?.let(projectNames::get),
                activityName = photo.activityId?.let(activityNames::get),
                category = photo.category,
                latitude = photo.latitude,
                longitude = photo.longitude,
                accuracyMeters = photo.locationAccuracyMeters,
                locationName = photo.locationName,
                imagePath = image,
            )
        }

        // 2. Group by report date with deterministic order.
        data class Entry(val id: Long, val day: LocalDate, val order: Int, val snapshot: ReportPhotoSnapshot)
        val entries = snapshots.map { (row, snap) -> Entry(row.id, LocalDate.ofEpochDay(row.reportEpochDay), row.displayOrder, snap) }
        val days = ReportGrouping.groupByDay(entries, { it.day }, { it.order }, { it.snapshot.capturedAtEpochMillis }, { it.id })
        val ordered = days.flatMap { it.items }

        val imageFiles = ordered.map { files.resolve(it.snapshot.imagePath) }
        val aspect = imageFiles.map { file ->
            runCatching { id.asmaraputra.fieldstamp.core.image.BitmapIO.orientedSize(file) }
                .map { it.width.toFloat() / it.height }
                .getOrElse { throw GenerationException(GenerationFailure.IMAGE) }
        }

        // 3. Compose the document.
        val layout = if (report.layout.requiresPro && !allowLayouts) id.asmaraputra.fieldstamp.core.model.ReportLayout.STANDARD else report.layout
        val branding = report.useBranding && allowBranding
        val logo = appSettings.logoPath?.takeIf { branding && files.exists(it) }?.let { files.resolve(it) }
        var index = 0
        val docDays = days.map { day ->
            DocDay(
                heading = Formats.dayHeading(day.date),
                countLabel = context.resources.getQuantityString(R.plurals.photo_count, day.items.size, day.items.size),
                entries = day.items.map { entry ->
                    val i = index++
                    docEntry(i, entry.snapshot, aspect[i], report.includeGps, report.projectId == null, report.activityId == null)
                },
            )
        }
        val generatedAt = ZonedDateTime.now()
        val scopeLine = listOfNotNull(
            report.projectId?.let(projectNames::get)?.let { context.getString(R.string.pdf_scope_project, it) },
            report.activityId?.let(activityNames::get)?.let { context.getString(R.string.pdf_scope_activity, it) },
        ).joinToString(" · ").ifBlank { null }

        val summary = buildList {
            add(DocSummaryRow(context.getString(R.string.pdf_summary_photos), ordered.size.toString()))
            add(DocSummaryRow(context.getString(R.string.pdf_summary_days), days.size.toString()))
            val byActivity = ordered.groupingBy { it.snapshot.activityName }.eachCount()
            if (byActivity.keys.any { it != null } && byActivity.size > 1) {
                val text = byActivity.entries.sortedByDescending { it.value }.joinToString("\n") { (name, count) ->
                    "${name ?: context.getString(R.string.pdf_summary_no_activity)}: $count"
                }
                add(DocSummaryRow(context.getString(R.string.pdf_summary_by_activity), text))
            }
        }

        val doc = ReportDocument(
            title = report.title,
            periodLine = context.getString(R.string.pdf_period, Formats.period(report.periodStart, report.periodEnd)),
            scopeLine = scopeLine,
            generatedLine = context.getString(R.string.pdf_generated, "${Formats.shortDate(generatedAt.toLocalDate())} ${Formats.time(generatedAt)}"),
            organizationName = appSettings.organizationName.takeIf { branding && it.isNotBlank() },
            hasLogo = logo != null,
            summaryHeading = context.getString(R.string.pdf_summary_heading),
            summary = summary,
            notesHeading = context.getString(R.string.pdf_notes_heading),
            notes = report.notes,
            days = docDays,
            columns = layout.columns,
            continuedSuffix = context.getString(R.string.pdf_continued),
            closingHeading = context.getString(R.string.pdf_closing_heading),
            closingNotes = report.closingNotes,
            preparedByLabel = context.getString(R.string.pdf_prepared_by),
            preparedBy = report.preparedBy,
            approvalLabel = if (report.includeSignatureBlock) context.getString(R.string.pdf_approved_by) else null,
            disclaimer = context.getString(R.string.pdf_disclaimer),
            emptyMessage = context.getString(R.string.pdf_empty),
        )

        val renderer = PdfReportRenderer(context)
        val result = ReportLayoutEngine(renderer).layout(doc)

        // 4. Render to a temp file and promote it only after the whole document is written.
        val relative = files.newRelativePath(FileStore.Area.REPORT, "pdf")
        val footerTitle = report.title.take(60)
        val temp = files.newTempFile("pdf")
        try {
            files.ensureSpace(files.dir(FileStore.Area.REPORT))
            withContext(Dispatchers.IO) {
                temp.outputStream().buffered().use { out ->
                    renderer.render(result.pages, imageFiles, logo, { page, total -> context.getString(R.string.pdf_footer, footerTitle, page, total) }, out, onProgress)
                }
            }
            if (!isValidPdf(temp)) throw GenerationException(GenerationFailure.UNKNOWN)
            files.moveInto(temp, relative)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }

        // 5. Persist snapshots and the new file, then remove the superseded PDF.
        val updatedItems = snapshots.map { (row, snap) -> row.copy(metadataSnapshot = encodeSnapshot(snap)) }
        try {
            reports.saveGenerated(
                reportEntity.copy(
                    status = ReportStatus.GENERATED,
                    pdfPath = relative,
                    generatedAt = clock(),
                    pdfOutdated = false,
                    lastError = null,
                    updatedAt = clock(),
                ),
                updatedItems,
            )
        } catch (t: Throwable) {
            files.delete(relative)
            throw t
        }
        if (reportEntity.pdfPath != null && reportEntity.pdfPath != relative) files.delete(reportEntity.pdfPath)
    }

    private fun docEntry(index: Int, s: ReportPhotoSnapshot, aspect: Float, includeGps: Boolean, showProject: Boolean, showActivity: Boolean): DocEntry {
        val time = CaptureTime(s.capturedAtEpochMillis, s.zoneId)
        val timeLine = buildString {
            append(Formats.time(time.zoned))
            if (showActivity) s.activityName?.let { append(" · ").append(it) }
        }
        val meta = buildList {
            if (showProject) s.projectName?.let { add(context.getString(R.string.pdf_meta_project, it)) }
            s.category?.let { add(context.getString(R.string.pdf_meta_category, it)) }
            if (includeGps) s.locationName?.let { add(it) }
            if (includeGps && s.latitude != null && s.longitude != null) {
                add(Formats.coordinatesWithAccuracy(GeoPoint(s.latitude, s.longitude, s.accuracyMeters, s.capturedAtEpochMillis)))
            }
        }
        return DocEntry(index, aspect, timeLine, s.caption.trim(), meta)
    }

    companion object {
        fun isValidPdf(file: File): Boolean {
            if (!file.isFile || file.length() < 64) return false
            val head = ByteArray(5)
            file.inputStream().use { it.read(head) }
            val tail = file.readTailString(1024)
            return String(head) == "%PDF-" && tail.contains("%%EOF")
        }

        private fun File.readTailString(bytes: Int): String {
            val len = length()
            java.io.RandomAccessFile(this, "r").use { raf ->
                val start = (len - bytes).coerceAtLeast(0)
                raf.seek(start)
                val buf = ByteArray((len - start).toInt())
                raf.readFully(buf)
                return String(buf, Charsets.ISO_8859_1)
            }
        }
    }
}

class GenerationException(val failure: GenerationFailure) : Exception(failure.name)
