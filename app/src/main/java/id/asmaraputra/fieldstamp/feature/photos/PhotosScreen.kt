package id.asmaraputra.fieldstamp.feature.photos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.SpeakerNotesOff
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.app.container
import id.asmaraputra.fieldstamp.core.designsystem.ConfirmDialog
import id.asmaraputra.fieldstamp.core.designsystem.EmptyState
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.LoadingState
import id.asmaraputra.fieldstamp.core.designsystem.PhotoThumbnail
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.data.PhotoFilter
import id.asmaraputra.fieldstamp.feature.common.MonthPickerDialog
import id.asmaraputra.fieldstamp.feature.common.OptionSheet
import id.asmaraputra.fieldstamp.feature.common.PickerOption
import id.asmaraputra.fieldstamp.feature.common.appFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class PhotosState(
    val loaded: Boolean = false,
    val days: List<Pair<LocalDate, List<PhotoListItem>>> = emptyList(),
    val totalInLibrary: Int = 0,
    val projects: List<Project> = emptyList(),
    val activities: List<FieldActivity> = emptyList(),
) {
    val isEmpty: Boolean get() = days.isEmpty()
}

class PhotosViewModel(private val c: AppContainer) : ViewModel() {
    private val _filter = MutableStateFlow(c.pendingPhotoFilter.value ?: PhotoFilter())
    val filter: StateFlow<PhotoFilter> = _filter.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    init {
        // Pick up "show photos of project X" requests from other screens.
        viewModelScope.launch {
            c.pendingPhotoFilter.collect { request ->
                if (request != null) {
                    _filter.value = request
                    c.pendingPhotoFilter.value = null
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PhotosState> = combine(
        _filter.flatMapLatest { c.photos.observe(it) },
        c.photos.observeCount(),
        c.projects.observeProjects(),
        c.projects.observeActivities(),
    ) { items, total, projects, activities ->
        PhotosState(
            loaded = true,
            days = items.groupBy { it.photo.captureLocalDate }.toList().sortedByDescending { it.first },
            totalInLibrary = total,
            projects = projects,
            activities = activities,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosState())

    fun setFilter(f: PhotoFilter) { _filter.value = f }
    fun setMonth(month: YearMonth?) = setFilter(_filter.value.copy(start = month?.atDay(1), end = month?.atEndOfMonth()))
    fun setProject(id: Long?) = setFilter(_filter.value.copy(projectId = id, activityId = null))
    fun setActivity(id: Long?) = setFilter(_filter.value.copy(activityId = id))
    fun setQuery(q: String) = setFilter(_filter.value.copy(query = q.take(80)))
    fun clearFilters() = setFilter(PhotoFilter())

    fun toggleSelection(id: Long) { _selection.value = _selection.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { _selection.value = emptySet() }

    fun selectedItems(): List<PhotoListItem> = state.value.days.flatMap { it.second }.filter { it.photo.id in _selection.value }

    fun deleteSelected() = viewModelScope.launch {
        _selection.value.forEach { c.photos.delete(it) }
        clearSelection()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> PhotosViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(filter.query.isNotEmpty()) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                FsTopBar(
                    title = pluralStringResource(R.plurals.selected_count, selection.size, selection.size),
                    actions = {
                        IconButton(onClick = {
                            val paths = vm.selectedItems().mapNotNull { item -> item.photo.displayPath.takeIf { context.container.files.exists(it) } }
                            if (paths.isEmpty()) {
                                scope.launch { snackbar.showSnackbar(resources.getString(R.string.photo_file_missing)) }
                            } else {
                                context.startActivity(context.container.export.shareIntent(paths, "image/jpeg", resources.getString(R.string.action_share)))
                            }
                        }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear_selection)) }
                    },
                )
            } else {
                FsTopBar(stringResource(R.string.nav_photos)) {
                    IconButton(onClick = { searching = !searching; if (!searching) vm.setQuery("") }) {
                        Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                    }
                }
            }
        },
        floatingActionButton = {
            if (selection.isEmpty()) {
                FloatingActionButton(
                    onClick = { nav.navigate(CameraRoute) },
                    containerColor = FieldStampTheme.extended.capture,
                    contentColor = FieldStampTheme.extended.onCapture,
                ) { Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.action_take_photo)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searching) {
                TextField(
                    value = filter.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text(stringResource(R.string.photos_search_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                )
            }
            FilterBar(filter, state, onMonth = { sheet = "month" }, onProject = { sheet = "project" }, onActivity = { sheet = "activity" }, vm = vm)

            when {
                !state.loaded -> LoadingState()
                state.totalInLibrary == 0 -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.photos_empty_title),
                    body = stringResource(R.string.photos_empty_body),
                    action = { OutlinedButton(onClick = { nav.navigate(CameraRoute) }) { Text(stringResource(R.string.action_take_photo)) } },
                )
                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.photos_no_results_title),
                    body = stringResource(R.string.photos_no_results_body),
                    action = { OutlinedButton(onClick = vm::clearFilters) { Text(stringResource(R.string.action_clear_filters)) } },
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    state.days.forEach { (day, items) ->
                        item(key = "h$day", span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.padding(top = Spacing.md, bottom = Spacing.xs), verticalAlignment = Alignment.Bottom) {
                                Text(Formats.dayHeading(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(pluralStringResource(R.plurals.photo_count, items.size, items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(items, key = { it.photo.id }) { item ->
                            PhotoCell(
                                item = item,
                                selected = item.photo.id in selection,
                                onClick = {
                                    if (selection.isNotEmpty()) vm.toggleSelection(item.photo.id)
                                    else nav.navigate(PhotoDetailRoute(item.photo.id))
                                },
                                onLongClick = { vm.toggleSelection(item.photo.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        "month" -> MonthPickerDialog(
            initial = filter.start?.let { YearMonth.from(it) } ?: YearMonth.now(),
            onConfirm = { vm.setMonth(it); sheet = null },
            onDismiss = { sheet = null },
            onClear = { vm.setMonth(null); sheet = null },
        )
        "project" -> OptionSheet(
            title = stringResource(R.string.field_project),
            options = listOf(PickerOption(null, stringResource(R.string.filter_all_projects))) + state.projects.map { PickerOption(it.id, it.name) },
            selectedId = filter.projectId,
            onSelect = { vm.setProject(it); sheet = null },
            onDismiss = { sheet = null },
        )
        "activity" -> OptionSheet(
            title = stringResource(R.string.field_activity),
            options = listOf(PickerOption(null, stringResource(R.string.filter_all_activities))) + state.activities.filter { it.projectId == filter.projectId }.map { PickerOption(it.id, it.name) },
            selectedId = filter.activityId,
            onSelect = { vm.setActivity(it); sheet = null },
            onDismiss = { sheet = null },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.photo_delete_title, selection.size, selection.size),
            body = stringResource(R.string.photo_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.deleteSelected(); confirmDelete = false },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FilterBar(
    filter: PhotoFilter,
    state: PhotosState,
    onMonth: () -> Unit,
    onProject: () -> Unit,
    onActivity: () -> Unit,
    vm: PhotosViewModel,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = filter.start != null,
            onClick = onMonth,
            label = { Text(filter.start?.let { Formats.monthYear(YearMonth.from(it)) } ?: stringResource(R.string.filter_all_time)) },
        )
        FilterChip(
            selected = filter.projectId != null,
            onClick = onProject,
            label = { Text(state.projects.firstOrNull { it.id == filter.projectId }?.name ?: stringResource(R.string.filter_all_projects), maxLines = 1) },
        )
        if (filter.projectId != null || state.activities.any { it.projectId == null }) {
            FilterChip(
                selected = filter.activityId != null,
                onClick = onActivity,
                label = { Text(state.activities.firstOrNull { it.id == filter.activityId }?.name ?: stringResource(R.string.filter_all_activities), maxLines = 1) },
            )
        }
        FilterChip(
            selected = filter.missingCaptionOnly,
            onClick = { vm.setFilter(filter.copy(missingCaptionOnly = !filter.missingCaptionOnly)) },
            label = { Text(stringResource(R.string.filter_no_caption)) },
        )
        FilterChip(
            selected = filter.missingGpsOnly,
            onClick = { vm.setFilter(filter.copy(missingGpsOnly = !filter.missingGpsOnly)) },
            label = { Text(stringResource(R.string.filter_no_gps)) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(item: PhotoListItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val noCaption = stringResource(R.string.photo_flag_no_caption)
    val noGps = stringResource(R.string.photo_flag_no_gps)
    val description = buildString {
        append(Formats.time(item.photo.captureTime.zoned))
        append(". ")
        append(item.photo.caption.ifBlank { noCaption })
        if (item.photo.location == null) append(". ").append(noGps)
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description; this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        PhotoThumbnail(file = appFile(item.photo.thumbnailPath), contentDescription = null, modifier = Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = Spacing.xs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Formats.time(item.photo.captureTime.zoned).substringBefore(' '), style = FieldStampTheme.mono.small, color = Color.White, modifier = Modifier.weight(1f))
            if (!item.photo.hasCaption) Icon(Icons.Outlined.SpeakerNotesOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            if (item.photo.location == null) Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary))
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

