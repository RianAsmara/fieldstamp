package id.asmaraputra.fieldstamp.core.storage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * App-owned file layout under the private files directory. The database stores paths relative to [root]
 * so records stay valid if Android relocates the app's data directory.
 *
 *   photos/original/  untouched captures and imported copies
 *   photos/rendered/  stamped JPEG outputs
 *   photos/thumb/     grid thumbnails
 *   templates/        imported PNG overlays (never modified)
 *   reports/          generated PDFs
 *   branding/         organization logo for report covers
 *   tmp/              in-progress writes; cleared at startup
 */
class FileStore(context: Context, val root: File = context.filesDir) {
    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.files"

    enum class Area(val dir: String) {
        ORIGINAL("photos/original"),
        RENDERED("photos/rendered"),
        THUMB("photos/thumb"),
        TEMPLATE("templates"),
        REPORT("reports"),
        BRANDING("branding"),
        TMP("tmp"),
    }

    fun dir(area: Area): File = File(root, area.dir).apply { mkdirs() }

    fun newRelativePath(area: Area, extension: String): String =
        "${area.dir}/${UUID.randomUUID()}.$extension"

    fun resolve(relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        require(file.path.startsWith(root.canonicalPath)) { "Path escapes app storage" }
        return file
    }

    fun exists(relativePath: String?): Boolean =
        relativePath != null && runCatching { resolve(relativePath).isFile }.getOrDefault(false)

    fun delete(relativePath: String?) {
        if (relativePath == null) return
        runCatching { resolve(relativePath).delete() }
    }

    fun relativize(file: File): String = file.canonicalFile.relativeTo(root.canonicalFile).path

    /**
     * Writes to a temporary sibling and renames it into place only after [block] completes,
     * so a crash or cancellation never leaves a half-written file at [relativePath].
     */
    @Throws(IOException::class)
    fun writeAtomically(relativePath: String, block: (OutputStream) -> Unit): File {
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        ensureSpace(target.parentFile ?: root)
        val temp = File(target.parentFile, ".${target.name}.part")
        try {
            temp.outputStream().buffered().use { out ->
                block(out)
                out.flush()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            return target
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    fun newTempFile(extension: String): File = File(dir(Area.TMP), "${UUID.randomUUID()}.$extension")

    /** Promotes a finished temp file into [relativePath]. */
    fun moveInto(source: File, relativePath: String): File {
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return target
    }

    /**
     * Removes abandoned temp files. Recent files are kept so an unsaved capture survives process
     * recreation (Android may kill the app while the review screen is in the background).
     */
    fun clearTemp(olderThanMillis: Long = 24L * 60 * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        dir(Area.TMP).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        listOf(Area.ORIGINAL, Area.RENDERED, Area.THUMB, Area.TEMPLATE, Area.REPORT).forEach { area ->
            dir(area).listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        }
    }

    fun usableBytes(): Long = runCatching { StatFs(root.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    @Throws(InsufficientStorageException::class)
    fun ensureSpace(dir: File, requiredBytes: Long = MIN_FREE_BYTES) {
        val available = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < requiredBytes) throw InsufficientStorageException(available)
    }

    fun sizeOf(area: Area): Long = dir(area).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** content:// URI that other apps can read when granted, without exposing filesystem paths. */
    fun shareUri(relativePath: String): Uri = FileProvider.getUriForFile(appContext, authority, resolve(relativePath))

    companion object {
        const val MIN_FREE_BYTES = 20L * 1024 * 1024
    }
}

class InsufficientStorageException(val availableBytes: Long) : IOException("Insufficient storage")
