package id.asmaraputra.fieldstamp.feature.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.ReportBuilderRoute
import id.asmaraputra.fieldstamp.app.ReportCreateRoute
import id.asmaraputra.fieldstamp.app.ReportsRoute
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.core.designsystem.EmptyState
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsExtendedFab
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.ListRow
import id.asmaraputra.fieldstamp.core.designsystem.LoadingState
import id.asmaraputra.fieldstamp.core.designsystem.Notice
import id.asmaraputra.fieldstamp.core.designsystem.NoticeTone
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.Report
import id.asmaraputra.fieldstamp.core.model.ReportStatus
import id.asmaraputra.fieldstamp.data.PhotoFilter
import id.asmaraputra.fieldstamp.feature.common.MonthPickerDialog
import id.asmaraputra.fieldstamp.feature.common.PickerField
import id.asmaraputra.fieldstamp.feature.common.ProjectActivityFields
import id.asmaraputra.fieldstamp.report.ReportPeriod
import id.asmaraputra.fieldstamp.report.ReportSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class ReportsViewModel(c: AppContainer) : ViewModel() {
    val reports: StateFlow<List<ReportSummary>?> = c.reports.observeSummaries()
        .map<List<ReportSummary>, List<ReportSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ReportsScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> ReportsViewModel(c) }
    val reports by vm.reports.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { FsTopBar(stringResource(R.string.nav_reports)) },
        floatingActionButton = {
            FsExtendedFab(label = stringResource(R.string.report_create), icon = Icons.Outlined.Add, onClick = { nav.navigate(ReportCreateRoute) })
        },
    ) { padding ->
        val list = reports
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.reports_empty_title),
                body = stringResource(R.string.reports_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(list, key = { it.report.id }) { summary ->
                    val r = summary.report
                    ListRow(
                        title = r.title,
                        supporting = "${Formats.period(r.periodStart, r.periodEnd)} · ${pluralStringResource(R.plurals.photo_count, summary.photoCount, summary.photoCount)}",
                        leading = { StatusIcon(r) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(statusLabel(r), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                            }
                        },
                        onClick = { nav.navigate(ReportBuilderRoute(r.id)) },
                    )
                    FsDivider(inset = true)
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

/** Status is shown as icon + text, never color alone. */
@Composable
fun statusLabel(r: Report): String = when {
    r.status == ReportStatus.FAILED -> stringResource(R.string.report_status_failed)
    r.pdfPath != null && r.pdfOutdated -> stringResource(R.string.report_status_outdated)
    r.status == ReportStatus.GENERATED -> stringResource(R.string.report_status_generated)
    else -> stringResource(R.string.report_status_draft)
}

@Composable
fun StatusIcon(r: Report) {
    val (icon: ImageVector, tint) = when {
        r.status == ReportStatus.FAILED -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
        r.pdfPath != null && r.pdfOutdated -> Icons.Outlined.Update to FieldStampTheme.extended.warning
        r.status == ReportStatus.GENERATED -> Icons.Outlined.TaskAlt to FieldStampTheme.extended.success
        else -> Icons.Outlined.EditNote to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, contentDescription = null, tint = tint)
}

fun generatedLabel(millis: Long): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "${Formats.shortDate(zoned.toLocalDate())} ${Formats.time(zoned)}"
}

// ---------------------------------------------------------------------------------------------------
// Create report

data class CreateForm(val month: YearMonth, val projectId: Long?, val activityId: Long?, val title: String, val titleEdited: Boolean)

data class CreateState(
    val form: CreateForm,
    val projects: List<Project> = emptyList(),
    val activities: List<FieldActivity> = emptyList(),
    val matchingCount: Int? = null,
    val matchingDays: Int = 0,
)

class ReportCreateViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val form = MutableStateFlow(
        CreateForm(
            month = handle.get<String>("month")?.let(YearMonth::parse) ?: YearMonth.now(),
            projectId = handle.get<Long>("projectId"),
            activityId = handle.get<Long>("activityId"),
            title = handle["title"] ?: "",
            titleEdited = handle["titleEdited"] ?: false,
        ),
    )
    private val handle = handle

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CreateState> = combine(
        form,
        c.projects.observeProjects(),
        c.projects.observeActivities(),
        form.flatMapLatest { f ->
            c.photos.observe(PhotoFilter(start = f.month.atDay(1), end = f.month.atEndOfMonth(), projectId = f.projectId, activityId = f.activityId))
        },
    ) { f, projects, activities, photos ->
        val project = projects.firstOrNull { it.id == f.projectId }
        val title = if (f.titleEdited) f.title else defaultTitle(f.month, project?.name)
        CreateState(f.copy(title = title), projects, activities, photos.size, photos.map { it.photo.captureLocalDate }.distinct().size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateState(form.value))

    private fun defaultTitle(month: YearMonth, project: String?): String =
        listOfNotNull(c.appContext.getString(R.string.report_default_title), project).joinToString(" – ") + " · " + Formats.monthYear(month)

    private fun update(block: (CreateForm) -> CreateForm) {
        val next = block(form.value)
        form.value = next
        handle["month"] = next.month.toString()
        handle["projectId"] = next.projectId
        handle["activityId"] = next.activityId
        handle["title"] = next.title
        handle["titleEdited"] = next.titleEdited
    }

    fun setMonth(m: YearMonth) = update { it.copy(month = m) }
    fun setProject(id: Long?) = update { f -> f.copy(projectId = id, activityId = state.value.activities.firstOrNull { it.id == f.activityId && it.projectId == id }?.id) }
    fun setActivity(id: Long?) = update { it.copy(activityId = id) }
    fun setTitle(t: String) = update { it.copy(title = t.take(120), titleEdited = true) }

    fun create(onCreated: (Long) -> Unit) = viewModelScope.launch {
        val s = state.value
        val settings = c.settings.current()
        val id = c.reports.create(
            title = s.form.title.ifBlank { defaultTitle(s.form.month, null) },
            period = ReportPeriod.month(s.form.month),
            projectId = s.form.projectId,
            activityId = s.form.activityId,
            preparedBy = settings.preparedBy,
            useBranding = c.billing.entitlement.value.isPro && (settings.organizationName.isNotBlank() || settings.logoPath != null),
        )
        onCreated(id)
    }
}

@Composable
fun ReportCreateScreen(nav: NavController) {
    val vm = appViewModel { c, h -> ReportCreateViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    var pickingMonth by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { FsTopBar(stringResource(R.string.report_create), onBack = { nav.popBackStack() }) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = {
                        vm.create { id ->
                            nav.navigate(ReportBuilderRoute(id)) { popUpTo<ReportsRoute> { inclusive = false } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.gutter).height(Spacing.touch),
                ) { Text(stringResource(R.string.report_create_action)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.report_create_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
            PickerField(stringResource(R.string.field_period), Formats.monthYear(state.form.month), onClick = { pickingMonth = true })
            FsDivider(inset = true)
            ProjectActivityFields(
                projects = state.projects,
                activities = state.activities,
                projectId = state.form.projectId,
                activityId = state.form.activityId,
                onProjectChange = vm::setProject,
                onActivityChange = vm::setActivity,
                onCreateProject = null,
                onCreateActivity = null,
                noneProjectLabel = stringResource(R.string.filter_all_projects),
                noneActivityLabel = stringResource(R.string.filter_all_activities),
            )
            FsDivider(inset = true)
            OutlinedTextField(
                value = state.form.title,
                onValueChange = vm::setTitle,
                label = { Text(stringResource(R.string.field_report_title)) },
                modifier = Modifier.fillMaxWidth().padding(Spacing.gutter),
            )
            val count = state.matchingCount
            if (count != null) {
                Notice(
                    if (count == 0) stringResource(R.string.report_create_no_photos)
                    else pluralStringResource(R.plurals.report_create_matching, count, count, state.matchingDays),
                    if (count == 0) NoticeTone.WARNING else NoticeTone.INFO,
                    Modifier.padding(horizontal = Spacing.gutter),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (pickingMonth) {
        MonthPickerDialog(initial = state.form.month, onConfirm = { vm.setMonth(it); pickingMonth = false }, onDismiss = { pickingMonth = false })
    }
}

