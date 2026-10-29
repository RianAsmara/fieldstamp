package id.asmaraputra.fieldstamp.feature.camera

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.toRoute
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.CaptureReviewRoute
import id.asmaraputra.fieldstamp.app.UiText
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.app.text
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.MetadataRow
import id.asmaraputra.fieldstamp.core.designsystem.Notice
import id.asmaraputra.fieldstamp.core.designsystem.NoticeTone
import id.asmaraputra.fieldstamp.core.designsystem.ConfirmDialog
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.designsystem.SwitchRow
import id.asmaraputra.fieldstamp.core.location.LocationResult
import id.asmaraputra.fieldstamp.core.location.PlaceResult
import id.asmaraputra.fieldstamp.data.PhotoRepository
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.PhotoSource
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.core.storage.InsufficientStorageException
import id.asmaraputra.fieldstamp.data.CaptureDraft
import id.asmaraputra.fieldstamp.feature.common.ProjectActivityFields
import id.asmaraputra.fieldstamp.feature.templates.TemplatePickerField
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface ReviewLocation {
    data object None : ReviewLocation
    data object Searching : ReviewLocation
    data class Found(val point: GeoPoint) : ReviewLocation
    data class Failed(val reason: UiText) : ReviewLocation
}

data class ReviewForm(
    val caption: String = "",
    val category: String = "",
    val projectId: Long? = null,
    val activityId: Long? = null,
    val templateId: Long? = null,
    val includeLocation: Boolean = true,
    val locationName: String = "",
    /** True once the user typed a place name; automatic lookup then never overwrites it. */
    val placeEdited: Boolean = false,
)

sealed interface PlaceStatus {
    data object Idle : PlaceStatus
    data object Searching : PlaceStatus
    data object Found : PlaceStatus
    data class Failed(val reason: UiText) : PlaceStatus
}

data class ReviewLookups(
    val projects: List<Project> = emptyList(),
    val activities: List<FieldActivity> = emptyList(),
    val templates: List<StampTemplate> = emptyList(),
)

class CaptureReviewViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<CaptureReviewRoute>()
    val imported = route.imported
    val timeFromFile = route.timeFromFile
    val captureTime = CaptureTime(route.capturedAt, route.zoneId)
    val file: File = File(c.files.dir(id.asmaraputra.fieldstamp.core.storage.FileStore.Area.TMP), route.tempName)
    val fileMissing: Boolean get() = !file.isFile

    private val _form = MutableStateFlow(
        ReviewForm(
            caption = handle["caption"] ?: "",
            category = handle["category"] ?: "",
            projectId = handle.get<Long>("projectId"),
            activityId = handle.get<Long>("activityId"),
            templateId = handle.get<Long>("templateId"),
            includeLocation = handle["includeLocation"] ?: true,
            locationName = handle["locationName"] ?: "",
            placeEdited = handle["placeEdited"] ?: false,
        ),
    )

    private val _place = MutableStateFlow<PlaceStatus>(PlaceStatus.Idle)
    val place: StateFlow<PlaceStatus> = _place.asStateFlow()
    val form: StateFlow<ReviewForm> = _form.asStateFlow()

    private val _location = MutableStateFlow<ReviewLocation>(initialLocation())
    val location: StateFlow<ReviewLocation> = _location.asStateFlow()

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _previewError = MutableStateFlow(false)
    val previewError: StateFlow<Boolean> = _previewError.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    val lookups: StateFlow<ReviewLookups> = combine(c.projects.observeProjects(), c.projects.observeActivities(), c.templates.observeAll(), ::ReviewLookups)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewLookups())

    init {
        viewModelScope.launch {
            if (!handle.contains("initialized")) {
                val settings = c.settings.current()
                update { it.copy(projectId = settings.currentProjectId, activityId = settings.currentActivityId, templateId = settings.selectedTemplateId) }
                handle["initialized"] = true
            }
            // Camera captures without a recent fix try once more; imports never use the current location,
            // because the photo may have been taken somewhere else.
            if (!imported && _location.value == ReviewLocation.None && c.settings.current().recordLocation) fetchLocation()
        }
        // Look up a place name whenever a fix becomes available (camera fix or the import's own EXIF GPS).
        viewModelScope.launch {
            location.collect { loc ->
                if (loc is ReviewLocation.Found && !form.value.placeEdited && form.value.locationName.isBlank() && c.settings.current().autoPlaceName) {
                    lookupPlace(loc.point)
                }
            }
        }
        renderPreviews()
    }

    private fun initialLocation(): ReviewLocation {
        val lat = route.lat.toDoubleOrNull()
        val lon = route.lon.toDoubleOrNull()
        return if (lat != null && lon != null) {
            ReviewLocation.Found(GeoPoint(lat, lon, route.accuracy.toFloatOrNull(), route.locationAt.takeIf { it > 0 } ?: route.capturedAt))
        } else {
            ReviewLocation.None
        }
    }

    fun fetchLocation() {
        if (imported) return
        _location.value = ReviewLocation.Searching
        viewModelScope.launch {
            _location.value = when (val r = c.location.currentLocation()) {
                is LocationResult.Fix -> ReviewLocation.Found(r.point)
                LocationResult.PermissionDenied -> ReviewLocation.Failed(text(R.string.location_permission_denied))
                LocationResult.ServicesDisabled -> ReviewLocation.Failed(text(R.string.location_services_disabled))
                LocationResult.Unavailable -> ReviewLocation.Failed(text(R.string.location_unavailable))
            }
        }
    }

    private suspend fun lookupPlace(point: GeoPoint) {
        _place.value = PlaceStatus.Searching
        _place.value = when (val r = c.places.resolve(point.latitude, point.longitude)) {
            is PlaceResult.Found -> {
                if (!form.value.placeEdited) update { it.copy(locationName = r.name) }
                PlaceStatus.Found
            }
            PlaceResult.Offline -> PlaceStatus.Failed(text(R.string.place_offline))
            PlaceResult.NotSupported -> PlaceStatus.Failed(text(R.string.place_not_supported))
            PlaceResult.NotFound -> PlaceStatus.Failed(text(R.string.place_not_found))
        }
    }

    fun retryPlace() {
        val point = (location.value as? ReviewLocation.Found)?.point ?: return
        update { it.copy(placeEdited = false, locationName = "") }
        viewModelScope.launch { lookupPlace(point) }
    }

    fun setLocationName(v: String) = update { it.copy(locationName = v.take(PhotoRepository.MAX_PLACE), placeEdited = true) }

    private fun update(block: (ReviewForm) -> ReviewForm) {
        val next = block(_form.value)
        _form.value = next
        handle["caption"] = next.caption
        handle["category"] = next.category
        handle["projectId"] = next.projectId
        handle["activityId"] = next.activityId
        handle["templateId"] = next.templateId
        handle["includeLocation"] = next.includeLocation
        handle["locationName"] = next.locationName
        handle["placeEdited"] = next.placeEdited
    }

    fun setCaption(v: String) = update { it.copy(caption = v.take(MAX_CAPTION)) }
    fun setCategory(v: String) = update { it.copy(category = v.take(60)) }
    fun setProject(id: Long?) = update { f ->
        val activity = lookups.value.activities.firstOrNull { it.id == f.activityId }
        f.copy(projectId = id, activityId = activity?.takeIf { it.projectId == id }?.id)
    }
    fun setActivity(id: Long?) = update { it.copy(activityId = id) }
    fun setTemplate(id: Long?) = update { it.copy(templateId = id) }
    fun setIncludeLocation(v: Boolean) = update { it.copy(includeLocation = v) }

    fun createProject(name: String) = viewModelScope.launch { setProject(c.projects.createProject(name, "")) }
    fun createActivity(name: String) = viewModelScope.launch { setActivity(c.projects.createActivity(_form.value.projectId, name, "")) }

    private fun effectiveLocation(): GeoPoint? =
        (location.value as? ReviewLocation.Found)?.point?.takeIf { form.value.includeLocation }

    /** Turning location off for a photo also drops its place name; a typed name without GPS is kept. */
    private fun effectiveLocationName(): String? {
        val hasFix = location.value is ReviewLocation.Found
        return form.value.locationName.trim().takeIf { it.isNotEmpty() && (!hasFix || form.value.includeLocation) }
    }

    @OptIn(FlowPreview::class)
    private fun renderPreviews() = viewModelScope.launch {
        combine(form, location, lookups) { f, _, _ -> f }.debounce(250).collectLatest { f ->
            if (!file.isFile) return@collectLatest
            runCatching {
                val stamp = c.photos.resolveStamp(f.templateId)
                val data = c.photos.stampData(captureTime, f.projectId, f.activityId, f.caption, f.category.ifBlank { null }, effectiveLocation(), effectiveLocationName())
                c.processor.preview(file, stamp.templatePath, stamp.config, data)
            }.onSuccess { _preview.value = it; _previewError.value = false }
                .onFailure { _previewError.value = true }
        }
    }

    /** Saves and remembers the chosen project/activity/template for the next capture. */
    fun save(onSaved: () -> Unit, onError: (UiText) -> Unit) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val f = form.value
            runCatching {
                c.photos.save(
                    CaptureDraft(
                        originalTemp = file,
                        source = if (imported) PhotoSource.IMPORT else PhotoSource.CAMERA,
                        captureTime = captureTime,
                        location = effectiveLocation(),
                        locationName = effectiveLocationName(),
                        projectId = f.projectId,
                        activityId = f.activityId,
                        caption = f.caption,
                        category = f.category.ifBlank { null },
                        templateId = f.templateId,
                    ),
                )
                c.settings.setCaptureContext(f.projectId, f.activityId)
                c.settings.setSelectedTemplate(f.templateId)
            }.onSuccess { onSaved() }.onFailure { e ->
                _saving.value = false
                onError(if (e is InsufficientStorageException) text(R.string.error_storage_full) else text(R.string.error_save_photo))
            }
        }
    }

    fun discard() { file.delete() }

    companion object {
        const val MAX_CAPTION = 1000
    }
}

