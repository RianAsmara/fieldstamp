package id.asmaraputra.fieldstamp.core.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import id.asmaraputra.fieldstamp.app.findActivity

enum class PermissionStatus { GRANTED, NOT_REQUESTED, DENIED, PERMANENTLY_DENIED }

@Stable
class PermissionController(
    val status: PermissionStatus,
    private val launch: () -> Unit,
) {
    val granted: Boolean get() = status == PermissionStatus.GRANTED
    fun request() = launch()
}

/**
 * Tracks a runtime permission group. The group counts as granted when any permission in it is granted
 * (so approximate location alone is enough). Re-checks on resume, so returning from system settings works.
 */
@Composable
fun rememberPermission(vararg permissions: String, onResult: (Boolean) -> Unit = {}): PermissionController {
    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf(currentStatus(context, permissions, requested)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = currentStatus(context, permissions, requested) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        requested = true
        status = currentStatus(context, permissions, true)
        onResult(result.values.any { it })
    }
    return PermissionController(status) { launcher.launch(arrayOf(*permissions)) }
}

private fun currentStatus(context: Context, permissions: Array<out String>, requested: Boolean): PermissionStatus {
    if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) return PermissionStatus.GRANTED
    if (!requested) return PermissionStatus.NOT_REQUESTED
    val activity = context.findActivity() ?: return PermissionStatus.DENIED
    val canAskAgain = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    return if (canAskAgain) PermissionStatus.DENIED else PermissionStatus.PERMANENTLY_DENIED
}

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun Context.openLocationSettings() {
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
