package id.asmaraputra.fieldstamp.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.core.image.BitmapIO
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

/**
 * Draws a laid-out report with android.graphics.pdf.PdfDocument. Images are decoded one at a time at
 * roughly [IMAGE_DPI] for their box size and released immediately, so memory stays flat for large reports.
 */
class PdfReportRenderer(private val context: Context) : TextMeasurer {

    private val regular by lazy { font(R.font.plex_sans_regular) }
    private val semibold by lazy { font(R.font.plex_sans_semibold) }
    private val bold by lazy { font(R.font.plex_sans_bold) }
    private val mono by lazy { font(R.font.plex_mono_regular) }

    private fun font(id: Int): Typeface = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT

    private val inkPrimary = 0xFF1A1F24.toInt()
    private val inkSecondary = 0xFF5B6570.toInt()
    private val inkAccent = 0xFF0E5A6B.toInt()
    private val ruleColor = 0xFFC9CED4.toInt()
    private val frameFill = 0xFFF4F5F6.toInt()

    private val paints = HashMap<Pair<PdfTextStyle, PdfInk>, TextPaint>()

    private fun paint(style: PdfTextStyle, ink: PdfInk = PdfInk.PRIMARY): TextPaint = paints.getOrPut(style to ink) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = style.size
            typeface = when (style) {
                PdfTextStyle.TITLE -> bold
                PdfTextStyle.HEADING, PdfTextStyle.LABEL -> semibold
                PdfTextStyle.META, PdfTextStyle.FOOTER -> mono
                else -> regular
            }
            if (style == PdfTextStyle.LABEL) letterSpacing = 0.06f
            color = when (ink) {
                PdfInk.PRIMARY -> inkPrimary
                PdfInk.SECONDARY -> inkSecondary
                PdfInk.ACCENT -> inkAccent
            }
        }
    }

    private fun staticLayout(text: String, style: PdfTextStyle, width: Float, maxLines: Int, ink: PdfInk = PdfInk.PRIMARY): StaticLayout {
        val paint = paint(style, ink)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, ceil(width).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(style.lineHeight - paint.fontSpacing, 1f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()
    }

    override fun lineCount(text: String, style: PdfTextStyle, width: Float): Int =
        staticLayout(text, style, width, Int.MAX_VALUE).lineCount

    /**
     * @param imageFiles image file per [DocEntry.index]
     * @param onProgress called after each image with (done, total)
     */
    suspend fun render(
        pages: List<PdfPage>,
        imageFiles: List<File>,
        logoFile: File?,
        footerLabel: (page: Int, total: Int) -> String,
        out: OutputStream,
        onProgress: (Int, Int) -> Unit,
    ) {
        val document = PdfDocument()
        val totalImages = pages.sumOf { page -> page.elements.count { it is PdfElement.Image } }
        var done = 0
        try {
            pages.forEach { page ->
                coroutineContext.ensureActive()
                val info = PdfDocument.PageInfo.Builder(
                    ReportLayoutEngine.PAGE_WIDTH.toInt(),
                    ReportLayoutEngine.PAGE_HEIGHT.toInt(),
                    page.number,
                ).create()
                val pdfPage = document.startPage(info)
                val canvas = pdfPage.canvas
                page.elements.forEach { element ->
                    coroutineContext.ensureActive()
                    draw(canvas, element, imageFiles, logoFile)
                    if (element is PdfElement.Image) onProgress(++done, totalImages)
                }
                drawFooter(canvas, footerLabel(page.number, pages.size))
                document.finishPage(pdfPage)
            }
            coroutineContext.ensureActive()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun draw(canvas: Canvas, element: PdfElement, imageFiles: List<File>, logoFile: File?) {
        when (element) {
            is PdfElement.Text -> {
                val layout = staticLayout(element.text, element.style, element.width, element.maxLines, element.ink)
                canvas.save()
                canvas.translate(element.x, element.y)
                layout.draw(canvas)
                canvas.restore()
            }
            is PdfElement.Image -> {
                val fill = Paint().apply { color = frameFill }
                canvas.drawRect(element.boxX, element.boxY, element.boxX + element.boxWidth, element.boxY + element.boxHeight, fill)
                val file = imageFiles[element.entryIndex]
                val px = (element.width / 72f * IMAGE_DPI).toInt().coerceAtLeast(1)
                val py = (element.height / 72f * IMAGE_DPI).toInt().coerceAtLeast(1)
                val bitmap = BitmapIO.decodeUpright(file, px, py, Bitmap.Config.ARGB_8888)
                try {
                    val scaled = if (bitmap.width > px * 1.5f) Bitmap.createScaledBitmap(bitmap, px, py, true) else bitmap
                    try {
                        canvas.drawBitmap(scaled, null, RectF(element.x, element.y, element.x + element.width, element.y + element.height), Paint(Paint.FILTER_BITMAP_FLAG))
                    } finally {
                        if (scaled !== bitmap) scaled.recycle()
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            is PdfElement.Logo -> {
                if (logoFile != null && logoFile.isFile) {
                    val px = (element.size / 72f * 300).toInt()
                    val bitmap = runCatching { BitmapIO.decodeUpright(logoFile, px, px) }.getOrNull() ?: return
                    try {
                        val (w, h) = ReportLayoutEngine.fit(bitmap.width.toFloat() / bitmap.height, element.size, element.size)
                        canvas.drawBitmap(bitmap, null, RectF(element.x, element.y + (element.size - h) / 2, element.x + w, element.y + (element.size + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            is PdfElement.Rule -> {
                val p = Paint().apply { color = if (element.strong) inkPrimary else ruleColor; strokeWidth = if (element.strong) 1.2f else 0.6f }
                canvas.drawLine(element.x, element.y, element.x + element.width, element.y, p)
            }
            is PdfElement.Frame -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ruleColor; style = Paint.Style.STROKE; strokeWidth = 0.6f }
                canvas.drawRoundRect(RectF(element.x, element.y, element.x + element.width, element.y + element.height), 3f, 3f, p)
            }
            is PdfElement.SignatureLine -> {
                val p = Paint().apply { color = inkSecondary; strokeWidth = 0.6f }
                canvas.drawLine(element.x, element.y, element.x + element.width, element.y, p)
            }
        }
    }

    private fun drawFooter(canvas: Canvas, label: String) {
        val y = ReportLayoutEngine.PAGE_HEIGHT - ReportLayoutEngine.MARGIN - PdfTextStyle.FOOTER.lineHeight
        val rule = Paint().apply { color = ruleColor; strokeWidth = 0.6f }
        canvas.drawLine(ReportLayoutEngine.MARGIN, y - 6f, ReportLayoutEngine.MARGIN + ReportLayoutEngine.CONTENT_WIDTH, y - 6f, rule)
        val layout = staticLayout(label, PdfTextStyle.FOOTER, ReportLayoutEngine.CONTENT_WIDTH, 1, PdfInk.SECONDARY)
        canvas.save()
        canvas.translate(ReportLayoutEngine.MARGIN, y)
        layout.draw(canvas)
        canvas.restore()
    }

    companion object {
        /** Print-adequate for documentation photos while keeping PDFs shareable. */
        const val IMAGE_DPI = 130f
    }
}
