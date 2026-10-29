package id.asmaraputra.fieldstamp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import id.asmaraputra.fieldstamp.core.database.TemplateDao
import id.asmaraputra.fieldstamp.core.database.TemplateEntity
import id.asmaraputra.fieldstamp.core.database.toModel
import id.asmaraputra.fieldstamp.core.image.BitmapIO
import id.asmaraputra.fieldstamp.core.image.StampLayout
import id.asmaraputra.fieldstamp.core.image.WhiteKeyer
import id.asmaraputra.fieldstamp.core.image.IntSize
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import id.asmaraputra.fieldstamp.core.storage.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed class TemplateImportException(message: String) : IOException(message) {
    class Unreadable : TemplateImportException("File could not be opened")
    class NotPng : TemplateImportException("Only PNG files are supported")
    class NoTransparency : TemplateImportException("PNG has no transparent area")
    class TooSmall : TemplateImportException("Image is too small")
    class TooLarge : TemplateImportException("Image is too large")
    /** A readable image without transparency (JPG, or opaque PNG). It can be converted with [TemplateRepository.stageWhiteKeyed]. */
    class NeedsWhiteKey(val candidate: StagedTemplate) : TemplateImportException("Image has no transparency")
    /** White-key conversion found no white area large enough to be the photo window. */
    class NoWhiteArea : TemplateImportException("No large white area")
}

class TemplateRepository(
    private val context: Context,
    private val dao: TemplateDao,
    private val files: FileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<StampTemplate>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observe(id: Long): Flow<StampTemplate?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: Long): StampTemplate? = dao.get(id)?.toModel()

    suspend fun count(): Int = dao.count()

    fun isSourceAvailable(template: StampTemplate): Boolean = files.exists(template.sourcePath)

    fun sourceFile(template: StampTemplate): File = files.resolve(template.sourcePath)

    /**
     * Validates [uri] and copies its bytes, unmodified, into app storage. Returns a temp handle the caller
     * finalizes with [save] once the user has named and configured the template.
     */
    suspend fun stageImport(uri: Uri): StagedTemplate = withContext(Dispatchers.IO) {
        val temp = files.newTempFile("png")
        try {
            val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: throw TemplateImportException.Unreadable()
            input.use { src -> temp.outputStream().use { src.copyTo(it) } }
            validatePng(temp)
        } catch (e: TemplateImportException.NeedsWhiteKey) {
            // The staged copy is the conversion candidate; the caller converts or discards it.
            throw e
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun validatePng(file: File): StagedTemplate {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val size = IntSize(bounds.outWidth, bounds.outHeight)
        if (size.width <= 0 || size.height <= 0) throw TemplateImportException.Unreadable()
        if (size.shortEdge < MIN_EDGE) throw TemplateImportException.TooSmall()
        if (size.longEdge > MAX_EDGE) throw TemplateImportException.TooLarge()

        val signature = ByteArray(8)
        val read = file.inputStream().use { it.read(signature) }
        val isPng = read == 8 && signature.contentEquals(PNG_SIGNATURE)
        // JPG and other formats cannot be transparent: offer the white-key conversion instead of rejecting.
        if (!isPng) throw TemplateImportException.NeedsWhiteKey(StagedTemplate(file, size.width, size.height))

        // Sample a downscaled copy to confirm there is a meaningful transparent region for the photo.
        val sample = StampLayout.sampleSize(size, 256, 256)
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw TemplateImportException.Unreadable()
        val transparent = try {
            bitmap.hasAlpha() && transparentFraction(bitmap) >= MIN_TRANSPARENT_FRACTION
        } finally {
            bitmap.recycle()
        }
        if (!transparent) throw TemplateImportException.NeedsWhiteKey(StagedTemplate(file, size.width, size.height))
        return StagedTemplate(file, size.width, size.height)
    }

    /**
     * Converts a non-transparent frame into a PNG whose largest white area (the photo window) is transparent.
     * The user's original file is not modified; the converted copy becomes the template source.
     */
    suspend fun stageWhiteKeyed(candidate: StagedTemplate): StagedTemplate = withContext(Dispatchers.Default) {
        val source = BitmapIO.decodeUprightMaxEdge(candidate.file, KEY_MAX_EDGE)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        source.recycle()
        val keyed = WhiteKeyer.key(pixels, width, height) ?: throw TemplateImportException.NoWhiteArea()
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val temp = files.newTempFile("png")
        try {
            out.setHasAlpha(true)
            out.setPixels(keyed.pixels, 0, width, 0, 0, width, height)
            withContext(Dispatchers.IO) {
                temp.outputStream().buffered().use { if (!out.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG encoding failed") }
            }
        } catch (t: Throwable) {
            temp.delete()
            throw t
        } finally {
            out.recycle()
        }
        candidate.file.delete()
        StagedTemplate(temp, width, height)
    }

    private fun transparentFraction(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val transparent = pixels.count { (it ushr 24) < 200 }
        return transparent.toFloat() / pixels.size
    }

    fun discard(staged: StagedTemplate) { staged.file.delete() }

    suspend fun save(staged: StagedTemplate, name: String, config: TemplateConfig): Long = withContext(Dispatchers.IO) {
        val path = files.newRelativePath(FileStore.Area.TEMPLATE, "png")
        files.moveInto(staged.file, path)
        val now = clock()
        val isFirst = dao.count() == 0
        try {
            dao.insert(
                TemplateEntity(
                    name = name.trim(),
                    sourcePath = path,
                    widthPx = staged.width,
                    heightPx = staged.height,
                    configurationJson = config.normalized().toJson(),
                    isDefault = isFirst,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } catch (t: Throwable) {
            files.delete(path)
            throw t
        }
    }

    suspend fun update(id: Long, name: String, config: TemplateConfig) {
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(name = name.trim(), configurationJson = config.normalized().toJson(), updatedAt = clock()))
    }

    suspend fun setDefault(id: Long) = dao.setDefault(id)

    suspend fun clearDefault() = dao.clearDefault()

    /** Already-rendered photos keep their baked-in overlay; only the template record and PNG are removed. */
    suspend fun delete(id: Long) {
        val existing = dao.get(id) ?: return
        dao.delete(id)
        files.delete(existing.sourcePath)
    }

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        const val MIN_EDGE = 320
        const val MAX_EDGE = 6000
        const val MIN_TRANSPARENT_FRACTION = 0.05f
        /** Converted frames are capped near the render size (2560 px) to bound memory. */
        const val KEY_MAX_EDGE = 3000
    }
}

data class StagedTemplate(val file: File, val width: Int, val height: Int)
