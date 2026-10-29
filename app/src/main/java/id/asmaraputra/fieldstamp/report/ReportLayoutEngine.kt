package id.asmaraputra.fieldstamp.report

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Typographic roles in the PDF. Sizes are in PostScript points. */
enum class PdfTextStyle(val size: Float, val lineHeight: Float) {
    TITLE(19f, 24f),
    SUBTITLE(10.5f, 14.5f),
    HEADING(12.5f, 16f),
    LABEL(8f, 11f),
    BODY(9.5f, 13.5f),
    CAPTION(9f, 12.5f),
    META(7.5f, 10.5f),
    FOOTER(7f, 9f),
}

enum class PdfInk { PRIMARY, SECONDARY, ACCENT }

/** Measures wrapped text. Implemented with StaticLayout on device and with a fixed-width model in tests. */
fun interface TextMeasurer {
    fun lineCount(text: String, style: PdfTextStyle, width: Float): Int
}

sealed interface PdfElement {
    data class Text(
        val text: String,
        val style: PdfTextStyle,
        val x: Float,
        val y: Float,
        val width: Float,
        val maxLines: Int = Int.MAX_VALUE,
        val ink: PdfInk = PdfInk.PRIMARY,
    ) : PdfElement

    /** Photo [entryIndex] drawn at the fitted rect, which preserves aspect ratio inside its box. */
    data class Image(
        val entryIndex: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val boxX: Float,
        val boxY: Float,
        val boxWidth: Float,
        val boxHeight: Float,
    ) : PdfElement

    data class Logo(val x: Float, val y: Float, val size: Float) : PdfElement
    data class Rule(val x: Float, val y: Float, val width: Float, val strong: Boolean = false) : PdfElement
    data class Frame(val x: Float, val y: Float, val width: Float, val height: Float) : PdfElement
    data class SignatureLine(val x: Float, val y: Float, val width: Float) : PdfElement
}

data class PdfPage(val number: Int, val elements: List<PdfElement>)

data class DocEntry(
    val index: Int,
    /** width / height of the image that will be drawn. */
    val aspectRatio: Float,
    val timeLine: String,
    val caption: String,
    val metaLines: List<String>,
)

data class DocDay(val heading: String, val countLabel: String, val entries: List<DocEntry>)

data class DocSummaryRow(val label: String, val value: String)

data class ReportDocument(
    val title: String,
    val periodLine: String,
    val scopeLine: String?,
    val generatedLine: String,
    val organizationName: String?,
    val hasLogo: Boolean,
    val summaryHeading: String,
    val summary: List<DocSummaryRow>,
    val notesHeading: String,
    val notes: String,
    val days: List<DocDay>,
    val columns: Int,
    val continuedSuffix: String,
    val closingHeading: String,
    val closingNotes: String,
    val preparedByLabel: String,
    val preparedBy: String,
    val approvalLabel: String?,
    val disclaimer: String,
    val emptyMessage: String,
)

/**
 * Paginates a report onto A4 pages. Rules:
 * - a day heading is never left alone at the bottom of a page (it moves with its first row);
 * - a row of photos is never split across pages; continuation pages repeat the day heading;
 * - images keep their aspect ratio inside a fixed box;
 * - a caption too long for an empty page is truncated with an ellipsis rather than overflowing,
 *   and that truncation is reported in [LayoutResult.truncatedEntries].
 */
class ReportLayoutEngine(private val measurer: TextMeasurer) {

    data class LayoutResult(val pages: List<PdfPage>, val truncatedEntries: Set<Int>)

