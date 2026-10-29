package id.asmaraputra.fieldstamp.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil

class ReportLayoutEngineTest {
    /** Approximates text width as 0.5em per character, honoring explicit line breaks. */
    private val measurer = TextMeasurer { text, style, width ->
        val perLine = (width / (style.size * 0.5f)).toInt().coerceAtLeast(1)
        text.split("\n").sumOf { ceil(it.length.coerceAtLeast(1) / perLine.toDouble()).toInt() }
    }
    private val engine = ReportLayoutEngine(measurer)

    private fun doc(days: List<DocDay>, columns: Int = 2, notes: String = "", closing: String = "", approval: String? = "Mengetahui") = ReportDocument(
        title = "Laporan Dokumentasi Kegiatan",
        periodLine = "Periode: Oktober 2026",
        scopeLine = null,
        generatedLine = "Dibuat 1 Nov 2026",
        organizationName = null,
        hasLogo = false,
        summaryHeading = "Ringkasan",
        summary = listOf(DocSummaryRow("Jumlah foto", "${days.sumOf { it.entries.size }}"), DocSummaryRow("Hari", "${days.size}")),
        notesHeading = "Catatan",
        notes = notes,
        days = days,
        columns = columns,
        continuedSuffix = "(lanjutan)",
        closingHeading = "Penutup",
        closingNotes = closing,
        preparedByLabel = "Disusun oleh",
        preparedBy = "Petugas",
        approvalLabel = approval,
        disclaimer = "Waktu dan koordinat dicatat oleh perangkat.",
        emptyMessage = "Tidak ada foto.",
    )

    private var nextIndex = 0
    private fun entry(aspect: Float = 4f / 3f, caption: String = "Pekerjaan pengecoran", meta: List<String> = emptyList()) =
        DocEntry(nextIndex++, aspect, "09:00 WIB", caption, meta)

    private fun day(n: Int, count: Int, caption: String = "Pekerjaan") = DocDay("Hari $n", "$count foto", (0 until count).map { entry(caption = caption) })

    private fun allElements(r: ReportLayoutEngine.LayoutResult) = r.pages.flatMap { it.elements }

    @Test fun everyEntryIsPlacedExactlyOnce() {
        val result = engine.layout(doc((1..12).map { day(it, 5) }))
        val placed = allElements(result).filterIsInstance<PdfElement.Image>().map { it.entryIndex }
        assertEquals((0 until 60).toList(), placed.sorted())
    }

    @Test fun nothingIsDrawnBelowTheContentArea() {
        val result = engine.layout(doc((1..20).map { day(it, 7) }, notes = "Catatan ".repeat(80), closing = "Penutup ".repeat(60)))
        result.pages.forEach { page ->
            page.elements.forEach { e ->
                val bottom = when (e) {
                    is PdfElement.Image -> e.boxY + e.boxHeight
                    is PdfElement.Text -> e.y + minOf(measurer.lineCount(e.text, e.style, e.width), e.maxLines) * e.style.lineHeight
                    is PdfElement.Frame -> e.y + e.height
                    is PdfElement.Rule -> e.y
                    is PdfElement.SignatureLine -> e.y
                    is PdfElement.Logo -> e.y + e.size
                }
                assertTrue("element ends at $bottom on page ${page.number}", bottom <= ReportLayoutEngine.BOTTOM + 0.5f)
            }
        }
    }

    @Test fun imagesPreserveAspectRatioInsideTheirBox() {
        nextIndex = 0
        val entries = listOf(entry(aspect = 3f / 4f), entry(aspect = 16f / 9f), entry(aspect = 1f))
        val result = engine.layout(doc(listOf(DocDay("Hari", "3 foto", entries))))
        val images = allElements(result).filterIsInstance<PdfElement.Image>()
        images.forEach { img ->
            val expected = entries.first { it.index == img.entryIndex }.aspectRatio
            assertEquals(expected, img.width / img.height, 0.01f)
            assertTrue(img.width <= img.boxWidth + 0.01f && img.height <= img.boxHeight + 0.01f)
            assertTrue(abs((img.x - img.boxX) - (img.boxX + img.boxWidth - img.x - img.width)) < 0.01f)
        }
    }

