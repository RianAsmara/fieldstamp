package id.asmaraputra.fieldstamp.feature.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.UiText
import id.asmaraputra.fieldstamp.app.text
import id.asmaraputra.fieldstamp.core.billing.Entitlement
import id.asmaraputra.fieldstamp.core.billing.ProFeature
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.Report
import id.asmaraputra.fieldstamp.core.model.ReportItem
import id.asmaraputra.fieldstamp.report.DayGroup
import id.asmaraputra.fieldstamp.report.GenerationState
import id.asmaraputra.fieldstamp.report.ItemIssue
import id.asmaraputra.fieldstamp.report.ReportGrouping
import id.asmaraputra.fieldstamp.report.ReportSettings
import id.asmaraputra.fieldstamp.report.ZipItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BuilderItem(val item: ReportItem, val issues: Set<ItemIssue>)

data class BuilderDay(val date: LocalDate, val included: List<BuilderItem>, val excluded: List<PhotoListItem>)

data class BuilderState(
    val loaded: Boolean = false,
    val report: Report? = null,
    val days: List<BuilderDay> = emptyList(),
    val includedCount: Int = 0,
    val documentedDays: Int = 0,
    val missingCount: Int = 0,
    val noCaptionCount: Int = 0,
    val noGpsCount: Int = 0,
    val pdfExists: Boolean = false,
    val entitlement: Entitlement = Entitlement.FREE,
    val hasBrandingConfigured: Boolean = false,
)

class ReportBuilderViewModel(private val c: AppContainer, val reportId: Long) : ViewModel() {
    private val reportFlow = c.reports.observe(reportId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val candidates = reportFlow.flatMapLatest { r -> if (r == null) flowOf(emptyList()) else c.reports.observeCandidates(r) }

    val state: StateFlow<BuilderState> = combine(
        reportFlow,
        c.reports.observeItems(reportId),
        candidates,
        c.billing.entitlement,
        c.settings.settings,
    ) { report, items, pool, entitlement, settings ->
        if (report == null) return@combine BuilderState(loaded = true)
        val built = items.map { item ->
            BuilderItem(item, ReportGrouping.issues(item, c.files::exists) { imagePath(report, it) })
        }
        val grouped: List<DayGroup<BuilderItem>> = ReportGrouping.groupByDay(
            built,
            date = { it.item.entry.reportDate },
            displayOrder = { it.item.entry.displayOrder },
            capturedAt = { it.item.photo?.photo?.captureTime?.epochMillis ?: Long.MAX_VALUE },
            id = { it.item.entry.id },
        )
        val includedIds = items.mapNotNull { it.entry.photoRecordId }.toSet()
        val excludedByDay = pool.filter { it.photo.id !in includedIds }.groupBy { it.photo.captureLocalDate }
        val allDates = (grouped.map { it.date } + excludedByDay.keys).distinct().sorted()
        BuilderState(
            loaded = true,
            report = report,
            days = allDates.map { d ->
                BuilderDay(
                    date = d,
                    included = grouped.firstOrNull { it.date == d }?.items.orEmpty(),
                    excluded = excludedByDay[d].orEmpty().sortedBy { it.photo.captureTime.epochMillis },
                )
            },
            includedCount = items.size,
            documentedDays = grouped.size,
            missingCount = built.count { b -> b.issues.any(ReportGrouping::isBlocking) },
            noCaptionCount = built.count { ItemIssue.NO_CAPTION in it.issues },
            noGpsCount = built.count { ItemIssue.NO_GPS in it.issues },
            pdfExists = c.files.exists(report.pdfPath),
            entitlement = entitlement,
            hasBrandingConfigured = settings.organizationName.isNotBlank() || settings.logoPath != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuilderState())

    val generation: StateFlow<GenerationState> = c.reportGenerator.state(reportId)

    private fun imagePath(report: Report, item: ReportItem): String? {
        val photo = item.photo?.photo ?: return null
        return if (report.useStampedPhotos && c.files.exists(photo.renderedPath)) photo.renderedPath else photo.originalPath
    }

    // --- Settings draft, saved automatically --------------------------------------------------------

    private val _draft = MutableStateFlow<ReportSettings?>(null)
    val draft: StateFlow<ReportSettings?> = _draft.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val r = reportFlow.filterNotNull().first()
            _draft.value = ReportSettings(r.title, r.notes, r.closingNotes, r.preparedBy, r.includeGps, r.includeSignatureBlock, r.layout, r.useBranding, r.useStampedPhotos)
        }
    }

