package id.asmaraputra.fieldstamp.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.CameraRoute
import id.asmaraputra.fieldstamp.app.PhotoDetailRoute
import id.asmaraputra.fieldstamp.app.PhotosRoute
import id.asmaraputra.fieldstamp.app.ProjectsRoute
import id.asmaraputra.fieldstamp.app.ReportBuilderRoute
import id.asmaraputra.fieldstamp.app.ReportCreateRoute
import id.asmaraputra.fieldstamp.app.TemplatesRoute
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.core.designsystem.CaptureButton
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.LoadingState
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.ListRow
import id.asmaraputra.fieldstamp.core.designsystem.PhotoThumbnail
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.feature.common.appFile
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.feature.common.ProjectActivityFields
import id.asmaraputra.fieldstamp.feature.common.rememberPhotoImport
import id.asmaraputra.fieldstamp.feature.reports.StatusIcon
import id.asmaraputra.fieldstamp.feature.reports.statusLabel
import id.asmaraputra.fieldstamp.feature.templates.TemplatePickerField
import id.asmaraputra.fieldstamp.report.ReportSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val projects: List<Project> = emptyList(),
    val activities: List<FieldActivity> = emptyList(),
    val projectId: Long? = null,
    val activityId: Long? = null,
    val todayPhotos: List<PhotoListItem> = emptyList(),
    val templates: List<StampTemplate> = emptyList(),
    val selectedTemplateId: Long? = null,
    val totalPhotos: Int = 0,
    val latestReport: ReportSummary? = null,
    val loaded: Boolean = false,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val today = flow { emit(LocalDate.now()) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(
        c.settings.settings,
        c.projects.observeProjects(),
        c.projects.observeActivities(),
        today.flatMapLatest { day -> c.photos.observeDay(day) },
        combine(c.templates.observeAll(), c.photos.observeCount(), c.reports.observeSummaries(), ::Triple),
    ) { settings, projects, activities, todayPhotos, (templates, count, reports) ->
        HomeState(
            projects = projects,
            activities = activities,
            projectId = settings.currentProjectId?.takeIf { id -> projects.any { it.id == id } },
            activityId = settings.currentActivityId?.takeIf { id -> activities.any { it.id == id } },
            todayPhotos = todayPhotos,
            templates = templates,
            selectedTemplateId = settings.selectedTemplateId?.takeIf { id -> templates.any { it.id == id } },
            totalPhotos = count,
            latestReport = reports.maxByOrNull { it.report.updatedAt },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setProject(id: Long?) = viewModelScope.launch {
        // Changing the project clears an activity that belongs to a different project.
        val activity = state.value.activities.firstOrNull { it.id == state.value.activityId }
        c.settings.setCaptureContext(id, activity?.takeIf { it.projectId == id }?.id)
    }

    fun setActivity(id: Long?) = viewModelScope.launch { c.settings.setCaptureContext(state.value.projectId, id) }

    fun setTemplate(id: Long?) = viewModelScope.launch { c.settings.setSelectedTemplate(id) }

    fun createProject(name: String) = viewModelScope.launch {
        val id = c.projects.createProject(name, "")
        c.settings.setCaptureContext(id, null)
    }

    fun createActivity(name: String) = viewModelScope.launch {
        val id = c.projects.createActivity(state.value.projectId, name, "")
        c.settings.setCaptureContext(state.value.projectId, id)
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val import = rememberPhotoImport(nav) {
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.import_failed)) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // The capture dock stays fixed above the navigation bar: the camera is always one tap away.
        bottomBar = { CaptureDock(onCapture = { nav.navigate(CameraRoute) }, onImport = import) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        if (!state.loaded) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Masthead(state.today)

            ProjectActivityFields(
                projects = state.projects,
                activities = state.activities,
                projectId = state.projectId,
                activityId = state.activityId,
                onProjectChange = vm::setProject,
                onActivityChange = vm::setActivity,
                onCreateProject = vm::createProject,
                onCreateActivity = vm::createActivity,
            )
            FsDivider(inset = true)
            TemplatePickerField(templates = state.templates, selectedId = state.selectedTemplateId, onSelect = vm::setTemplate)
            FsDivider(inset = true)

            Spacer(Modifier.height(Spacing.md))
            if (state.todayPhotos.isEmpty()) {
                SectionHeader(stringResource(R.string.home_today))
                Column(Modifier.padding(horizontal = Spacing.gutter)) {
                    Text(
                        stringResource(R.string.home_today_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.home_today_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            } else {
                SectionHeader(
                    text = stringResource(R.string.home_today) + " · " + pluralStringResource(R.plurals.photo_count, state.todayPhotos.size, state.todayPhotos.size),
                    trailing = { TextButton(onClick = { nav.navigate(PhotosRoute) }) { Text(stringResource(R.string.action_see_all)) } },
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.todayPhotos, key = { it.photo.id }) { item ->
                        // Time sits under the thumbnail so it never covers the stamp printed on the photo.
                        Column(Modifier.width(THUMB_WIDTH)) {
                            PhotoThumbnail(
                                file = appFile(item.photo.thumbnailPath),
                                contentDescription = item.photo.caption.ifBlank { stringResource(R.string.photo_no_caption) },
                                modifier = Modifier
                                    .size(THUMB_WIDTH, THUMB_HEIGHT)
                                    .clickable { nav.navigate(PhotoDetailRoute(item.photo.id)) },
                            )
                            Text(
                                Formats.time(item.photo.captureTime.zoned),
                                style = FieldStampTheme.mono.small,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            state.latestReport?.let { latest ->
                FsDivider(inset = true)
                ListRow(
                    title = latest.report.title,
                    supporting = statusLabel(latest.report) + " · " + pluralStringResource(R.plurals.photo_count, latest.photoCount, latest.photoCount),
                    titleMaxLines = 2,
                    leading = { StatusIcon(latest.report) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    onClick = { nav.navigate(ReportBuilderRoute(latest.report.id)) },
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            SectionHeader(stringResource(R.string.home_manage))
            ListRow(
                title = stringResource(R.string.projects_title),
                supporting = if (state.projects.isEmpty()) stringResource(R.string.home_projects_empty)
                else pluralStringResource(R.plurals.project_count, state.projects.size, state.projects.size),
                leading = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(ProjectsRoute) },
            )
            FsDivider(inset = true)
            ListRow(
                title = stringResource(R.string.templates_title),
                supporting = if (state.templates.isEmpty()) stringResource(R.string.home_templates_empty)
                else pluralStringResource(R.plurals.template_count, state.templates.size, state.templates.size),
                leading = { Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(TemplatesRoute) },
            )
            FsDivider(inset = true)
            ListRow(
                title = stringResource(R.string.report_create),
                supporting = stringResource(R.string.home_report_hint),
                leading = { Icon(Icons.Outlined.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(ReportCreateRoute) },
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

private val THUMB_WIDTH = 120.dp
private val THUMB_HEIGHT = 160.dp

/** Weekday and time zone in mono caps, the date as the headline, then a heavy rule. Nothing decorative. */
@Composable
private fun Masthead(today: LocalDate) {
    val zone = Formats.zoneLabel(ZonedDateTime.now())
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Formats.locale).uppercase(Formats.locale)
    Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xl)) {
        Text(
            "$weekday · $zone",
            style = FieldStampTheme.mono.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            Formats.longDate(today),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CaptureDock(onCapture: () -> Unit, onImport: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            FsDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedIconButton(
                    onClick = onImport,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(Spacing.capture),
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.action_import_photo))
                }
                CaptureButton(
                    label = stringResource(R.string.action_take_photo),
                    onClick = onCapture,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
