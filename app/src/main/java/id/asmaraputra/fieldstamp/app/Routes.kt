package id.asmaraputra.fieldstamp.app

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object PhotosRoute
@Serializable data object ReportsRoute
@Serializable data object SettingsRoute

@Serializable data object CameraRoute

/**
 * Review of an unsaved capture or import. [tempName] is a file in the app's tmp/ directory; values are
 * route arguments so they survive process recreation. Coordinates are strings ("" = none).
 */
@Serializable
data class CaptureReviewRoute(
    val tempName: String,
    val capturedAt: Long,
    val zoneId: String,
    val imported: Boolean,
    val timeFromFile: Boolean = true,
    val lat: String = "",
    val lon: String = "",
    val accuracy: String = "",
    val locationAt: Long = 0,
)

@Serializable data class PhotoDetailRoute(val photoId: Long)
@Serializable data object ProjectsRoute
@Serializable data class ProjectDetailRoute(val projectId: Long)
@Serializable data object TemplatesRoute

/** templateId = -1 edits the basic (text-only) stamp; stagedName is set when importing a new PNG. */
@Serializable data class TemplateEditorRoute(val templateId: Long = -1, val stagedName: String = "", val stagedWidth: Int = 0, val stagedHeight: Int = 0)
@Serializable data object ReportCreateRoute
@Serializable data class ReportBuilderRoute(val reportId: Long)
@Serializable data object PaywallRoute
@Serializable data object PrivacyRoute
