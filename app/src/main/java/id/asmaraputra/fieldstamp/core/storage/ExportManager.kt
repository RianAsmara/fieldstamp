package id.asmaraputra.fieldstamp.core.storage

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Explicit, user-initiated export. Nothing leaves the device unless the user shares or saves it.
 * No storage permission is used: sharing goes through FileProvider, saving goes through the
 * Storage Access Framework (a destination the user picks) or MediaStore on Android 10+.
 */
class ExportManager(private val context: Context, private val files: FileStore) {

    fun shareIntent(relativePaths: List<String>, mimeType: String, title: String): Intent {
        val uris = relativePaths.map { files.shareUri(it) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = mimeType
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant to the chosen target on every API level.
        send.clipData = ClipData.newUri(context.contentResolver, title, uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun viewIntent(relativePath: String, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(files.shareUri(relativePath), mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Copies an app file to a document the user created through ACTION_CREATE_DOCUMENT. */
    suspend fun copyToDocument(relativePath: String, destination: Uri) = withContext(Dispatchers.IO) {
        val source = files.resolve(relativePath)
        if (!source.isFile) throw MissingFileException()
        val out = context.contentResolver.openOutputStream(destination, "wt") ?: throw IOException("Destination unavailable")
        out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
    }

    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Saves a JPEG into Pictures/FieldStamp via MediaStore (Android 10+, no permission needed). */
    suspend fun saveJpegToGallery(relativePath: String, displayName: String): Uri = withContext(Dispatchers.IO) {
        check(canSaveToGallery)
        val source = files.resolve(relativePath)
        if (!source.isFile) throw MissingFileException()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FieldStamp")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("MediaStore stream unavailable")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}

class MissingFileException : IOException("Source file is missing")
