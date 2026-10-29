package id.asmaraputra.fieldstamp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import id.asmaraputra.fieldstamp.core.database.FieldStampDatabase
import id.asmaraputra.fieldstamp.core.image.StampRenderer
import id.asmaraputra.fieldstamp.core.settings.SettingsRepository
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.data.PhotoProcessor
import id.asmaraputra.fieldstamp.data.PhotoRepository
import id.asmaraputra.fieldstamp.data.ProjectRepository
import id.asmaraputra.fieldstamp.data.TemplateRepository
import id.asmaraputra.fieldstamp.report.ReportGenerator
import id.asmaraputra.fieldstamp.report.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Isolated object graph for instrumentation tests: in-memory database and a throwaway file root,
 * so tests never touch the installed app's real data. Only synthetic images are used.
 */
class TestGraph(val context: Context) {
    val root: File = File(context.cacheDir, "test-${System.nanoTime()}").apply { mkdirs() }
    val db: FieldStampDatabase = Room.inMemoryDatabaseBuilder(context, FieldStampDatabase::class.java).build()
    val files = FileStore(context, root)
    val settings = SettingsRepository(context)
    val renderer = StampRenderer(context)
    val processor = PhotoProcessor(files, renderer)
    val projects = ProjectRepository(db.projectDao(), db.activityDao())
    val templates = TemplateRepository(context, db.templateDao(), files)
    val photos = PhotoRepository(context, db.photoDao(), db.reportDao(), projects, templates, settings, processor, files)
    val reports = ReportRepository(db, photos, projects, files)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val generator = ReportGenerator(context, reports, projects, settings, files, scope)

    fun close() {
        db.close()
        root.deleteRecursively()
    }
}

object Fixtures {
    const val PHOTO_COLOR = 0xFF2E7D32.toInt()
    const val FRAME_COLOR = 0xFFC62828.toInt()

    /** Solid-color synthetic "photo" with an optional EXIF orientation tag. */
    fun photo(dir: File, width: Int = 1600, height: Int = 1200, color: Int = PHOTO_COLOR, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val file = File(dir, "photo-${System.nanoTime()}.jpg")
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        // Marker in the raw top-left corner so orientation handling can be verified.
        Canvas(bmp).drawRect(0f, 0f, width * 0.1f, height * 0.1f, Paint().apply { this.color = Color.BLUE })
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        }
        return file
    }

    /** PNG with an opaque frame and a fully transparent window, like a Canva export. */
    fun framePng(dir: File, width: Int = 1080, height: Int = 1350, border: Int = 90): File {
        val file = File(dir, "frame-${System.nanoTime()}.png")
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val paint = Paint().apply { color = FRAME_COLOR }
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, width.toFloat(), border.toFloat(), paint)
        c.drawRect(0f, (height - border).toFloat(), width.toFloat(), height.toFloat(), paint)
        c.drawRect(0f, 0f, border.toFloat(), height.toFloat(), paint)
        c.drawRect((width - border).toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    const val TEXT_X = 300
    const val TEXT_Y = 40

    /** Free-Canva-style frame: navy header and border, plain white photo window, small white text in the header. */
    fun whiteWindowJpg(dir: File, window: Int = Color.WHITE): File {
        val file = File(dir, "frame-${System.nanoTime()}.jpg")
        val bmp = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF0E2A47.toInt())
        c.drawRect(60f, 160f, 1020f, 1290f, Paint().apply { color = window })
        c.drawRect(280f, 25f, 520f, 60f, Paint().apply { color = Color.WHITE })
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }

    fun opaquePng(dir: File): File {
        val file = File(dir, "opaque-${System.nanoTime()}.png")
        val bmp = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    fun similar(a: Int, b: Int, tolerance: Int = 24): Boolean =
        kotlin.math.abs(Color.red(a) - Color.red(b)) <= tolerance &&
            kotlin.math.abs(Color.green(a) - Color.green(b)) <= tolerance &&
            kotlin.math.abs(Color.blue(a) - Color.blue(b)) <= tolerance
}