    @Test fun dayHeadingIsNeverOrphanedAtPageBottom() {
        val result = engine.layout(doc((1..15).map { day(it, 3) }))
        result.pages.forEach { page ->
            val headings = page.elements.filterIsInstance<PdfElement.Text>().filter { it.style == PdfTextStyle.HEADING && it.text.startsWith("Hari") }
            headings.forEach { h ->
                val imagesBelow = page.elements.filterIsInstance<PdfElement.Image>().any { it.boxY > h.y }
                assertTrue("heading '${h.text}' has no photos after it on page ${page.number}", imagesBelow)
            }
        }
    }

    @Test fun longDaysContinueWithRepeatedHeading() {
        val result = engine.layout(doc(listOf(day(1, 20))))
        assertTrue(result.pages.size > 1)
        val continued = allElements(result).filterIsInstance<PdfElement.Text>().filter { it.text == "Hari 1 (lanjutan)" }
        assertEquals(result.pages.size - 1, continued.size.coerceAtMost(result.pages.size - 1))
        assertTrue(continued.isNotEmpty())
    }

    @Test fun rowsAreNeverSplitAcrossPages() {
        val result = engine.layout(doc(listOf(day(1, 30))))
        val byPage = result.pages.map { p -> p.elements.filterIsInstance<PdfElement.Image>().map { it.entryIndex } }
        byPage.forEach { indices ->
            // Two columns: every page starts a row on an even index.
            assertTrue(indices.isEmpty() || indices.first() % 2 == 0)
        }
    }

    @Test fun oversizedCaptionIsTruncatedAndReported() {
        nextIndex = 0
        val huge = "kata ".repeat(5000)
        val result = engine.layout(doc(listOf(DocDay("Hari", "1 foto", listOf(entry(caption = huge))))))
        assertTrue(0 in result.truncatedEntries)
        val caption = allElements(result).filterIsInstance<PdfElement.Text>().first { it.style == PdfTextStyle.CAPTION }
        assertTrue(caption.maxLines < measurer.lineCount(huge, PdfTextStyle.CAPTION, caption.width))
    }

    @Test fun normalCaptionsAreNotTruncated() {
        val result = engine.layout(doc(listOf(day(1, 4, caption = "Keterangan sedang ".repeat(10)))))
        assertTrue(result.truncatedEntries.isEmpty())
    }

    @Test fun columnCountControlsCellWidth() {
        fun widthFor(columns: Int) = allElements(engine.layout(doc(listOf(day(1, 3)), columns = columns))).filterIsInstance<PdfElement.Image>().first().boxWidth
        assertTrue(widthFor(1) > widthFor(2))
        assertTrue(widthFor(2) > widthFor(3))
        assertEquals(ReportLayoutEngine.CONTENT_WIDTH, widthFor(1), 0.01f)
    }

    @Test fun emptyReportProducesOnePageWithMessage() {
        val result = engine.layout(doc(emptyList()))
        assertEquals(1, result.pages.size)
        assertTrue(allElements(result).filterIsInstance<PdfElement.Text>().any { it.text == "Tidak ada foto." })
    }

    @Test fun signatureBlockIsOptional() {
        val with = allElements(engine.layout(doc(listOf(day(1, 1)), approval = "Mengetahui"))).filterIsInstance<PdfElement.SignatureLine>()
        val without = allElements(engine.layout(doc(listOf(day(1, 1)), approval = null))).filterIsInstance<PdfElement.SignatureLine>()
        assertEquals(2, with.size)
        assertEquals(1, without.size)
    }

    @Test fun largeReportPaginatesPredictably() {
        val result = engine.layout(doc((1..31).map { day(it, 15) }))
        val images = allElements(result).count { it is PdfElement.Image }
        assertEquals(465, images)
        // A2-column page holds at most 3 rows (6 photos) in this layout.
        result.pages.forEach { p -> assertTrue(p.elements.count { it is PdfElement.Image } <= 6) }
        assertEquals((1..result.pages.size).toList(), result.pages.map { it.number })
    }
}
