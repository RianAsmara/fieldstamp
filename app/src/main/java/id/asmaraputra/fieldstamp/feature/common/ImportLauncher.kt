package id.asmaraputra.fieldstamp.feature.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.app.CaptureReviewRoute
import id.asmaraputra.fieldstamp.app.container
import kotlinx.coroutines.launch

/**
 * Opens the system Photo Picker (no storage permission needed) and routes the chosen image to the
 * capture review screen. Returns the function that launches the picker.
 */
@Composable
fun rememberPhotoImport(nav: NavController, onError: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.container.photos.stageImport(uri) }
                .onSuccess { staged ->
                    nav.navigate(
                        CaptureReviewRoute(
                            tempName = staged.file.name,
                            capturedAt = staged.captureTime.epochMillis,
                            zoneId = staged.captureTime.zoneId,
                            imported = true,
                            timeFromFile = staged.timeFromFile,
                            lat = staged.location?.latitude?.toString().orEmpty(),
                            lon = staged.location?.longitude?.toString().orEmpty(),
                            locationAt = staged.location?.capturedAtEpochMillis ?: 0,
                        ),
                    )
                }
                .onFailure { onError() }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
