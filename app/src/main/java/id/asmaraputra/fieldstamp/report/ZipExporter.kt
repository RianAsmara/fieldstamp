package id.asmaraputra.fieldstamp.report

import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.storage.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

data class ZipItem(val photo: PhotoListItem, val reportDate: LocalDate, val caption: String, val imagePath: String)

/** Exports photos as a ZIP with one folder per date and a CSV manifest. Written to tmp/, then shared. */
class ZipExporter(private val files: FileStore) {

    suspend fun export(name: String, items: List<ZipItem>): String = withContext(Dispatchers.IO) {
        val temp = files.newTempFile("zip.part")
        val dayFormat = DateTimeFormatter.ISO_LOCAL_DATE
        val timeFormat = DateTimeFormatter.ofPattern("HHmmss", Locale.ROOT)
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                val manifest = StringBuilder(HEADER)
                val used = HashSet<String>()
                items.sortedWith(compareBy({ it.reportDate }, { it.photo.photo.captureTime.epochMillis })).forEach { item ->
                    coroutineContext.ensureActive()
                    val source = files.resolve(item.imagePath)
                    if (!source.isFile) return@forEach
                    val folder = dayFormat.format(item.reportDate)
                    var entryName = "$folder/${timeFormat.format(item.photo.photo.captureTime.zoned)}_${item.photo.photo.id}.jpg"
                    if (!used.add(entryName)) entryName = entryName.replace(".jpg", "_${used.size}.jpg")
                    zip.putNextEntry(ZipEntry(entryName))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    val p = item.photo.photo
                    manifest.append(
                        listOf(
                            entryName,
                            folder,
                            Formats.stampDateTime(p.captureTime),
                            p.captureTime.zoneId,
                            item.photo.projectName.orEmpty(),
                            item.photo.activityName.orEmpty(),
                            item.caption,
                            p.category.orEmpty(),
                            p.location?.latitude?.toString().orEmpty(),
                            p.location?.longitude?.toString().orEmpty(),
                            p.location?.accuracyMeters?.toString().orEmpty(),
                            p.locationName.orEmpty(),
                        ).joinToString(",") { csv(it) },
                    ).append("\r\n")
                }
                zip.putNextEntry(ZipEntry("manifest.csv"))
                // UTF-8 BOM so spreadsheet apps detect the encoding.
                zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "fieldstamp" }
            val target = File(files.dir(FileStore.Area.TMP), "$safeName.zip")
            target.delete()
            if (!temp.renameTo(target)) throw java.io.IOException("Could not finalize ZIP")
            files.relativize(target)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

    companion object {
        private const val HEADER = "file,report_date,captured_at,time_zone,project,activity,caption,category,latitude,longitude,accuracy_m,location_name\r\n"
    }
}
