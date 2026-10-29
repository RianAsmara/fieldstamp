package id.asmaraputra.fieldstamp.feature.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import id.asmaraputra.fieldstamp.app.PhotoDetailRoute
import id.asmaraputra.fieldstamp.core.designsystem.Motion
import id.asmaraputra.fieldstamp.core.designsystem.PhotoThumbnail
import id.asmaraputra.fieldstamp.core.designsystem.pressScale
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.feature.common.appFile
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import android.Manifest
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.CaptureReviewRoute
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.location.LocationResult
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.core.permissions.PermissionStatus
import id.asmaraputra.fieldstamp.core.permissions.openAppSettings
import id.asmaraputra.fieldstamp.core.permissions.rememberPermission
import id.asmaraputra.fieldstamp.feature.common.OptionSheet
import id.asmaraputra.fieldstamp.feature.common.PickerOption
import id.asmaraputra.fieldstamp.feature.common.ProjectActivityFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

sealed interface GpsStatus {
    data object Off : GpsStatus
    data object NoPermission : GpsStatus
    data object ServicesDisabled : GpsStatus
    data object Searching : GpsStatus
    data object Unavailable : GpsStatus
    data class Fixed(val point: GeoPoint) : GpsStatus
}

data class CameraUiState(
    val template: StampTemplate? = null,
    val templateFile: File? = null,
    val recordLocation: Boolean = true,
    val templates: List<StampTemplate> = emptyList(),
    /** "Project · Activity" the next photo is filed under; null when neither is set. */
    val context: String? = null,
    val projects: List<Project> = emptyList(),
    val activities: List<FieldActivity> = emptyList(),
    val projectId: Long? = null,
    val activityId: Long? = null,
)

class CameraViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<CameraUiState> = combine(
        c.settings.settings,
        c.templates.observeAll(),
        c.projects.observeProjects(),
        c.projects.observeActivities(),
    ) { settings, templates, projects, activities ->
        val template = templates.firstOrNull { it.id == settings.selectedTemplateId }?.takeIf { c.templates.isSourceAvailable(it) }
        val context = listOfNotNull(
            projects.firstOrNull { it.id == settings.currentProjectId }?.name,
            activities.firstOrNull { it.id == settings.currentActivityId }?.name,
        ).joinToString(" · ").ifEmpty { null }
        CameraUiState(
            template, template?.let { c.templates.sourceFile(it) }, settings.recordLocation, templates, context,
            projects, activities, settings.currentProjectId, settings.currentActivityId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraUiState())

    /** Most recent photo of today, shown in the corner like a camera's last-shot preview. */
    val latest: StateFlow<PhotoListItem?> = c.photos.observeDay(LocalDate.now()).map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTemplate(id: Long?) = viewModelScope.launch { c.settings.setSelectedTemplate(id) }

    fun setProject(id: Long?) = viewModelScope.launch {
        // Changing the project clears an activity that belongs to a different project.
        val activity = ui.value.activities.firstOrNull { it.id == ui.value.activityId }
        c.settings.setCaptureContext(id, activity?.takeIf { it.projectId == id }?.id)
    }

    fun setActivity(id: Long?) = viewModelScope.launch { c.settings.setCaptureContext(ui.value.projectId, id) }

    fun createProject(name: String) = viewModelScope.launch {
        val id = c.projects.createProject(name, "")
        c.settings.setCaptureContext(id, null)
    }

    fun createActivity(name: String) = viewModelScope.launch {
        val id = c.projects.createActivity(ui.value.projectId, name, "")
        c.settings.setCaptureContext(ui.value.projectId, id)
    }

    private val _gps = MutableStateFlow<GpsStatus>(GpsStatus.Searching)
    val gps: StateFlow<GpsStatus> = _gps.asStateFlow()

    fun refreshLocation(enabled: Boolean) {
        if (!enabled) { _gps.value = GpsStatus.Off; return }
        if (!c.location.hasAnyPermission()) { _gps.value = GpsStatus.NoPermission; return }
        _gps.value = GpsStatus.Searching
        viewModelScope.launch {
            _gps.value = when (val r = c.location.currentLocation()) {
                is LocationResult.Fix -> GpsStatus.Fixed(r.point)
                LocationResult.PermissionDenied -> GpsStatus.NoPermission
                LocationResult.ServicesDisabled -> GpsStatus.ServicesDisabled
                LocationResult.Unavailable -> GpsStatus.Unavailable
            }
        }
    }

    fun setRecordLocation(value: Boolean) = viewModelScope.launch { c.settings.setRecordLocation(value) }

    fun newCaptureFile(): File = c.files.newTempFile("jpg")

    /** Fix to attach to a capture: only a real, recent device fix. */
    fun fixForCapture(now: Long): GeoPoint? =
        (gps.value as? GpsStatus.Fixed)?.point?.takeIf { now - it.capturedAtEpochMillis <= 2 * 60_000L }
}

