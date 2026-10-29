package id.asmaraputra.fieldstamp.feature.reports

import android.content.ActivityNotFoundException
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.PaywallRoute
import id.asmaraputra.fieldstamp.app.UiText
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.app.container
import id.asmaraputra.fieldstamp.app.text
import id.asmaraputra.fieldstamp.core.billing.ProFeature
import id.asmaraputra.fieldstamp.core.designsystem.ConfirmDialog
import id.asmaraputra.fieldstamp.core.designsystem.EmptyState
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.LoadingState
import id.asmaraputra.fieldstamp.core.designsystem.Notice
import id.asmaraputra.fieldstamp.core.designsystem.NoticeTone
import id.asmaraputra.fieldstamp.core.designsystem.PhotoThumbnail
import id.asmaraputra.fieldstamp.core.designsystem.ProBadge
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.designsystem.SwitchRow
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.Report
import id.asmaraputra.fieldstamp.core.model.ReportLayout
import id.asmaraputra.fieldstamp.core.model.ReportStatus
import id.asmaraputra.fieldstamp.core.storage.PdfPrintAdapter
import id.asmaraputra.fieldstamp.feature.common.appFile
import id.asmaraputra.fieldstamp.report.GenerationFailure
import id.asmaraputra.fieldstamp.report.GenerationState
import id.asmaraputra.fieldstamp.report.ItemIssue
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val LARGE_REPORT = 300