    fun layout(doc: ReportDocument): LayoutResult {
        val pages = mutableListOf<PdfPage>()
        var elements = mutableListOf<PdfElement>()
        var y = TOP
        val truncated = mutableSetOf<Int>()

        fun newPage() {
            pages += PdfPage(pages.size + 1, elements)
            elements = mutableListOf()
            y = TOP
        }

        fun textHeight(text: String, style: PdfTextStyle, width: Float, maxLines: Int = Int.MAX_VALUE): Float =
            if (text.isBlank()) 0f else min(measurer.lineCount(text, style, width), maxLines) * style.lineHeight

        fun addText(text: String, style: PdfTextStyle, x: Float = MARGIN, width: Float = CONTENT_WIDTH, ink: PdfInk = PdfInk.PRIMARY, gapAfter: Float = 0f) {
            if (text.isBlank()) return
            elements += PdfElement.Text(text, style, x, y, width, ink = ink)
            y += textHeight(text, style, width) + gapAfter
        }

        // --- Header -------------------------------------------------------------------------------
        if (doc.hasLogo || !doc.organizationName.isNullOrBlank()) {
            var textX = MARGIN
            if (doc.hasLogo) {
                elements += PdfElement.Logo(MARGIN, y, LOGO_SIZE)
                textX += LOGO_SIZE + 10f
            }
            val orgWidth = CONTENT_WIDTH - (textX - MARGIN)
            doc.organizationName?.takeIf { it.isNotBlank() }?.let {
                val h = textHeight(it, PdfTextStyle.HEADING, orgWidth, 2)
                val ty = if (doc.hasLogo) y + max(0f, (LOGO_SIZE - h) / 2f) else y
                elements += PdfElement.Text(it, PdfTextStyle.HEADING, textX, ty, orgWidth, maxLines = 2)
            }
            y += if (doc.hasLogo) LOGO_SIZE else textHeight(doc.organizationName.orEmpty(), PdfTextStyle.HEADING, orgWidth, 2)
            y += 10f
            elements += PdfElement.Rule(MARGIN, y, CONTENT_WIDTH, strong = true)
            y += 14f
        }
        addText(doc.title, PdfTextStyle.TITLE, gapAfter = 4f)
        addText(doc.periodLine, PdfTextStyle.SUBTITLE, gapAfter = 1f)
        doc.scopeLine?.let { addText(it, PdfTextStyle.SUBTITLE, gapAfter = 1f) }
        addText(doc.generatedLine, PdfTextStyle.META, ink = PdfInk.SECONDARY, gapAfter = 14f)

        // --- Summary ------------------------------------------------------------------------------
        if (doc.summary.isNotEmpty()) {
            val labelWidth = CONTENT_WIDTH * 0.42f
            val valueWidth = CONTENT_WIDTH - labelWidth - 2 * BOX_PADDING
            val rowHeights = doc.summary.map { max(textHeight(it.label, PdfTextStyle.BODY, labelWidth - BOX_PADDING), textHeight(it.value, PdfTextStyle.BODY, valueWidth)) }
            val boxHeight = PdfTextStyle.LABEL.lineHeight + 6f + rowHeights.sum() + 2 * BOX_PADDING
            if (y + boxHeight > BOTTOM) newPage()
            elements += PdfElement.Frame(MARGIN, y, CONTENT_WIDTH, boxHeight)
            var by = y + BOX_PADDING
            elements += PdfElement.Text(doc.summaryHeading.uppercase(), PdfTextStyle.LABEL, MARGIN + BOX_PADDING, by, CONTENT_WIDTH - 2 * BOX_PADDING, ink = PdfInk.SECONDARY)
            by += PdfTextStyle.LABEL.lineHeight + 6f
            doc.summary.forEachIndexed { i, row ->
                elements += PdfElement.Text(row.label, PdfTextStyle.BODY, MARGIN + BOX_PADDING, by, labelWidth - BOX_PADDING, ink = PdfInk.SECONDARY)
                elements += PdfElement.Text(row.value, PdfTextStyle.BODY, MARGIN + BOX_PADDING + labelWidth, by, valueWidth)
                by += rowHeights[i]
            }
            y += boxHeight + 16f
        }

        if (doc.notes.isNotBlank()) {
            val h = PdfTextStyle.LABEL.lineHeight + 4f + textHeight(doc.notes, PdfTextStyle.BODY, CONTENT_WIDTH)
            if (y + min(h, 120f) > BOTTOM) newPage()
            addText(doc.notesHeading.uppercase(), PdfTextStyle.LABEL, ink = PdfInk.SECONDARY, gapAfter = 4f)
            flowParagraph(doc.notes, PdfTextStyle.BODY, { elements }, { y }, { y = it }, ::newPage)
            y += 16f
        }

        // --- Daily sections -----------------------------------------------------------------------
        val columns = doc.columns.coerceIn(1, 3)
        val cellWidth = (CONTENT_WIDTH - COLUMN_GAP * (columns - 1)) / columns
        val boxHeight = cellWidth * if (columns == 1) 0.62f else 0.75f
        val headingHeight = PdfTextStyle.HEADING.lineHeight + 4f + 8f

        if (doc.days.isEmpty()) {
            addText(doc.emptyMessage, PdfTextStyle.BODY, ink = PdfInk.SECONDARY, gapAfter = 16f)
        }

        fun cellHeight(entry: DocEntry, captionLines: Int): Float =
            boxHeight + 6f + PdfTextStyle.META.lineHeight + 2f +
                (if (entry.caption.isBlank()) 0f else captionLines * PdfTextStyle.CAPTION.lineHeight + 2f) +
                entry.metaLines.sumOf { textHeight(it, PdfTextStyle.META, cellWidth).toDouble() }.toFloat()

        val pageBodyHeight = BOTTOM - TOP - headingHeight

        doc.days.forEach { day ->
            fun drawHeading(continued: Boolean) {
                val label = if (continued) "${day.heading} ${doc.continuedSuffix}" else day.heading
                val countWidth = 90f
                elements += PdfElement.Text(label, PdfTextStyle.HEADING, MARGIN, y, CONTENT_WIDTH - countWidth)
                elements += PdfElement.Text(day.countLabel, PdfTextStyle.META, MARGIN + CONTENT_WIDTH - countWidth, y + 4f, countWidth, ink = PdfInk.SECONDARY)
                y += PdfTextStyle.HEADING.lineHeight + 4f
                elements += PdfElement.Rule(MARGIN, y, CONTENT_WIDTH)
                y += 8f
            }

            val rows = day.entries.chunked(columns)
            rows.forEachIndexed { rowIndex, row ->
                // Fit captions: never taller than an empty page allows.
                val captionLines = row.associate { entry ->
                    val natural = if (entry.caption.isBlank()) 0 else measurer.lineCount(entry.caption, PdfTextStyle.CAPTION, cellWidth)
                    val fixed = cellHeight(entry, 0)
                    val maxLines = floor((pageBodyHeight - fixed - ROW_GAP) / PdfTextStyle.CAPTION.lineHeight).toInt().coerceAtLeast(1)
                    if (natural > maxLines) truncated += entry.index
                    entry.index to min(natural, maxLines)
                }
                val rowHeight = row.maxOf { cellHeight(it, captionLines.getValue(it.index)) }
                val needed = rowHeight + if (rowIndex == 0) headingHeight else 0f

                if (y + needed > BOTTOM) {
                    if (y > TOP) newPage()
                    drawHeading(continued = rowIndex > 0)
                } else if (rowIndex == 0) {
                    drawHeading(continued = false)
                }

                row.forEachIndexed { col, entry ->
                    val x = MARGIN + col * (cellWidth + COLUMN_GAP)
                    val (w, h) = fit(entry.aspectRatio, cellWidth, boxHeight)
                    elements += PdfElement.Image(entry.index, x + (cellWidth - w) / 2f, y + (boxHeight - h) / 2f, w, h, x, y, cellWidth, boxHeight)
                    var cy = y + boxHeight + 6f
                    elements += PdfElement.Text(entry.timeLine, PdfTextStyle.META, x, cy, cellWidth, maxLines = 1, ink = PdfInk.ACCENT)
                    cy += PdfTextStyle.META.lineHeight + 2f
                    val lines = captionLines.getValue(entry.index)
                    if (entry.caption.isNotBlank()) {
                        elements += PdfElement.Text(entry.caption, PdfTextStyle.CAPTION, x, cy, cellWidth, maxLines = lines)
                        cy += lines * PdfTextStyle.CAPTION.lineHeight + 2f
                    }
                    entry.metaLines.forEach { meta ->
                        elements += PdfElement.Text(meta, PdfTextStyle.META, x, cy, cellWidth, ink = PdfInk.SECONDARY)
                        cy += textHeight(meta, PdfTextStyle.META, cellWidth)
                    }
                }
                y += rowHeight + ROW_GAP
            }
            y += DAY_GAP
        }

        // --- Closing ------------------------------------------------------------------------------
        val hasClosing = doc.closingNotes.isNotBlank() || doc.preparedBy.isNotBlank() || doc.approvalLabel != null
        if (hasClosing) {
            val signatureHeight = PdfTextStyle.LABEL.lineHeight + SIGNATURE_SPACE + PdfTextStyle.BODY.lineHeight
            val closingHeight = PdfTextStyle.LABEL.lineHeight + 6f + min(textHeight(doc.closingNotes, PdfTextStyle.BODY, CONTENT_WIDTH), 200f) + 14f + signatureHeight
            if (y + closingHeight > BOTTOM) newPage()
            addText(doc.closingHeading.uppercase(), PdfTextStyle.LABEL, ink = PdfInk.SECONDARY, gapAfter = 6f)
            if (doc.closingNotes.isNotBlank()) {
                flowParagraph(doc.closingNotes, PdfTextStyle.BODY, { elements }, { y }, { y = it }, ::newPage)
            }
            y += 14f
            if (y + signatureHeight > BOTTOM) newPage()
            val half = (CONTENT_WIDTH - COLUMN_GAP * 2) / 2f
            val columnsX = if (doc.approvalLabel != null) listOf(MARGIN, MARGIN + half + COLUMN_GAP * 2) else listOf(MARGIN)
            elements += PdfElement.Text(doc.preparedByLabel, PdfTextStyle.LABEL, columnsX[0], y, half, ink = PdfInk.SECONDARY)
            doc.approvalLabel?.let { elements += PdfElement.Text(it, PdfTextStyle.LABEL, columnsX[1], y, half, ink = PdfInk.SECONDARY) }
            val lineY = y + PdfTextStyle.LABEL.lineHeight + SIGNATURE_SPACE
            columnsX.forEach { elements += PdfElement.SignatureLine(it, lineY, half * 0.8f) }
            if (doc.preparedBy.isNotBlank()) {
                elements += PdfElement.Text(doc.preparedBy, PdfTextStyle.BODY, columnsX[0], lineY + 3f, half)
            }
            y = lineY + 3f + PdfTextStyle.BODY.lineHeight + 12f
        }

        if (y + textHeight(doc.disclaimer, PdfTextStyle.META, CONTENT_WIDTH) > BOTTOM) newPage()
        addText(doc.disclaimer, PdfTextStyle.META, ink = PdfInk.SECONDARY)

        pages += PdfPage(pages.size + 1, elements)
        return LayoutResult(pages, truncated)
    }

