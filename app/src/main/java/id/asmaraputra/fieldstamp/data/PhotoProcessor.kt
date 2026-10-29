package id.asmaraputra.fieldstamp.data

import id.asmaraputra.fieldstamp.core.image.BitmapIO
import id.asmaraputra.fieldstamp.core.image.StampData
import id.asmaraputra.fieldstamp.core.image.StampRenderer
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.core.storage.MissingFileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RenderedFiles(val renderedPath: String, val thumbnailPath: String, val widthPx: Int, val heightPx: Int)

/**
 * Produces the stamped JPEG and its thumbnail from an original. The original is only read, never modified.
 * Both outputs are fully written to temp files before being moved into place.
 */
class PhotoProcessor(private val files: FileStore, private val renderer: StampRenderer) {

    suspend fun render(
        originalPath: String,
        templatePath: String?,
        config: TemplateConfig,
        data: StampData,
        embedGps: Boolean,
    ): RenderedFiles = withContext(Dispatchers.Default) {
        val original = files.resolve(originalPath)
        if (!original.isFile) throw MissingFileException()
        val template = templatePath?.let { files.resolve(it) }?.takeIf { it.isFile }
        files.ensureSpace(files.dir(FileStore.Area.RENDERED))

        val bitmap = renderer.render(original, template, config, data)
        val width = bitmap.width
        val height = bitmap.height
        val renderedTemp = files.newTempFile("jpg")
        val thumbTemp = files.newTempFile("jpg")
        try {
            BitmapIO.writeJpeg(bitmap, renderedTemp, StampRenderer.JPEG_QUALITY)
            val thumb = BitmapIO.scaleToMaxEdge(bitmap, StampRenderer.THUMB_EDGE)
            try {
                BitmapIO.writeJpeg(thumb, thumbTemp, 80)
            } finally {
                if (thumb !== bitmap) thumb.recycle()
            }
        } catch (t: Throwable) {
            renderedTemp.delete()
            thumbTemp.delete()
            throw t
        } finally {
            bitmap.recycle()
        }
        withContext(Dispatchers.IO) {
            StampRenderer.writeExif(renderedTemp, data, embedGps)
            val renderedPath = files.newRelativePath(FileStore.Area.RENDERED, "jpg")
            val thumbPath = files.newRelativePath(FileStore.Area.THUMB, "jpg")
            files.moveInto(renderedTemp, renderedPath)
            files.moveInto(thumbTemp, thumbPath)
            RenderedFiles(renderedPath, thumbPath, width, height)
        }
    }

    /** Lower-resolution render for on-screen previews; written to a temp file the caller owns. */
    suspend fun preview(
        original: File,
        templatePath: String?,
        config: TemplateConfig,
        data: StampData,
        maxLongEdge: Int = 1280,
        drawGuide: Boolean = false,
    ): android.graphics.Bitmap = withContext(Dispatchers.Default) {
        val template = templatePath?.let { files.resolve(it) }?.takeIf { it.isFile }
        renderer.render(original, template, config, data, maxLongEdge, drawGuide)
    }
}