@Composable
fun ReportBuilderScreen(nav: NavController, reportId: Long) {
    val vm = appViewModel(key = "report-$reportId") { c, _ -> ReportBuilderViewModel(c, reportId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val generation by vm.generation.collectAsStateWithLifecycle()
    val zipBusy by vm.zipBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.container
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val show: (UiText) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg.resolve(context)) } }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.flushSettings() }

    LaunchedEffect(generation) {
        when (val g = generation) {
            GenerationState.Succeeded -> { show(text(R.string.report_generated_toast)); vm.acknowledgeGeneration(); tab = 2 }
            GenerationState.Cancelled -> { show(text(R.string.report_generation_cancelled)); vm.acknowledgeGeneration() }
            is GenerationState.Failed -> if (g.reason == GenerationFailure.MISSING_SOURCES) dialog = "missing"
            else -> Unit
        }
    }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveAs(uri, show)
    }

    val report = state.report
    val running = generation is GenerationState.Running

    fun requestGenerate() {
        when {
            state.includedCount == 0 -> show(text(R.string.report_error_empty))
            state.missingCount > 0 -> dialog = "missing"
            state.pdfExists -> dialog = "regenerate"
            state.includedCount > LARGE_REPORT -> dialog = "large"
            else -> vm.generate()
        }
    }

    Scaffold(
        topBar = {
            FsTopBar(report?.title ?: stringResource(R.string.nav_reports), onBack = { vm.flushSettings(); nav.popBackStack() }) {
                if (report != null) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; dialog = "delete" },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (report != null) {
                GenerateBar(
                    report = report,
                    generation = generation,
                    pdfExists = state.pdfExists,
                    onGenerate = ::requestGenerate,
                    onCancel = vm::cancelGeneration,
                )
            }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingState(Modifier.padding(padding))
            report == null -> EmptyState(Icons.Outlined.Description, stringResource(R.string.report_missing_title), stringResource(R.string.report_missing_body), Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                StatusLine(report, state)
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(R.string.report_tab_photos, R.string.report_tab_settings, R.string.report_tab_pdf).forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
                    }
                }
                when (tab) {
                    0 -> PhotosTab(state, vm, onRemoveMissing = { dialog = "missing" })
                    1 -> SettingsTab(vm, state, onPaywall = { nav.navigate(PaywallRoute) })
                    else -> PdfTab(
                        report = report,
                        pdfExists = state.pdfExists,
                        zipAllowed = state.entitlement.allows(ProFeature.ZIP_EXPORT),
                        zipBusy = zipBusy,
                        running = running,
                        onOpen = {
                            val path = report.pdfPath ?: return@PdfTab
                            try { context.startActivity(container.export.viewIntent(path, "application/pdf")) }
                            catch (_: ActivityNotFoundException) { show(text(R.string.error_no_pdf_viewer)) }
                        },
                        onShare = {
                            val path = report.pdfPath ?: return@PdfTab
                            context.startActivity(container.export.shareIntent(listOf(path), "application/pdf", resources.getString(R.string.action_share)))
                        },
                        onSaveAs = { saveAs.launch(vm.pdfFileName()) },
                        onPrint = {
                            val path = report.pdfPath ?: return@PdfTab
                            val pm = context.getSystemService(PrintManager::class.java)
                            pm?.print(report.title, PdfPrintAdapter(container.files.resolve(path), vm.pdfFileName()), null)
                        },
                        onZip = {
                            if (!state.entitlement.allows(ProFeature.ZIP_EXPORT)) nav.navigate(PaywallRoute)
                            else vm.exportZip(
                                onReady = { path -> context.startActivity(container.export.shareIntent(listOf(path), "application/zip", resources.getString(R.string.action_share))) },
                                onError = show,
                            )
                        },
                    )
                }
            }
        }
    }

    when (dialog) {
        "missing" -> ConfirmDialog(
            title = stringResource(R.string.report_missing_items_title),
            body = pluralStringResource(R.plurals.report_missing_items_body, state.missingCount, state.missingCount),
            confirmLabel = stringResource(R.string.report_remove_missing),
            onConfirm = { vm.removeMissing(); vm.acknowledgeGeneration(); dialog = null },
            onDismiss = { vm.acknowledgeGeneration(); dialog = null },
        )
        "regenerate" -> ConfirmDialog(
            title = stringResource(R.string.report_regenerate_title),
            body = stringResource(R.string.report_regenerate_body),
            confirmLabel = stringResource(R.string.report_regenerate),
            onConfirm = {
                dialog = if (state.includedCount > LARGE_REPORT) "large" else null
                if (dialog == null) vm.generate()
            },
            onDismiss = { dialog = null },
        )
        "large" -> ConfirmDialog(
            title = stringResource(R.string.report_large_title),
            body = stringResource(R.string.report_large_body, state.includedCount),
            confirmLabel = stringResource(R.string.report_generate),
            onConfirm = { dialog = null; vm.generate() },
            onDismiss = { dialog = null },
        )
        "delete" -> ConfirmDialog(
            title = stringResource(R.string.report_delete_title),
            body = stringResource(R.string.report_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { dialog = null; vm.delete { nav.popBackStack() } },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun StatusLine(report: Report, state: BuilderState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(report)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                "${Formats.period(report.periodStart, report.periodEnd)} · ${statusLabel(report)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOf(
                    pluralStringResource(R.plurals.photo_count, state.includedCount, state.includedCount),
                    pluralStringResource(R.plurals.day_count, state.documentedDays, state.documentedDays),
                ).joinToString(" · ") + (report.generatedAt?.let { " · " + stringResource(R.string.report_generated_at, generatedLabel(it)) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenerateBar(report: Report, generation: GenerationState, pdfExists: Boolean, onGenerate: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.md)) {
            when (generation) {
                is GenerationState.Running -> {
                    val label = if (generation.total > 0) stringResource(R.string.report_generating_progress, generation.done, generation.total) else stringResource(R.string.report_generating)
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    if (generation.total > 0) LinearProgressIndicator(progress = { generation.done.toFloat() / generation.total }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_cancel)) }
                }
                else -> {
                    if (generation is GenerationState.Failed && generation.reason != GenerationFailure.MISSING_SOURCES) {
                        Text(
                            stringResource(failureMessage(generation.reason)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = Spacing.xs),
                        )
                    } else if (report.status == ReportStatus.FAILED) {
                        Text(stringResource(R.string.report_last_attempt_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = Spacing.xs))
                    }
                    Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth().height(Spacing.touch)) {
                        Text(stringResource(if (pdfExists) R.string.report_regenerate else R.string.report_generate))
                    }
                }
            }
        }
    }
}

