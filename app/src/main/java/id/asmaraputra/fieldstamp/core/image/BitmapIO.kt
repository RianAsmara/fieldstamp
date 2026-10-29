package id.asmaraputra.fieldstamp.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max

/** Thrown when a file cannot be decoded as an image. */
class UnsupportedImageException(message: String) : IOException(message)

/** Decoding helpers that honor EXIF orientation and never decode more pixels than needed. */
object BitmapIO {

    fun orientation(file: File): Int = runCatching {
        ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun swapsAxes(orientation: Int) = orientation in setOf(
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
    )

    /** Raw encoded dimensions (before orientation). */
    fun rawSize(file: File): IntSize {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw UnsupportedImageException("Not a decodable image")
        return IntSize(opts.outWidth, opts.outHeight)
    }

    /** Dimensions as displayed, i.e. after applying EXIF orientation. */
    fun orientedSize(file: File): IntSize {
        val raw = rawSize(file)
        return if (swapsAxes(orientation(file))) IntSize(raw.height, raw.width) else raw
    }

    /**
     * Decodes [file] upright, downsampled so the result is at least [minWidth] x [minHeight] in
     * displayed orientation (or the full image if it is smaller).
     */
    fun decodeUpright(file: File, minWidth: Int, minHeight: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val orientation = orientation(file)
        val raw = rawSize(file)
        val (needW, needH) = if (swapsAxes(orientation)) minHeight to minWidth else minWidth to minHeight
        val opts = BitmapFactory.Options().apply {
            inSampleSize = StampLayout.sampleSize(raw, needW.coerceAtLeast(1), needH.coerceAtLeast(1))
            inPreferredConfig = config
        }
        val decoded = BitmapFactory.decodeFile(file.path, opts) ?: throw UnsupportedImageException("Decode failed")
        return applyOrientation(decoded, orientation)
    }

    fun decodeUprightMaxEdge(file: File, maxEdge: Int): Bitmap {
        val oriented = orientedSize(file)
        val scale = maxEdge.toFloat() / max(oriented.width, oriented.height)
        val bitmap = decodeUpright(file, (oriented.width * scale).toInt(), (oriented.height * scale).toInt())
        return scaleToMaxEdge(bitmap, maxEdge)
    }

    fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.outputStream().buffered().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("JPEG encoding failed")
        }
    }
}
