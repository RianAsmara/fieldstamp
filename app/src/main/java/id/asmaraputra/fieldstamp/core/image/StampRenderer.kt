package id.asmaraputra.fieldstamp.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.TypefaceSpan
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.core.model.BackdropStyle
import id.asmaraputra.fieldstamp.core.model.StampAnchor
import id.asmaraputra.fieldstamp.core.model.StampTextColor
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import kotlinx.coroutines.ensureActive
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Renders photo + optional transparent PNG overlay + configured text fields into a single bitmap. */
class StampRenderer(private val context: Context) {

    private val regular: Typeface by lazy { font(R.font.plex_sans_medium) ?: Typeface.DEFAULT }
    private val bold: Typeface by lazy { font(R.font.plex_sans_bold) ?: Typeface.DEFAULT_BOLD }

    private fun font(id: Int): Typeface? = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()

    fun labels() = StampLabels(
        project = context.getString(R.string.stamp_label_project),
        activity = context.getString(R.string.stamp_label_activity),
        category = context.getString(R.string.stamp_label_category),
        coordinates = context.getString(R.string.stamp_label_coordinates),
        place = context.getString(R.string.stamp_label_place),
    )

    /**
     * @param photoFile the original, untouched image (any EXIF orientation).
     * @param templateFile the transparent PNG overlay, or null for a text-only stamp.
     */
    suspend fun render(
        photoFile: File,
        templateFile: File?,
        config: TemplateConfig,
        data: StampData,
        maxLongEdge: Int = StampLayout.DEFAULT_MAX_LONG_EDGE,
        drawGuide: Boolean = false,
    ): Bitmap {
        val photoSize = BitmapIO.orientedSize(photoFile)
        val templateSize = templateFile?.let { BitmapIO.rawSize(it) }
        val canvasSize = StampLayout.canvasSize(photoSize, templateSize, maxLongEdge)
        coroutineContext.ensureActive()

        val output = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.BLACK)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Photo: cover-fit into the canvas, decoded no larger than needed.
            val photo = BitmapIO.decodeUpright(photoFile, canvasSize.width, canvasSize.height)
            try {
                val crop = StampLayout.centerCrop(IntSize(photo.width, photo.height), canvasSize)
                canvas.drawBitmap(photo, Rect(crop.left, crop.top, crop.right, crop.bottom), Rect(0, 0, canvasSize.width, canvasSize.height), paint)
            } finally {
                photo.recycle()
            }
            coroutineContext.ensureActive()

            // Overlay: drawn with alpha intact so transparent regions reveal the photo.
            if (templateFile != null && templateSize != null) {
                val overlay = BitmapIO.decodeUpright(templateFile, canvasSize.width, canvasSize.height)
                try {
                    canvas.drawBitmap(overlay, null, Rect(0, 0, canvasSize.width, canvasSize.height), paint)
                } finally {
                    overlay.recycle()
                }
            }
            coroutineContext.ensureActive()

