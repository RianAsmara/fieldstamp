package id.asmaraputra.fieldstamp.app

import android.content.Context
import id.asmaraputra.fieldstamp.core.billing.BillingManager
import id.asmaraputra.fieldstamp.core.database.FieldStampDatabase
import id.asmaraputra.fieldstamp.core.image.StampRenderer
import id.asmaraputra.fieldstamp.core.location.LocationProvider
import id.asmaraputra.fieldstamp.core.location.PlaceNameResolver
import id.asmaraputra.fieldstamp.core.settings.SettingsRepository
import id.asmaraputra.fieldstamp.core.storage.ExportManager
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.data.PhotoProcessor
import id.asmaraputra.fieldstamp.data.PhotoRepository
import id.asmaraputra.fieldstamp.data.ProjectRepository
import id.asmaraputra.fieldstamp.data.TemplateRepository
import id.asmaraputra.fieldstamp.report.ReportGenerator
import id.asmaraputra.fieldstamp.report.ReportRepository
import id.asmaraputra.fieldstamp.report.ZipExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import id.asmaraputra.fieldstamp.data.PhotoFilter

/** Manual dependency graph. Small enough that a DI framework would add more than it removes. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Outlives screens: report generation and billing callbacks run here. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: FieldStampDatabase by lazy { FieldStampDatabase.create(appContext) }
    val files = FileStore(appContext)
    val settings = SettingsRepository(appContext)
    val location = LocationProvider(appContext)
    val places = PlaceNameResolver(appContext)
    val export = ExportManager(appContext, files)
    val stampRenderer = StampRenderer(appContext)
    val processor = PhotoProcessor(files, stampRenderer)

    val projects: ProjectRepository by lazy { ProjectRepository(database.projectDao(), database.activityDao()) }
    val templates: TemplateRepository by lazy { TemplateRepository(appContext, database.templateDao(), files) }
    val photos: PhotoRepository by lazy {
        PhotoRepository(appContext, database.photoDao(), database.reportDao(), projects, templates, settings, processor, files)
    }
    val reports: ReportRepository by lazy { ReportRepository(database, photos, projects, files) }
    val reportGenerator: ReportGenerator by lazy { ReportGenerator(appContext, reports, projects, settings, files, appScope) }
    val zipExporter = ZipExporter(files)
    val billing: BillingManager by lazy { BillingManager(appContext, settings, appScope) }

    /** One-shot filter request for the Photos tab (e.g. "show this project's photos"). */
    val pendingPhotoFilter = MutableStateFlow<PhotoFilter?>(null)
}