private fun failureMessage(reason: GenerationFailure): Int = when (reason) {
    GenerationFailure.MISSING_SOURCES -> R.string.report_error_missing
    GenerationFailure.EMPTY -> R.string.report_error_empty
    GenerationFailure.STORAGE -> R.string.error_storage_full
    GenerationFailure.IMAGE -> R.string.report_error_image
    GenerationFailure.UNKNOWN -> R.string.report_error_unknown
}

// --- Photos tab -------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotosTab(state: BuilderState, vm: ReportBuilderViewModel, onRemoveMissing: () -> Unit) {
    var captionFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val report = state.report ?: return

    if (state.days.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Description,
            title = stringResource(R.string.report_no_photos_title),
            body = stringResource(R.string.report_no_photos_body),
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.missingCount > 0) {
                    Notice(
                        pluralStringResource(R.plurals.report_missing_items_body, state.missingCount, state.missingCount),
                        NoticeTone.ERROR,
                        action = { TextButton(onClick = onRemoveMissing) { Text(stringResource(R.string.report_remove_missing)) } },
                    )
                }
                if (state.noCaptionCount > 0) {
                    Notice(pluralStringResource(R.plurals.report_no_caption_notice, state.noCaptionCount, state.noCaptionCount), NoticeTone.WARNING)
                }
                Text(stringResource(R.string.report_photos_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.days.forEach { day ->
            item(key = "d${day.date}") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.lg, bottom = Spacing.xs),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(Formats.dayHeading(day.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                    Text(pluralStringResource(R.plurals.photo_count, day.included.size, day.included.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FsDivider()
            }
            items(day.included, key = { "i${it.item.entry.id}" }) { b ->
                val index = day.included.indexOf(b)
                IncludedRow(
                    b = b,
                    canUp = index > 0,
                    canDown = index < day.included.lastIndex,
                    onUp = { vm.move(b.item.entry.id, -1) },
                    onDown = { vm.move(b.item.entry.id, 1) },
                    onCaption = { captionFor = b.item.entry.id },
                    onDate = { dateFor = b.item.entry.id },
                    onExclude = { vm.exclude(b.item.entry.id) },
                )
                FsDivider(inset = true)
            }
            if (day.excluded.isNotEmpty()) {
                item(key = "x${day.date}") {
                    ExcludedBlock(day.excluded, onInclude = vm::include, onIncludeAll = { vm.includeAll(day.excluded) })
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }

    captionFor?.let { id ->
        val b = state.days.flatMap { it.included }.firstOrNull { it.item.entry.id == id }
        if (b == null) captionFor = null else CaptionDialog(
            original = b.item.photo?.photo?.caption.orEmpty(),
            current = b.item.entry.captionOverride,
            onSave = { vm.setCaption(id, it); captionFor = null },
            onDismiss = { captionFor = null },
        )
    }
    dateFor?.let { id ->
        val b = state.days.flatMap { it.included }.firstOrNull { it.item.entry.id == id }
        if (b == null) dateFor = null else ReportDateDialog(
            report = report,
            current = b.item.entry.reportDate,
            captureDate = b.item.photo?.photo?.captureLocalDate,
            onSave = { vm.setReportDate(id, it); dateFor = null },
            onDismiss = { dateFor = null },
        )
    }
}

@Composable
private fun IncludedRow(
    b: BuilderItem,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onCaption: () -> Unit,
    onDate: () -> Unit,
    onExclude: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val photo = b.item.photo?.photo
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(start = Spacing.gutter, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.Top) {
        PhotoThumbnail(appFile(photo?.thumbnailPath), contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            if (photo == null) {
                Text(stringResource(R.string.report_item_deleted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                b.item.entry.snapshot?.let { Text(it.caption, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            } else {
                Text(Formats.time(photo.captureTime.zoned), style = FieldStampTheme.mono.small, color = MaterialTheme.colorScheme.primary)
                if (photo.captureLocalDate != b.item.entry.reportDate) {
                    Text(stringResource(R.string.report_item_moved, Formats.shortDate(photo.captureLocalDate)), style = MaterialTheme.typography.labelSmall, color = FieldStampTheme.extended.warning)
                }
                Text(
                    b.item.effectiveCaption.ifBlank { stringResource(R.string.photo_no_caption) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (b.item.effectiveCaption.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (b.item.entry.captionOverride != null) {
                    Text(stringResource(R.string.report_caption_override_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val flags = b.issues.mapNotNull { issueLabel(it) }
            if (flags.isNotEmpty()) {
                Text(flags.map { stringResource(it) }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = if (b.issues.any { it == ItemIssue.MISSING_FILE || it == ItemIssue.MISSING_RECORD }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column {
            IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up)) }
            IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down)) }
        }
        Column {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (photo != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.report_edit_caption)) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = { menu = false; onCaption() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.report_change_date)) }, leadingIcon = { Icon(Icons.Outlined.EditCalendar, null) }, onClick = { menu = false; onDate() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.report_exclude)) }, leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, null) }, onClick = { menu = false; onExclude() })
            }
        }
    }
}

private fun issueLabel(issue: ItemIssue): Int? = when (issue) {
    ItemIssue.MISSING_RECORD -> null
    ItemIssue.MISSING_FILE -> R.string.photo_file_missing
    ItemIssue.NO_CAPTION -> R.string.photo_flag_no_caption
    ItemIssue.NO_GPS -> R.string.photo_flag_no_gps
}

@Composable
private fun ExcludedBlock(photos: List<PhotoListItem>, onInclude: (PhotoListItem) -> Unit, onIncludeAll: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = Spacing.touch).padding(horizontal = Spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.report_excluded_count, photos.size, photos.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null)
        }
        if (expanded) {
            photos.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    PhotoThumbnail(appFile(p.photo.thumbnailPath), contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(Formats.time(p.photo.captureTime.zoned), style = FieldStampTheme.mono.small)
                        Text(p.photo.caption.ifBlank { stringResource(R.string.photo_no_caption) }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { onInclude(p) }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.report_include))
                    }
                }
            }
            if (photos.size > 1) {
                TextButton(onClick = onIncludeAll, modifier = Modifier.padding(horizontal = Spacing.sm)) { Text(stringResource(R.string.report_include_all)) }
            }
        }
        FsDivider()
    }
}

