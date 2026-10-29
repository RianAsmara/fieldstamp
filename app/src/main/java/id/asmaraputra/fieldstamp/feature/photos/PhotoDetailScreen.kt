package id.asmaraputra.fieldstamp.feature.photos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil3.compose.SubcomposeAsyncImage
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.UiText
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.app.container
import id.asmaraputra.fieldstamp.app.text
import id.asmaraputra.fieldstamp.core.designsystem.ConfirmDialog
import id.asmaraputra.fieldstamp.core.designsystem.EmptyState
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.LoadingState
import id.asmaraputra.fieldstamp.core.designsystem.MetadataRow
import id.asmaraputra.fieldstamp.core.designsystem.MissingImage
import id.asmaraputra.fieldstamp.core.designsystem.Notice
import id.asmaraputra.fieldstamp.core.designsystem.NoticeTone
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.location.PlaceResult
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.PhotoSource
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.core.storage.InsufficientStorageException
import id.asmaraputra.fieldstamp.feature.common.ProjectActivityFields
import id.asmaraputra.fieldstamp.feature.common.appFile
import id.asmaraputra.fieldstamp.feature.templates.TemplatePickerField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class PhotoDetailState(
    val loaded: Boolean = false,
    val item: PhotoListItem? = null,
    val projects: List<Project> = emptyList(),
    val activities: List<FieldActivity> = emptyList(),
    val templates: List<StampTemplate> = emptyList(),
    val renderedExists: Boolean = false,
    val originalExists: Boolean = false,
)

