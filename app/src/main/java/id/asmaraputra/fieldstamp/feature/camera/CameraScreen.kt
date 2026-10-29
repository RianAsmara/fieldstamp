package id.asmaraputra.fieldstamp.feature.camera

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
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
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.core.permissions.PermissionStatus
import id.asmaraputra.fieldstamp.core.permissions.openAppSettings
import id.asmaraputra.fieldstamp.core.permissions.rememberPermission
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
)

class CameraViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<CameraUiState> = combine(c.settings.settings, c.templates.observeAll()) { settings, templates ->
        val template = templates.firstOrNull { it.id == settings.selectedTemplateId }?.takeIf { c.templates.isSourceAvailable(it) }
        CameraUiState(template, template?.let { c.templates.sourceFile(it) }, settings.recordLocation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraUiState())

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
        if (saved && camera.status == PermissionStatus.GRANTED) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.Center).zIndex(1f),
            ) {
                Text(stringResource(R.string.camera_saved), modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md))
            }
        }
        when (camera.status) {
            PermissionStatus.GRANTED -> CameraContent(
                ui = ui,
                gps = gps,
                vm = vm,
                onClose = { nav.popBackStack() },
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
    onGpsClick: () -> Unit,
    onCaptured: (File, CaptureTime, GeoPoint?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by rememberSaveable { mutableStateOf(FlashSetting.AUTO) }
    var capturing by remember { mutableStateOf(false) }
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

        // Top controls
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraIconButton(Icons.Outlined.Close, stringResource(R.string.action_close), onClose)
            Spacer(Modifier.weight(1f))
            GpsChip(gps, onGpsClick)
            Spacer(Modifier.width(Spacing.sm))
            CameraIconButton(
                when (flash) { FlashSetting.AUTO -> Icons.Outlined.FlashAuto; FlashSetting.ON -> Icons.Outlined.FlashOn; FlashSetting.OFF -> Icons.Outlined.FlashOff },
                stringResource(when (flash) { FlashSetting.AUTO -> R.string.camera_flash_auto; FlashSetting.ON -> R.string.camera_flash_on; FlashSetting.OFF -> R.string.camera_flash_off }),
            ) { flash = FlashSetting.entries[(flash.ordinal + 1) % FlashSetting.entries.size] }
        }

        error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.align(Alignment.Center).padding(Spacing.xl)) {
                Text(stringResource(it), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(Spacing.lg))
            }
        }

        // Bottom controls: shutter centered, within thumb reach.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).navigationBarsPadding().padding(vertical = Spacing.xl, horizontal = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ui.template?.name ?: stringResource(R.string.template_basic_name),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val shutterLabel = stringResource(R.string.camera_shutter)
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(FieldStampTheme.extended.capture)
                    .border(4.dp, Color.White, CircleShape)
                    .semantics { contentDescription = shutterLabel }
                    .clickable(enabled = !capturing && error == null) {
                        capturing = true
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
                else Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = FieldStampTheme.extended.onCapture, modifier = Modifier.size(30.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                CameraIconButton(Icons.Outlined.Cameraswitch, stringResource(R.string.camera_switch)) {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }
            }
        }
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
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .heightIn(min = Spacing.touch)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (status is GpsStatus.Fixed) FieldStampTheme.extended.capture else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