private enum class FlashSetting(val mode: Int) { AUTO(ImageCapture.FLASH_MODE_AUTO), ON(ImageCapture.FLASH_MODE_ON), OFF(ImageCapture.FLASH_MODE_OFF) }

@Composable
fun CameraScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> CameraViewModel(c) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val gps by vm.gps.collectAsStateWithLifecycle()
    val camera = rememberPermission(Manifest.permission.CAMERA)
    val location = rememberPermission(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) { vm.refreshLocation(ui.recordLocation) }

    LaunchedEffect(camera.status) {
        if (camera.status == PermissionStatus.NOT_REQUESTED) camera.request()
    }
    LaunchedEffect(ui.recordLocation, location.granted) { vm.refreshLocation(ui.recordLocation) }

    // Set by the review screen after a successful save; shown briefly so rapid capture has feedback.
    val savedFlag = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow("photoSaved", false)
    val saved by (savedFlag ?: MutableStateFlow(false)).collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            kotlinx.coroutines.delay(2_000)
            nav.currentBackStackEntry?.savedStateHandle?.set("photoSaved", false)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Drops in under the top controls, then leaves the way it came.
        AnimatedVisibility(
            visible = saved && camera.status == PermissionStatus.GRANTED,
            enter = slideInVertically(tween(Motion.MEDIUM, easing = Motion.enter)) { -it } + fadeIn(tween(Motion.SHORT)),
            exit = slideOutVertically(tween(Motion.SHORT, easing = Motion.exit)) { -it } + fadeOut(tween(Motion.SHORT)),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = TOAST_TOP).zIndex(1f),
        ) {
            SavedToast()
        }
        when (camera.status) {
            PermissionStatus.GRANTED -> CameraContent(
                ui = ui,
                gps = gps,
                vm = vm,
                onClose = { nav.popBackStack() },
                onOpenPhoto = { nav.navigate(PhotoDetailRoute(it)) },
                onGpsClick = {
                    when {
                        !ui.recordLocation -> vm.setRecordLocation(true)
                        !location.granted -> location.request()
                        else -> vm.setRecordLocation(false)
                    }
                },
                onCaptured = { file, time, fix ->
                    nav.navigate(
                        CaptureReviewRoute(
                            tempName = file.name,
                            capturedAt = time.epochMillis,
                            zoneId = time.zoneId,
                            imported = false,
                            lat = fix?.latitude?.toString().orEmpty(),
                            lon = fix?.longitude?.toString().orEmpty(),
                            accuracy = fix?.accuracyMeters?.toString().orEmpty(),
                            locationAt = fix?.capturedAtEpochMillis ?: 0,
                        ),
                    )
                },
            )
            PermissionStatus.NOT_REQUESTED -> Unit
            else -> CameraPermissionDenied(
                permanently = camera.status == PermissionStatus.PERMANENTLY_DENIED,
                onRequest = camera::request,
                onClose = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun CameraPermissionDenied(permanently: Boolean, onRequest: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(Icons.Outlined.NoPhotography, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(if (permanently) R.string.camera_permission_settings_body else R.string.camera_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { if (permanently) context.openAppSettings() else onRequest() }) {
                Text(stringResource(if (permanently) R.string.action_open_settings else R.string.action_allow_camera))
            }
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        }
    }
}

@Composable
private fun CameraContent(
    ui: CameraUiState,
    gps: GpsStatus,
    vm: CameraViewModel,
    onClose: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onGpsClick: () -> Unit,
    onCaptured: (File, CaptureTime, GeoPoint?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by rememberSaveable { mutableStateOf(FlashSetting.AUTO) }
    var capturing by remember { mutableStateOf(false) }
    var shots by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val latest by vm.latest.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<Int?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    imageCapture.flashMode = flash.mode

    // Binds to the screen's lifecycle: the camera is released as soon as this screen stops.
    DisposableEffect(lensFacing, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                error = null
            }.onFailure { error = R.string.camera_error_init }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }

    var pickContext by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Template guide: the PNG drawn at its own aspect ratio so the user can frame the shot.
        if (ui.templateFile != null && ui.template != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // aspectRatio picks the largest box of this ratio that fits the screen.
                val ratio = ui.template.widthPx.toFloat() / ui.template.heightPx
                AsyncImage(
                    model = ui.templateFile,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .aspectRatio(ratio)
                        .border(1.dp, Color.White.copy(alpha = 0.6f)),
                )
            }
        }

        // Shutter flash: a brief white veil over the preview, the classic "photo taken" cue.
        val shutterFlash = remember { Animatable(0f) }
        LaunchedEffect(shots) {
            if (shots == 0) return@LaunchedEffect
            shutterFlash.snapTo(0.7f)
            shutterFlash.animateTo(0f, tween(Motion.SHORT, easing = Motion.exit))
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterFlash.value }.background(Color.White))

        // Top controls on a solid scrim (not blur) so white text stays readable over any scene.
        Column(Modifier.fillMaxWidth().background(SCRIM).statusBarsPadding().padding(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CameraIconButton(Icons.Outlined.Close, stringResource(R.string.action_close), onClose)
            Spacer(Modifier.weight(1f))
            GpsChip(gps, onGpsClick)
            Spacer(Modifier.width(Spacing.sm))
            CameraIconButton(
                when (flash) { FlashSetting.AUTO -> Icons.Outlined.FlashAuto; FlashSetting.ON -> Icons.Outlined.FlashOn; FlashSetting.OFF -> Icons.Outlined.FlashOff },
                stringResource(when (flash) { FlashSetting.AUTO -> R.string.camera_flash_auto; FlashSetting.ON -> R.string.camera_flash_on; FlashSetting.OFF -> R.string.camera_flash_off }),
            ) { flash = FlashSetting.entries[(flash.ordinal + 1) % FlashSetting.entries.size] }
        }
        ContextChip(ui.context ?: stringResource(R.string.context_no_project)) { pickContext = true }
        }

        error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.align(Alignment.Center).padding(Spacing.xl)) {
                Text(stringResource(it), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(Spacing.lg))
            }
        }

        // Bottom controls: template switcher above a centered shutter, within thumb reach.
        var pickTemplate by rememberSaveable { mutableStateOf(false) }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(SCRIM).navigationBarsPadding().padding(bottom = Spacing.xl, top = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TemplateChip(ui.template?.name ?: stringResource(R.string.template_basic_name)) { pickTemplate = true }
            Spacer(Modifier.height(Spacing.lg))
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    LatestShot(latest, onOpenPhoto)
                }
                val shutterLabel = stringResource(R.string.camera_shutter)
                val shutterInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(SHUTTER)
                        .pressScale(shutterInteraction, pressedScale = 0.92f)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(FieldStampTheme.extended.capture)
                        .semantics { contentDescription = shutterLabel }
                        .clickable(interactionSource = shutterInteraction, indication = LocalIndication.current, enabled = !capturing && error == null) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            capturing = true
                            shots++
                            val time = CaptureTime.now()
                            val fix = vm.fixForCapture(time.epochMillis)
                            val file = vm.newCaptureFile()
                            imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        capturing = false
                                        onCaptured(file, time, fix)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        capturing = false
                                        file.delete()
                                        error = R.string.camera_error_capture
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (capturing) CircularProgressIndicator(color = FieldStampTheme.extended.onCapture, modifier = Modifier.size(28.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    CameraIconButton(Icons.Outlined.Cameraswitch, stringResource(R.string.camera_switch)) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }
                }
            }
        }
        if (pickTemplate) {
            OptionSheet(
                title = stringResource(R.string.field_template),
                options = listOf(PickerOption(null, stringResource(R.string.template_basic_name), stringResource(R.string.template_basic_desc))) +
                    ui.templates.map { PickerOption(it.id, it.name, "${it.widthPx} × ${it.heightPx} px") },
                selectedId = ui.template?.id,
                onSelect = { vm.setTemplate(it); pickTemplate = false },
                onDismiss = { pickTemplate = false },
            )
        }
        if (pickContext) ContextSheet(ui, vm) { pickContext = false }
    }
}

