package id.asmaraputra.fieldstamp.feature.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import id.asmaraputra.fieldstamp.app.container
import id.asmaraputra.fieldstamp.data.PhotoFilter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.PhotosRoute
import id.asmaraputra.fieldstamp.app.ProjectDetailRoute
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.core.designsystem.ConfirmDialog
import id.asmaraputra.fieldstamp.core.designsystem.EmptyState
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsExtendedFab
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.ListRow
import id.asmaraputra.fieldstamp.core.designsystem.LoadingState
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.data.ProjectSummary
import id.asmaraputra.fieldstamp.feature.common.NameDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val c: AppContainer) : ViewModel() {
    val projects: StateFlow<List<ProjectSummary>?> = c.projects.observeSummaries()
        .map<List<ProjectSummary>, List<ProjectSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, description: String) = viewModelScope.launch { c.projects.createProject(name, description) }
}

@Composable
fun ProjectsScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> ProjectsViewModel(c) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { FsTopBar(stringResource(R.string.projects_title), onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            FsExtendedFab(label = stringResource(R.string.project_create), icon = Icons.Outlined.Add, onClick = { creating = true })
        },
    ) { padding ->
        val list = projects
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.projects_empty_title),
                body = stringResource(R.string.projects_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                val active = list.filter { !it.project.isArchived }
                val archived = list.filter { it.project.isArchived }
                items(active, key = { it.project.id }) { summary ->
                    ProjectRow(summary) { nav.navigate(ProjectDetailRoute(summary.project.id)) }
                    FsDivider(inset = true)
                }
                if (archived.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.projects_archived)) }
                    items(archived, key = { it.project.id }) { summary ->
                        ProjectRow(summary) { nav.navigate(ProjectDetailRoute(summary.project.id)) }
                        FsDivider(inset = true)
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.project_create),
            initial = "",
            onConfirm = {},
            onDismiss = { creating = false },
            initialDescription = "",
            onConfirmWithDescription = { name, desc -> vm.create(name, desc); creating = false },
        )
    }
}

@Composable
private fun ProjectRow(summary: ProjectSummary, onClick: () -> Unit) {
    ListRow(
        title = summary.project.name,
        supporting = listOf(
            pluralStringResource(R.plurals.photo_count, summary.photoCount, summary.photoCount),
            pluralStringResource(R.plurals.activity_count, summary.activityCount, summary.activityCount),
        ).joinToString(" · "),
        trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
        onClick = onClick,
    )
}

data class ProjectDetailState(val project: Project?, val activities: List<FieldActivity>, val loaded: Boolean)

class ProjectDetailViewModel(private val c: AppContainer, private val projectId: Long) : ViewModel() {
    val state: StateFlow<ProjectDetailState> = combine(
        c.projects.observeProject(projectId),
        c.projects.observeActivities(projectId),
    ) { project, activities -> ProjectDetailState(project, activities, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailState(null, emptyList(), false))

    fun rename(name: String, description: String) = viewModelScope.launch { c.projects.updateProject(projectId, name, description) }
    fun setArchived(archived: Boolean) = viewModelScope.launch { c.projects.setArchived(projectId, archived) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val settings = c.settings.current()
        if (settings.currentProjectId == projectId) c.settings.setCaptureContext(null, null)
        c.projects.deleteProject(projectId)
        onDone()
    }
    fun addActivity(name: String, description: String) = viewModelScope.launch { c.projects.createActivity(projectId, name, description) }
    fun editActivity(id: Long, name: String, description: String) = viewModelScope.launch { c.projects.updateActivity(id, name, description) }
    fun deleteActivity(id: Long) = viewModelScope.launch { c.projects.deleteActivity(id) }
}

@Composable
fun ProjectDetailScreen(nav: NavController, projectId: Long) {
    val vm = appViewModel(key = "project-$projectId") { c, _ -> ProjectDetailViewModel(c, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var editingActivity by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingActivity by rememberSaveable { mutableStateOf<Long?>(null) }
    val project = state.project
    val container = LocalContext.current.container

    Scaffold(
        topBar = {
            FsTopBar(project?.name ?: stringResource(R.string.projects_title), onBack = { nav.popBackStack() }) {
                if (project != null) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menu = false; dialog = "edit" },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (project.isArchived) R.string.action_unarchive else R.string.action_archive)) },
                            leadingIcon = { Icon(if (project.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = null) },
                            onClick = { menu = false; vm.setArchived(!project.isArchived) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; dialog = "delete" },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (project != null) {
                FsExtendedFab(label = stringResource(R.string.activity_create), icon = Icons.Outlined.Add, onClick = { dialog = "activity" })
            }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingState(Modifier.padding(padding))
            project == null -> EmptyState(Icons.Outlined.Folder, stringResource(R.string.project_missing_title), stringResource(R.string.project_missing_body), Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                        if (project.description.isNotBlank()) {
                            Text(project.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (project.isArchived) {
                            Text(stringResource(R.string.project_archived_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            container.pendingPhotoFilter.value = PhotoFilter(projectId = project.id)
                            nav.navigate(PhotosRoute)
                        }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.project_view_photos_hint))
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.activities_title)) }
                if (state.activities.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.activities_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.gutter),
                        )
                    }
                }
                items(state.activities, key = { it.id }) { activity ->
                    ListRow(
                        title = activity.name,
                        supporting = activity.description.ifBlank { null },
                        trailing = {
                            Row {
                                IconButton(onClick = { editingActivity = activity.id }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
                                IconButton(onClick = { deletingActivity = activity.id }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
                            }
                        },
                    )
                    FsDivider(inset = true)
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    when (dialog) {
        "edit" -> project?.let {
            NameDialog(
                title = stringResource(R.string.project_edit),
                initial = it.name,
                onConfirm = {},
                onDismiss = { dialog = null },
                initialDescription = it.description,
                onConfirmWithDescription = { name, desc -> vm.rename(name, desc); dialog = null },
            )
        }
        "delete" -> ConfirmDialog(
            title = stringResource(R.string.project_delete_title),
            body = stringResource(R.string.project_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { dialog = null; vm.delete { nav.popBackStack() } },
            onDismiss = { dialog = null },
        )
        "activity" -> NameDialog(
            title = stringResource(R.string.activity_create),
            initial = "",
            onConfirm = {},
            onDismiss = { dialog = null },
            initialDescription = "",
            onConfirmWithDescription = { name, desc -> vm.addActivity(name, desc); dialog = null },
        )
    }
    editingActivity?.let { id ->
        state.activities.firstOrNull { it.id == id }?.let { activity ->
            NameDialog(
                title = stringResource(R.string.activity_edit),
                initial = activity.name,
                onConfirm = {},
                onDismiss = { editingActivity = null },
                initialDescription = activity.description,
                onConfirmWithDescription = { name, desc -> vm.editActivity(id, name, desc); editingActivity = null },
            )
        }
    }
    deletingActivity?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.activity_delete_title),
            body = stringResource(R.string.activity_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.deleteActivity(id); deletingActivity = null },
            onDismiss = { deletingActivity = null },
        )
    }
}