class PhotoDetailViewModel(private val c: AppContainer, private val photoId: Long) : ViewModel() {
    val state: StateFlow<PhotoDetailState> = combine(
        c.photos.observeItem(photoId),
        c.projects.observeProjects(),
        c.projects.observeActivities(),
        c.templates.observeAll(),
    ) { item, projects, activities, templates ->
        PhotoDetailState(
            loaded = true,
            item = item,
            projects = projects,
            activities = activities,
            templates = templates,
            renderedExists = c.photos.fileExists(item?.photo?.renderedPath),
            originalExists = c.photos.fileExists(item?.photo?.originalPath),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoDetailState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _usage = MutableStateFlow(0)
    val reportUsage: StateFlow<Int> = _usage.asStateFlow()

    fun loadUsage() = viewModelScope.launch { _usage.value = c.photos.reportUsageCount(photoId) }

    fun save(caption: String, category: String, projectId: Long?, activityId: Long?, templateId: Long?, place: String, onDone: () -> Unit, onError: (UiText) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { c.photos.update(photoId, caption, category.ifBlank { null }, projectId, activityId, templateId, place) }
                .onSuccess { onDone() }
                .onFailure { onError(if (it is InsufficientStorageException) text(R.string.error_storage_full) else text(R.string.error_render_photo)) }
            _busy.value = false
        }
    }

    /** Explicit user action, so it runs even when automatic place names are turned off. */
    fun lookupPlace(onResult: (String?, UiText?) -> Unit) = viewModelScope.launch {
        val loc = state.value.item?.photo?.location ?: return@launch onResult(null, text(R.string.place_no_coordinates))
        when (val r = c.places.resolve(loc.latitude, loc.longitude)) {
            is PlaceResult.Found -> onResult(r.name, null)
            PlaceResult.Offline -> onResult(null, text(R.string.place_offline))
            PlaceResult.NotSupported -> onResult(null, text(R.string.place_not_supported))
            PlaceResult.NotFound -> onResult(null, text(R.string.place_not_found))
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        c.photos.delete(photoId)
        onDone()
    }

    fun saveToGallery(path: String, name: String, onResult: (UiText) -> Unit) = viewModelScope.launch {
        runCatching { c.export.saveJpegToGallery(path, name) }
            .onSuccess { onResult(text(R.string.export_saved_gallery)) }
            .onFailure { onResult(text(R.string.export_failed)) }
    }

    fun saveToDocument(path: String, uri: Uri, onResult: (UiText) -> Unit) = viewModelScope.launch {
        runCatching { c.export.copyToDocument(path, uri) }
            .onSuccess { onResult(text(R.string.export_saved_file)) }
            .onFailure { onResult(text(R.string.export_failed)) }
    }
}

@Composable
fun PhotoDetailScreen(nav: NavController, photoId: Long) {
    val vm = appViewModel(key = "photo-$photoId") { c, _ -> PhotoDetailViewModel(c, photoId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val usage by vm.reportUsage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.container
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val show: (UiText) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg.resolve(context)) } }

    val item = state.item
    val photo = item?.photo
    val exportPath = photo?.let { if (!showOriginal && state.renderedExists) it.renderedPath else it.originalPath }?.takeIf { container.files.exists(it) }
    val fileName = photo?.let { "FieldStamp_${Formats.fileStamp(it.captureTime.zoned)}_${it.id}.jpg" }.orEmpty()

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null && exportPath != null) vm.saveToDocument(exportPath, uri, show)
    }

    Scaffold(
        topBar = {
            FsTopBar(
                title = photo?.let { Formats.shortDate(it.captureLocalDate) } ?: stringResource(R.string.photo_detail_title),
                onBack = { nav.popBackStack() },
            ) {
                if (photo != null) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    IconButton(onClick = {
                        if (exportPath == null) show(text(R.string.photo_file_missing))
                        else context.startActivity(container.export.shareIntent(listOf(exportPath), "image/jpeg", resources.getString(R.string.action_share)))
                    }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (container.export.canSaveToGallery) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_save_gallery)) },
                                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    if (exportPath == null) show(text(R.string.photo_file_missing)) else vm.saveToGallery(exportPath, fileName, show)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save_as_file)) },
                            leadingIcon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                            onClick = { menu = false; if (exportPath == null) show(text(R.string.photo_file_missing)) else createDocument.launch(fileName) },
                        )
                        photo.location?.let { loc ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_map)) },
                                leadingIcon = { Icon(Icons.Outlined.Map, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}")
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { show(text(R.string.error_no_map_app)) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; vm.loadUsage(); confirmDelete = true },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.loaded -> LoadingState(Modifier.padding(padding))
            item == null || photo == null -> EmptyState(Icons.Outlined.ImageNotSupported, stringResource(R.string.photo_missing_title), stringResource(R.string.photo_missing_body), Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                val shownPath = if (!showOriginal && photo.renderedPath != null) photo.renderedPath else photo.originalPath
                val shownFile = appFile(shownPath)
                Box(Modifier.fillMaxWidth().height(380.dp).clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    if (shownFile == null || !shownFile.isFile) {
                        MissingImage(Modifier.padding(Spacing.xl))
                    } else {
                        SubcomposeAsyncImage(
                            model = shownFile,
                            contentDescription = photo.caption.ifBlank { stringResource(R.string.photo_no_caption) },
                            contentScale = ContentScale.Fit,
                            loading = { CircularProgressIndicator(Modifier.size(32.dp)) },
                            error = { MissingImage() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                    SegmentedButton(selected = !showOriginal, onClick = { showOriginal = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.photo_view_stamped)) }
                    SegmentedButton(selected = showOriginal, onClick = { showOriginal = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.photo_view_original)) }
                }
                if (!state.originalExists) {
                    Notice(stringResource(R.string.photo_original_missing), NoticeTone.WARNING, Modifier.padding(horizontal = Spacing.gutter))
                }

                Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                    Text(
                        photo.caption.ifBlank { stringResource(R.string.photo_no_caption) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (photo.hasCaption) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FsDivider()
                SectionHeader(stringResource(R.string.photo_metadata_heading))
                Column(Modifier.padding(horizontal = Spacing.gutter)) {
                    MetadataRow(stringResource(R.string.field_time), "${Formats.dayHeading(photo.captureLocalDate)}\n${Formats.time(photo.captureTime.zoned)}", mono = false)
                    MetadataRow(stringResource(R.string.field_time_zone), photo.captureTime.zoneId, mono = true)
                    MetadataRow(stringResource(R.string.field_source), stringResource(if (photo.source == PhotoSource.CAMERA) R.string.source_camera else R.string.source_import))
                    MetadataRow(stringResource(R.string.field_project), item.projectName ?: stringResource(R.string.context_no_project), muted = item.projectName == null)
                    MetadataRow(stringResource(R.string.field_activity), item.activityName ?: stringResource(R.string.context_no_activity), muted = item.activityName == null)
                    photo.category?.let { MetadataRow(stringResource(R.string.field_category), it) }
                    MetadataRow(stringResource(R.string.field_place_name), photo.locationName ?: stringResource(R.string.place_none), muted = photo.locationName == null)
                    val loc = photo.location
                    if (loc != null) {
                        MetadataRow(stringResource(R.string.field_coordinates), Formats.coordinatesWithAccuracy(loc), mono = true)
                        MetadataRow(stringResource(R.string.field_location_time), Formats.time(Instant.ofEpochMilli(loc.capturedAtEpochMillis).atZone(photo.captureTime.zone)), mono = true)
                    } else {
                        MetadataRow(stringResource(R.string.field_coordinates), stringResource(R.string.photo_no_location), muted = true)
                    }
                    MetadataRow(stringResource(R.string.field_template), state.templates.firstOrNull { it.id == photo.templateId }?.name ?: stringResource(R.string.template_basic_name))
                    MetadataRow(stringResource(R.string.field_dimensions), "${photo.widthPx} × ${photo.heightPx} px", mono = true)
                }
                Text(
                    stringResource(R.string.photo_integrity_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.gutter),
                )
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    if (editing && item != null) {
        EditPhotoSheet(
            item = item,
            projects = state.projects,
            activities = state.activities,
            templates = state.templates,
            busy = busy,
            onDismiss = { editing = false },
            onSave = { caption, category, projectId, activityId, templateId, place ->
                vm.save(caption, category, projectId, activityId, templateId, place, onDone = { editing = false }, onError = show)
            },
            onLookupPlace = vm::lookupPlace,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.photo_delete_title, 1, 1),
            body = if (usage > 0) pluralStringResource(R.plurals.photo_delete_used_body, usage, usage) else stringResource(R.string.photo_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete { nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPhotoSheet(
    item: PhotoListItem,
    projects: List<Project>,
    activities: List<FieldActivity>,
    templates: List<StampTemplate>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, Long?, Long?, String) -> Unit,
    onLookupPlace: ((String?, UiText?) -> Unit) -> Unit,
) {
    val photo = item.photo
    var caption by rememberSaveable { mutableStateOf(photo.caption) }
    var category by rememberSaveable { mutableStateOf(photo.category.orEmpty()) }
    var projectId by rememberSaveable { mutableStateOf(photo.projectId) }
    var activityId by rememberSaveable { mutableStateOf(photo.activityId) }
    var templateId by rememberSaveable { mutableStateOf(photo.templateId) }
    var place by rememberSaveable { mutableStateOf(photo.locationName.orEmpty()) }
    var placeSearching by remember { mutableStateOf(false) }
    var placeMessage by remember { mutableStateOf<UiText?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.photo_edit_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.gutter))
            Text(stringResource(R.string.photo_edit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs))
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it.take(1000) },
                label = { Text(stringResource(R.string.field_caption)) },
                minLines = 2,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it.take(60) },
                label = { Text(stringResource(R.string.field_category_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
            )
            OutlinedTextField(
                value = place,
                onValueChange = { place = it.take(160); placeMessage = null },
                label = { Text(stringResource(R.string.field_place_name)) },
                singleLine = true,
                supportingText = placeMessage?.let { msg -> { Text(msg.resolve()) } },
                trailingIcon = {
                    if (placeSearching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (photo.location != null) {
                        IconButton(onClick = {
                            placeSearching = true
                            onLookupPlace { name, error ->
                                placeSearching = false
                                if (name != null) place = name
                                placeMessage = error
                            }
                        }) { Icon(Icons.Outlined.TravelExplore, contentDescription = stringResource(R.string.place_lookup)) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            Spacer(Modifier.height(Spacing.sm))
            ProjectActivityFields(
                projects = projects,
                activities = activities,
                projectId = projectId,
                activityId = activityId,
                onProjectChange = { id ->
                    projectId = id
                    if (activities.none { it.id == activityId && it.projectId == id }) activityId = null
                },
                onActivityChange = { activityId = it },
                onCreateProject = null,
                onCreateActivity = null,
            )
            FsDivider(inset = true)
            TemplatePickerField(templates, templateId) { templateId = it }
            Row(Modifier.fillMaxWidth().padding(Spacing.gutter)) {
                Button(onClick = { onSave(caption, category, projectId, activityId, templateId, place) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(Spacing.touch)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(stringResource(R.string.photo_edit_save))
                }
            }
        }
    }
}