/** Project/activity switcher so the next photo can be refiled without leaving the camera. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(ui: CameraUiState, vm: CameraViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.lg)) {
            Text(
                stringResource(R.string.camera_context_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
            ProjectActivityFields(
                projects = ui.projects,
                activities = ui.activities,
                projectId = ui.projectId,
                activityId = ui.activityId,
                onProjectChange = vm::setProject,
                onActivityChange = vm::setActivity,
                onCreateProject = vm::createProject,
                onCreateActivity = vm::createActivity,
            )
        }
    }
}

@Composable
private fun ContextChip(label: String, onClick: () -> Unit) {
    val description = stringResource(R.string.camera_change_context, label)
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = Spacing.touch)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwapText(label, FieldStampTheme.mono.small, Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(Spacing.xs))
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.6f)
private val SHUTTER = 84.dp

@Composable
private fun TemplateChip(name: String, onClick: () -> Unit) {
    val description = stringResource(R.string.camera_change_template, name)
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, Color.White.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = Spacing.touch)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        SwapText(name, MaterialTheme.typography.bodyMedium, Modifier.widthIn(max = 220.dp))
        Spacer(Modifier.width(Spacing.xs))
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CameraIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
        modifier = Modifier.size(Spacing.touch),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun GpsChip(status: GpsStatus, onClick: () -> Unit) {
    val (icon, label) = when (status) {
        GpsStatus.Off -> Icons.Outlined.LocationOff to stringResource(R.string.gps_off)
        GpsStatus.NoPermission -> Icons.Outlined.LocationOff to stringResource(R.string.gps_no_permission)
        GpsStatus.ServicesDisabled -> Icons.Outlined.LocationOff to stringResource(R.string.gps_disabled)
        GpsStatus.Searching -> Icons.Outlined.LocationOn to stringResource(R.string.gps_searching)
        GpsStatus.Unavailable -> Icons.Outlined.LocationOff to stringResource(R.string.gps_unavailable)
        is GpsStatus.Fixed -> Icons.Outlined.LocationOn to (status.point.accuracyMeters?.let { stringResource(R.string.gps_fixed_accuracy, it.roundToInt()) } ?: stringResource(R.string.gps_fixed))
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .heightIn(min = Spacing.touch)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color supports the text (accuracy in metres); it never carries the meaning alone.
        val target = when {
            status !is GpsStatus.Fixed -> Color.White
            (status.point.accuracyMeters ?: Float.MAX_VALUE) <= GOOD_ACCURACY_M -> GPS_GOOD
            else -> FieldStampTheme.extended.capture
        }
        val tint by animateColorAsState(target, tween(Motion.MEDIUM), label = "gpsTint")
        // While searching the pin pulses, so a waiting state never looks frozen.
        val pulse = if (status == GpsStatus.Searching) {
            val t = rememberInfiniteTransition(label = "gpsPulse")
            t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(700, easing = Motion.standard), RepeatMode.Reverse), label = "gpsPulseAlpha")
        } else null
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).graphicsLayer { alpha = pulse?.value ?: 1f })
        Spacer(Modifier.width(Spacing.xs))
        SwapText(label, FieldStampTheme.mono.small, Modifier.animateContentSize(tween(Motion.SHORT)))
    }
}

private const val GOOD_ACCURACY_M = 20f
private val GPS_GOOD = Color(0xFF6FD3A0)

private val TOAST_TOP = 112.dp
private val LATEST_SIZE = 52.dp

/** White chip text that slides to its new value, so a changed project or GPS state is noticed. */
@Composable
private fun SwapText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(Motion.SHORT, easing = Motion.enter)) { it / 2 } + fadeIn(tween(Motion.SHORT)))
                .togetherWith(slideOutVertically(tween(Motion.INSTANT, easing = Motion.exit)) { -it / 2 } + fadeOut(tween(Motion.INSTANT)))
                .using(SizeTransform(clip = false))
        },
        label = "chipText",
        modifier = modifier,
    ) { value ->
        Text(value, color = Color.White, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Last saved photo; a new one pops in, so each saved shot visibly lands somewhere. */
@Composable
private fun LatestShot(latest: PhotoListItem?, onOpen: (Long) -> Unit) {
    AnimatedContent(
        targetState = latest,
        contentKey = { it?.photo?.id },
        transitionSpec = {
            (scaleIn(Motion.press(), initialScale = 0.6f) + fadeIn(tween(Motion.SHORT)))
                .togetherWith(fadeOut(tween(Motion.SHORT)))
        },
        label = "latestShot",
    ) { item ->
        if (item == null) {
            Spacer(Modifier.size(LATEST_SIZE))
        } else {
            val description = stringResource(R.string.camera_open_latest)
            PhotoThumbnail(
                file = appFile(item.photo.thumbnailPath),
                contentDescription = description,
                modifier = Modifier
                    .size(LATEST_SIZE)
                    .border(2.dp, Color.White, MaterialTheme.shapes.small)
                    .clickable { onOpen(item.photo.id) },
            )
        }
    }
}

@Composable
private fun SavedToast() {
    Surface(color = Color.White, contentColor = Color(0xFF1A1F24), shape = CircleShape, shadowElevation = 6.dp) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF1E6B3A), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.camera_saved), style = MaterialTheme.typography.labelLarge)
        }
    }
}
