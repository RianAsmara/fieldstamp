package id.asmaraputra.fieldstamp.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import id.asmaraputra.fieldstamp.app.container
import java.io.File

/** Resolves an app-relative path to a File for image loading; null when there is no path. */
@Composable
fun appFile(relativePath: String?): File? {
    val files = LocalContext.current.container.files
    return remember(relativePath) { relativePath?.let { runCatching { files.resolve(it) }.getOrNull() } }
}