@Composable
private fun CaptionDialog(original: String, current: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(current ?: original) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_edit_caption)) },
        text = {
            Column {
                Text(stringResource(R.string.report_caption_dialog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(1000) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.takeIf { it != original }) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.report_caption_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDateDialog(report: Report, current: LocalDate, captureDate: LocalDate?, onSave: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val start = report.periodStart
    val end = report.periodEnd
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = start.year..end.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !d.isBefore(start) && !d.isAfter(end)
            }
            override fun isSelectableYear(year: Int) = year in start.year..end.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onSave(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        Column {
            captureDate?.let {
                Text(
                    stringResource(R.string.report_date_dialog_hint, Formats.shortDate(it)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                )
            }
            DatePicker(state = picker, showModeToggle = false, title = null)
        }
    }
}

// --- Settings tab -----------------------------------------------------------------------------------

@Composable
private fun SettingsTab(vm: ReportBuilderViewModel, state: BuilderState, onPaywall: () -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val d = draft ?: return LoadingState()
    val pro = state.entitlement.isPro
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = d.title,
            onValueChange = { v -> vm.editSettings { it.copy(title = v.take(120)) } },
            label = { Text(stringResource(R.string.field_report_title)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        )
        OutlinedTextField(
            value = d.notes,
            onValueChange = { v -> vm.editSettings { it.copy(notes = v.take(1500)) } },
            label = { Text(stringResource(R.string.field_report_notes)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        )
        OutlinedTextField(
            value = d.closingNotes,
            onValueChange = { v -> vm.editSettings { it.copy(closingNotes = v.take(1500)) } },
            label = { Text(stringResource(R.string.field_report_closing)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        )
        OutlinedTextField(
            value = d.preparedBy,
            onValueChange = { v -> vm.editSettings { it.copy(preparedBy = v.take(120)) } },
            label = { Text(stringResource(R.string.field_prepared_by)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        )
        Spacer(Modifier.height(Spacing.sm))
        SwitchRow(stringResource(R.string.report_include_gps), d.includeGps, { v -> vm.editSettings { it.copy(includeGps = v) } }, supporting = stringResource(R.string.report_include_gps_hint))
        SwitchRow(stringResource(R.string.report_use_stamped), d.useStampedPhotos, { v -> vm.editSettings { it.copy(useStampedPhotos = v) } }, supporting = stringResource(R.string.report_use_stamped_hint))
        SwitchRow(stringResource(R.string.report_signature_block), d.includeSignatureBlock, { v -> vm.editSettings { it.copy(includeSignatureBlock = v) } }, supporting = stringResource(R.string.report_signature_hint))
        FsDivider()
        SectionHeader(stringResource(R.string.report_layout_heading))
        FlowRow(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ReportLayout.entries.forEach { layout ->
                val locked = layout.requiresPro && !pro
                FilterChip(
                    selected = d.layout == layout,
                    onClick = { if (locked) onPaywall() else vm.editSettings { it.copy(layout = layout) } },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(layoutLabel(layout)))
                            if (locked) { Spacer(Modifier.width(Spacing.xs)); ProBadge() }
                        }
                    },
                )
            }
        }
        if (d.layout.requiresPro && !pro) {
            Notice(stringResource(R.string.report_layout_downgraded), NoticeTone.INFO, Modifier.padding(Spacing.gutter))
        }
        SwitchRow(
            title = stringResource(R.string.report_branding),
            checked = d.useBranding && pro,
            onCheckedChange = { v -> if (!pro) onPaywall() else vm.editSettings { it.copy(useBranding = v) } },
            supporting = stringResource(if (state.hasBrandingConfigured) R.string.report_branding_hint else R.string.report_branding_not_set),
            badge = if (!pro) { { ProBadge() } } else null,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

private fun layoutLabel(layout: ReportLayout): Int = when (layout) {
    ReportLayout.STANDARD -> R.string.layout_standard
    ReportLayout.LARGE -> R.string.layout_large
    ReportLayout.COMPACT -> R.string.layout_compact
}

// --- PDF tab ----------------------------------------------------------------------------------------

@Composable
private fun PdfTab(
    report: Report,
    pdfExists: Boolean,
    zipAllowed: Boolean,
    zipBusy: Boolean,
    running: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onPrint: () -> Unit,
    onZip: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.sm)) {
        when {
            !pdfExists && report.pdfPath != null -> Notice(stringResource(R.string.report_pdf_file_missing), NoticeTone.ERROR, Modifier.padding(horizontal = Spacing.gutter))
            !pdfExists -> Text(
                stringResource(R.string.report_pdf_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            else -> {
                if (report.pdfOutdated) {
                    Notice(stringResource(R.string.report_pdf_outdated), NoticeTone.WARNING, Modifier.padding(horizontal = Spacing.gutter))
                    Spacer(Modifier.height(Spacing.sm))
                }
                PdfAction(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.action_open), enabled = !running, onClick = onOpen)
                PdfAction(Icons.Outlined.Share, stringResource(R.string.action_share), enabled = !running, onClick = onShare)
                PdfAction(Icons.Outlined.SaveAlt, stringResource(R.string.action_save_as_file), enabled = !running, onClick = onSaveAs)
                PdfAction(Icons.Outlined.Print, stringResource(R.string.action_print), enabled = !running, onClick = onPrint)
            }
        }
        FsDivider(Modifier.padding(vertical = Spacing.sm))
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.report_zip_title), style = MaterialTheme.typography.bodyLarge)
                    if (!zipAllowed) { Spacer(Modifier.width(Spacing.sm)); ProBadge() }
                }
                Text(stringResource(R.string.report_zip_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onZip, enabled = !zipBusy) {
                Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.action_export))
            }
        }
        Text(
            stringResource(R.string.report_integrity_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}

@Composable
private fun PdfAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = Spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.lg))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