    /** Lays out a long paragraph line-block by line-block so it can continue onto following pages. */
    private fun flowParagraph(
        text: String,
        style: PdfTextStyle,
        elements: () -> MutableList<PdfElement>,
        getY: () -> Float,
        setY: (Float) -> Unit,
        newPage: () -> Unit,
    ) {
        text.trim().split("\n").forEach { paragraph ->
            val p = paragraph.ifBlank { " " }
            val h = measurer.lineCount(p, style, CONTENT_WIDTH) * style.lineHeight
            if (getY() + h > BOTTOM) newPage()
            // A single paragraph taller than a page is clamped to the page with an ellipsis.
            val available = BOTTOM - getY()
            val maxLines = floor(available / style.lineHeight).toInt().coerceAtLeast(1)
            elements() += PdfElement.Text(p, style, MARGIN, getY(), CONTENT_WIDTH, maxLines = maxLines)
            setY(getY() + min(h, maxLines * style.lineHeight))
        }
    }

    companion object {
        const val PAGE_WIDTH = 595.28f
        const val PAGE_HEIGHT = 841.89f
        const val MARGIN = 42f
        const val FOOTER_HEIGHT = 22f
        const val TOP = MARGIN
        const val BOTTOM = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val COLUMN_GAP = 14f
        const val ROW_GAP = 14f
        const val DAY_GAP = 8f
        const val BOX_PADDING = 10f
        const val LOGO_SIZE = 40f
        const val SIGNATURE_SPACE = 48f

        fun fit(aspectRatio: Float, boxWidth: Float, boxHeight: Float): Pair<Float, Float> {
            val ratio = if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio else 1f
            return if (boxWidth / boxHeight > ratio) (boxHeight * ratio) to boxHeight else boxWidth to (boxWidth / ratio)
        }
    }
}
