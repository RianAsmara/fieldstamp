package id.asmaraputra.fieldstamp.report

import id.asmaraputra.fieldstamp.core.model.ReportItem
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup<T>(val date: LocalDate, val items: List<T>)

data class ReportPeriod(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "Period end precedes start" } }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val days: Long get() = end.toEpochDay() - start.toEpochDay() + 1

    companion object {
        fun month(month: YearMonth) = ReportPeriod(month.atDay(1), month.atEndOfMonth())
    }
}

/** Issues the builder surfaces for review. None of them silently exclude a photo. */
enum class ItemIssue { MISSING_RECORD, MISSING_FILE, NO_CAPTION, NO_GPS }

object ReportGrouping {

    /**
     * Groups by report date (which defaults to the recorded local capture date), skipping days without
     * photos. Within a day: explicit display order, then capture time, then id — fully deterministic.
     */
    fun <T> groupByDay(
        items: List<T>,
        date: (T) -> LocalDate,
        displayOrder: (T) -> Int,
        capturedAt: (T) -> Long,
        id: (T) -> Long,
    ): List<DayGroup<T>> =
        items.groupBy(date)
            .toSortedMap()
            .map { (day, dayItems) ->
                DayGroup(day, dayItems.sortedWith(compareBy(displayOrder, capturedAt, id)))
            }

    fun groupItems(items: List<ReportItem>): List<DayGroup<ReportItem>> = groupByDay(
        items,
        date = { it.entry.reportDate },
        displayOrder = { it.entry.displayOrder },
        capturedAt = { it.photo?.photo?.captureTime?.epochMillis ?: it.entry.snapshot?.capturedAtEpochMillis ?: Long.MAX_VALUE },
        id = { it.entry.id },
    )

    /** Display order for new items: chronological position within each day, starting at 0. */
    fun <T> chronologicalOrder(items: List<T>, date: (T) -> LocalDate, capturedAt: (T) -> Long, id: (T) -> Long): Map<Long, Int> =
        items.groupBy(date).values.flatMap { day ->
            day.sortedWith(compareBy(capturedAt, id)).mapIndexed { index, item -> id(item) to index }
        }.toMap()

    fun issues(item: ReportItem, fileExists: (String) -> Boolean, imagePath: (ReportItem) -> String?): Set<ItemIssue> {
        val photo = item.photo?.photo ?: return setOf(ItemIssue.MISSING_RECORD)
        return buildSet {
            val path = imagePath(item)
            if (path == null || !fileExists(path)) add(ItemIssue.MISSING_FILE)
            if (item.effectiveCaption.isBlank()) add(ItemIssue.NO_CAPTION)
            if (photo.location == null) add(ItemIssue.NO_GPS)
        }
    }

    /** Blocking issues prevent generation until the user repairs the selection. */
    fun isBlocking(issue: ItemIssue) = issue == ItemIssue.MISSING_RECORD || issue == ItemIssue.MISSING_FILE
}