    @OptIn(FlowPreview::class)
    fun editSettings(block: (ReportSettings) -> ReportSettings) {
        val next = _draft.value?.let(block) ?: return
        _draft.value = next
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            c.reports.updateSettings(reportId, next)
        }
    }

    fun flushSettings() {
        val pending = _draft.value ?: return
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            viewModelScope.launch { c.reports.updateSettings(reportId, pending) }
        }
    }

    // --- Selection ----------------------------------------------------------------------------------

    fun include(photo: PhotoListItem) = viewModelScope.launch { c.reports.include(reportId, photo) }
    fun includeAll(photos: List<PhotoListItem>) = viewModelScope.launch { photos.forEach { c.reports.include(reportId, it) } }
    fun exclude(itemId: Long) = viewModelScope.launch { c.reports.exclude(reportId, listOf(itemId)) }
    fun move(itemId: Long, direction: Int) = viewModelScope.launch { c.reports.move(reportId, itemId, direction) }
    fun setCaption(itemId: Long, caption: String?) = viewModelScope.launch { c.reports.setCaption(reportId, itemId, caption) }
    fun setReportDate(itemId: Long, date: LocalDate) = viewModelScope.launch { c.reports.setReportDate(reportId, itemId, date) }
    fun removeMissing() = viewModelScope.launch { c.reports.removeMissing(reportId) }

    // --- Generation ---------------------------------------------------------------------------------

    fun generate() {
        flushSettings()
        val ent = state.value.entitlement
        viewModelScope.launch {
            // Make sure the latest settings are persisted before the generator reads them.
            _draft.value?.let { c.reports.updateSettings(reportId, it) }
            c.reportGenerator.start(reportId, allowBranding = ent.allows(ProFeature.REPORT_BRANDING), allowLayouts = ent.allows(ProFeature.REPORT_LAYOUTS))
        }
    }

    fun cancelGeneration() = c.reportGenerator.cancel(reportId)
    fun acknowledgeGeneration() = c.reportGenerator.acknowledge(reportId)

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        c.reportGenerator.cancel(reportId)
        c.reports.delete(reportId)
        onDone()
    }

    fun pdfFileName(): String {
        val r = state.value.report ?: return "laporan.pdf"
        val base = r.title.replace(Regex("[^\\p{L}\\p{N}._ -]+"), "").trim().replace(' ', '_').take(60).ifBlank { "laporan" }
        return "$base.pdf"
    }

    fun saveAs(uri: Uri, onResult: (UiText) -> Unit) = viewModelScope.launch {
        val path = state.value.report?.pdfPath ?: return@launch
        runCatching { c.export.copyToDocument(path, uri) }
            .onSuccess { onResult(text(R.string.export_saved_file)) }
            .onFailure { onResult(text(R.string.export_failed)) }
    }

    private val _zipBusy = MutableStateFlow(false)
    val zipBusy: StateFlow<Boolean> = _zipBusy.asStateFlow()

    fun exportZip(onReady: (String) -> Unit, onError: (UiText) -> Unit) {
        if (_zipBusy.value) return
        _zipBusy.value = true
        viewModelScope.launch {
            val s = state.value
            val report = s.report
            val items = s.days.flatMap { it.included }.mapNotNull { b ->
                val photo = b.item.photo ?: return@mapNotNull null
                val path = report?.let { imagePath(it, b.item) } ?: return@mapNotNull null
                ZipItem(photo, b.item.entry.reportDate, b.item.effectiveCaption, path)
            }
            runCatching { c.zipExporter.export(pdfFileName().removeSuffix(".pdf"), items) }
                .onSuccess(onReady)
                .onFailure { onError(text(R.string.export_failed)) }
            _zipBusy.value = false
        }
    }
}
