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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
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
import id.asmaraputra.fieldstamp.app.ReportCreateRoute
import id.asmaraputra.fieldstamp.app.TemplatesRoute
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val today = flow { emit(LocalDate.now()) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(
        c.settings.settings,
        c.projects.observeProjects(),
        c.projects.observeActivities(),
        today.flatMapLatest { day -> c.photos.observeDay(day) },
        combine(c.templates.observeAll(), c.photos.observeCount(), ::Pair),
    ) { settings, projects, activities, todayPhotos, (templates, count) ->
        HomeState(
            projects = projects,
            activities = activities,
            projectId = settings.currentProjectId?.takeIf { id -> projects.any { it.id == id } },
            activityId = settings.currentActivityId?.takeIf { id -> activities.any { it.id == id } },
            todayPhotos = todayPhotos,
            templates = templates,
            selectedTemplateId = settings.selectedTemplateId?.takeIf { id -> templates.any { it.id == id } },
            totalPhotos = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setProject(id: Long?) = viewModelScope.launch {
        // Changing the project clears an activity that belongs to a different project.
        val activity = state.value.activities.firstOrNull { it.id == state.value.activityId }
        c.settings.setCaptureContext(id, activity?.takeIf { it.projectId == id }?.id)
    }

    fun setActivity(id: Long?) = viewModelScope.launch { c.settings.setCaptureContext(state.value.projectId, id) }

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
    val context = LocalContext.current
    val resources = LocalResources.current
    val import = rememberPhotoImport(nav) {
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.import_failed)) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Masthead: product name and today's date, nothing decorative.
            Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xl, bottom = Spacing.md)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    Formats.dayHeading(state.today),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = Spacing.xs)) {
                    Text(
                        stringResource(R.string.home_context_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    )
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
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = { nav.navigate(CameraRoute) },
                colors = ButtonDefaults.buttonColors(containerColor = FieldStampTheme.extended.capture, contentColor = FieldStampTheme.extended.onCapture),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(vertical = 18.dp),
                modifier = Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.action_take_photo), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = import,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth().height(Spacing.touch),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.action_import_photo))
            }
            val template = state.templates.firstOrNull { it.id == state.selectedTemplateId }
            Text(
                if (template != null) stringResource(R.string.home_template_active, template.name) else stringResource(R.string.home_template_basic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )

            Spacer(Modifier.height(Spacing.md))
            SectionHeader(
                text = stringResource(R.string.home_today) + " · " + pluralStringResource(R.plurals.photo_count, state.todayPhotos.size, state.todayPhotos.size),
                trailing = if (state.totalPhotos > 0) {
                    { TextButton(onClick = { nav.navigate(PhotosRoute) }) { Text(stringResource(R.string.action_see_all)) } }
                } else null,
            )
            if (state.todayPhotos.isEmpty()) {
                Text(
                    stringResource(R.string.home_today_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.todayPhotos, key = { it.photo.id }) { item ->
                        Column(Modifier.width(112.dp)) {
                            PhotoThumbnail(
                                file = appFile(item.photo.thumbnailPath),
                                contentDescription = item.photo.caption.ifBlank { stringResource(R.string.photo_no_caption) },
                                modifier = Modifier
                                    .size(112.dp)
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
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(stringResource(R.string.home_manage))
            ListRow(
                title = stringResource(R.string.projects_title),
                supporting = pluralStringResource(R.plurals.project_count, state.projects.size, state.projects.size),
                leading = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(ProjectsRoute) },
            )
            FsDivider(inset = true)
            ListRow(
                title = stringResource(R.string.templates_title),
                supporting = pluralStringResource(R.plurals.template_count, state.templates.size, state.templates.size),
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