            drawText(canvas, canvasSize, config.normalized(), data, drawGuide)
            return output
        } catch (t: Throwable) {
            output.recycle()
            throw t
        }
    }

    private fun drawText(canvas: Canvas, size: IntSize, config: TemplateConfig, data: StampData, drawGuide: Boolean) {
        val lines = StampLayout.lines(config, data, labels())
        val shortEdge = size.shortEdge.toFloat()
        val style = config.effectiveBackdrop
        val textColor = if (config.textColor == StampTextColor.WHITE) Color.WHITE else Color.BLACK

        val text = SpannableStringBuilder()
        lines.forEachIndexed { index, line ->
            if (index > 0) text.append('\n')
            val start = text.length
            text.append(line.text)
            if (line.emphasized) text.setSpan(BoldSpan(bold), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        fun paint(px: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular
            textSize = px
            color = textColor
            if (style == BackdropStyle.NONE) {
                setShadowLayer(px * 0.18f, 0f, px * 0.06f, if (textColor == Color.WHITE) 0xCC000000.toInt() else 0x99FFFFFF.toInt())
            }
        }

        fun layout(px: Float, width: Int, alignment: Layout.Alignment, maxLines: Int = Int.MAX_VALUE): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint(px), width.coerceAtLeast(1))
                .setAlignment(alignment)
                .setLineSpacing(px * 0.12f, 1f)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(if (maxLines == Int.MAX_VALUE) null else android.text.TextUtils.TruncateAt.END)
                .build()

        fun backdropPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (style) {
                BackdropStyle.SOLID -> config.solidColor or 0xFF000000.toInt()
                else -> if (textColor == Color.WHITE) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
            }
        }

        if (config.anchor == StampAnchor.CUSTOM) {
            // Fixed box (e.g. a template's info panel): text shrinks to fit, then clips with an ellipsis.
            val (l, t, r, b) = StampLayout.boxRect(size, config.box).let { listOf(it[0], it[1], it[2], it[3]) }
            val rect = RectF(l, t, r, b)
            if (style != BackdropStyle.NONE) canvas.drawRect(rect, backdropPaint())
            if (drawGuide) {
                canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = shortEdge * 0.004f
                    color = 0xFFF2A900.toInt()
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(shortEdge * 0.015f, shortEdge * 0.01f), 0f)
                })
            }
            if (lines.isEmpty()) return
            val pad = shortEdge * 0.012f
            val innerW = (rect.width() - 2 * pad).roundToInt().coerceAtLeast(1)
            val innerH = rect.height() - 2 * pad
            val start = shortEdge * config.textSizePercent / 100f
            val min = shortEdge * CUSTOM_MIN_TEXT_PERCENT / 100f
            val px = StampLayout.fitTextSize(start, min, innerH) { layout(it, innerW, Layout.Alignment.ALIGN_NORMAL).height.toFloat() }
            val lineHeight = layout(px, innerW, Layout.Alignment.ALIGN_NORMAL, 1).height.toFloat().coerceAtLeast(1f)
            val fits = layout(px, innerW, Layout.Alignment.ALIGN_NORMAL).height <= innerH
            val box = if (fits) layout(px, innerW, Layout.Alignment.ALIGN_NORMAL)
            else layout(px, innerW, Layout.Alignment.ALIGN_NORMAL, (innerH / lineHeight).toInt().coerceAtLeast(1))
            canvas.save()
            canvas.clipRect(rect)
            canvas.translate(rect.left + pad, rect.top + pad)
            box.draw(canvas)
            canvas.restore()
            return
        }

        if (lines.isEmpty()) return
        val textPx = shortEdge * config.textSizePercent / 100f
        val margin = shortEdge * config.marginPercent / 100f
        val padding = textPx * 0.6f
        val maxBlockWidth = when (config.anchor) {
            StampAnchor.BOTTOM_CENTER -> size.width - 2 * margin
            else -> size.width * 0.78f - margin
        }.coerceAtLeast(textPx * 4)
        val alignment = when (config.anchor) {
            StampAnchor.TOP_RIGHT, StampAnchor.BOTTOM_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            StampAnchor.BOTTOM_CENTER -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val innerWidth = (maxBlockWidth - 2 * padding).roundToInt().coerceAtLeast(1)
        val measured = layout(textPx, innerWidth, Layout.Alignment.ALIGN_NORMAL)
        var widest = 0f
        for (i in 0 until measured.lineCount) widest = max(widest, measured.getLineWidth(i))
        val blockInnerWidth = widest.roundToInt().coerceIn(1, innerWidth)
        val block = layout(textPx, blockInnerWidth, alignment)

        val blockW = blockInnerWidth + 2 * padding
        val blockH = block.height + 2 * padding
        val left = when (config.anchor) {
            StampAnchor.TOP_RIGHT, StampAnchor.BOTTOM_RIGHT -> size.width - margin - blockW
            StampAnchor.BOTTOM_CENTER -> (size.width - blockW) / 2f
            else -> margin
        }
        val top = when (config.anchor) {
            StampAnchor.TOP_LEFT, StampAnchor.TOP_RIGHT -> margin
            else -> size.height - margin - blockH
        }
        if (style != BackdropStyle.NONE) {
            val radius = textPx * 0.35f
            canvas.drawRoundRect(RectF(left, top, left + blockW, top + blockH), radius, radius, backdropPaint())
        }
        canvas.save()
        canvas.translate(left + padding, top + padding)
        block.draw(canvas)
        canvas.restore()
    }

    private class BoldSpan(private val face: Typeface) : TypefaceSpan("sans-serif") {
        override fun updateDrawState(ds: TextPaint) { ds.typeface = face }
        override fun updateMeasureState(paint: TextPaint) { paint.typeface = face }
    }

    companion object {
        const val JPEG_QUALITY = 90
        /** Smallest text a custom box may shrink to, as % of the short edge (≈ 12 px at 1080 px). */
        const val CUSTOM_MIN_TEXT_PERCENT = 1.1f
        const val THUMB_EDGE = 480

        private val exifDate = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

        /**
         * Writes descriptive EXIF to a freshly encoded JPEG. These tags are informative only: any app
         * can edit or strip them, so they are never presented as proof of time or place.
         */
        fun writeExif(file: File, data: StampData, includeGps: Boolean) {
            val exif = ExifInterface(file)
            val zoned = data.captureTime.zoned
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate.format(zoned))
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate.format(zoned))
            val offset = zoned.offset.id.let { if (it == "Z") "+00:00" else it }
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "FieldStamp")
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            data.caption.trim().takeIf { it.isNotEmpty() }?.let { exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it.take(500)) }
            val location = data.location
            if (includeGps && location != null) {
                exif.setLatLong(location.latitude, location.longitude)
            }
            exif.saveAttributes()
        }
    }
}