/** Fixed so portrait and landscape previews never push or overlap the form. */
private val PREVIEW_HEIGHT = 340.dp
private val PREVIEW_COMPACT_HEIGHT = 72.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureReviewScreen(nav: NavController) {
    val vm = appViewModel { c, h -> CaptureReviewViewModel(c, h) }
    val form by vm.form.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val previewError by vm.previewError.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val lookups by vm.lookups.collectAsStateWithLifecycle()
    val place by vm.place.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val leave = {
        vm.discard()
        nav.popBackStack()
    }
    BackHandler(enabled = !saving) { confirmDiscard = true }

    val save = {
        vm.save(
            onSaved = {
                nav.previousBackStackEntry?.savedStateHandle?.set("photoSaved", true)
                nav.popBackStack()
            },
            onError = { msg -> scope.launch { snackbar.showSnackbar(msg.resolve(context)) } },
        )
    }
    val canSave = !saving && !vm.fileMissing
    // While typing, Save stays reachable in the top bar and in a toolbar riding on the keyboard.
    val typing = WindowInsets.isImeVisible

    Scaffold(
        topBar = {
            FsTopBar(stringResource(if (vm.imported) R.string.review_title_import else R.string.review_title), onBack = { confirmDiscard = true }) {
                if (typing) TextButton(onClick = save, enabled = canSave) { Text(stringResource(R.string.action_save)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.gutter, vertical = if (typing) Spacing.sm else Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                ) {
                    if (!typing) {
                        OutlinedButton(onClick = { confirmDiscard = true }, enabled = !saving, modifier = Modifier.weight(1f).height(Spacing.touch)) {
                            Text(stringResource(if (vm.imported) R.string.action_cancel else R.string.action_retake))
                        }
                    }
                    Button(
                        onClick = save,
                        enabled = canSave,
                        modifier = (if (typing) Modifier else Modifier.weight(1f)).height(Spacing.touch),
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else {
                            if (typing) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Spacing.xs))
                            }
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (vm.fileMissing) {
            Column(Modifier.padding(padding).padding(Spacing.gutter)) {
                Notice(stringResource(R.string.review_file_missing), NoticeTone.ERROR)
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Shrinks to a compact strip while typing so the field being edited stays in view.
            Box(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .height(if (typing) PREVIEW_COMPACT_HEIGHT else PREVIEW_HEIGHT)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = preview
                when {
                    bmp != null -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.review_preview_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    previewError -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xl)) {
                        Icon(Icons.Outlined.BrokenImage, contentDescription = null)
                        Text(stringResource(R.string.review_preview_failed), style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> CircularProgressIndicator()
                }
            }

            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md)) {
                MetadataRow(stringResource(R.string.field_time), Formats.stampDateTime(vm.captureTime), mono = true)
                Text(
                    stringResource(if (vm.imported && !vm.timeFromFile) R.string.review_time_import_fallback else if (vm.imported) R.string.review_time_from_file else R.string.review_time_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (vm.imported && !vm.timeFromFile) FieldStampTheme.extended.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                LocationBlock(location, form.includeLocation, vm.imported, onRetry = vm::fetchLocation)
            }
            if (location is ReviewLocation.Found) {
                SwitchRow(
                    title = stringResource(R.string.review_include_location),
                    supporting = stringResource(R.string.review_include_location_hint),
                    checked = form.includeLocation,
                    onCheckedChange = vm::setIncludeLocation,
                )
            }
            PlaceNameField(
                value = form.locationName,
                status = place,
                enabled = location !is ReviewLocation.Found || form.includeLocation,
                canLookup = location is ReviewLocation.Found,
                onValueChange = vm::setLocationName,
                onLookup = vm::retryPlace,
            )
            FsDivider()

            OutlinedTextField(
                value = form.caption,
                onValueChange = vm::setCaption,
                label = { Text(stringResource(R.string.field_caption)) },
                supportingText = { Text("${form.caption.length}/${CaptureReviewViewModel.MAX_CAPTION}") },
                minLines = 2,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
            OutlinedTextField(
                value = form.category,
                onValueChange = vm::setCategory,
                label = { Text(stringResource(R.string.field_category_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
            )
            Spacer(Modifier.height(Spacing.sm))
            ProjectActivityFields(
                projects = lookups.projects,
                activities = lookups.activities,
                projectId = form.projectId,
                activityId = form.activityId,
                onProjectChange = vm::setProject,
                onActivityChange = vm::setActivity,
                onCreateProject = vm::createProject,
                onCreateActivity = vm::createActivity,
            )
            FsDivider(inset = true)
            TemplatePickerField(templates = lookups.templates, selectedId = form.templateId, onSelect = vm::setTemplate)
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.review_discard_title),
            body = stringResource(R.string.review_discard_body),
            confirmLabel = stringResource(R.string.action_discard),
            destructive = true,
            onConfirm = { confirmDiscard = false; leave() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun LocationBlock(location: ReviewLocation, include: Boolean, imported: Boolean, onRetry: () -> Unit) {
    val label = stringResource(R.string.field_coordinates)
    when (location) {
        is ReviewLocation.Found -> MetadataRow(label, Formats.coordinatesWithAccuracy(location.point), mono = true, muted = !include)
        ReviewLocation.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.gps_searching), style = MaterialTheme.typography.bodyMedium)
        }
        ReviewLocation.None -> Column {
            MetadataRow(label, stringResource(if (imported) R.string.review_location_none_import else R.string.review_location_none), muted = true)
            if (!imported) TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry_location)) }
        }
        is ReviewLocation.Failed -> Column {
            MetadataRow(label, location.reason.resolve(), muted = true)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry_location)) }
        }
    }
}

@Composable
private fun PlaceNameField(
    value: String,
    status: PlaceStatus,
    enabled: Boolean,
    canLookup: Boolean,
    onValueChange: (String) -> Unit,
    onLookup: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.field_place_name)) },
        placeholder = { Text(stringResource(R.string.place_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        trailingIcon = {
            when {
                status == PlaceStatus.Searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                canLookup && enabled -> IconButton(onClick = onLookup) {
                    Icon(Icons.Outlined.TravelExplore, contentDescription = stringResource(R.string.place_lookup))
                }
            }
        },
        supportingText = {
            Text(
                when (status) {
                    PlaceStatus.Searching -> stringResource(R.string.place_searching)
                    is PlaceStatus.Failed -> status.reason.resolve()
                    PlaceStatus.Found -> stringResource(R.string.place_found_hint)
                    PlaceStatus.Idle -> stringResource(R.string.place_manual_hint)
                },
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
    )
}
