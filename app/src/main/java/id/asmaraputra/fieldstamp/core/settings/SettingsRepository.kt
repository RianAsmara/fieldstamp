package id.asmaraputra.fieldstamp.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    /** Stamp layout used when no PNG template is selected. */
    val basicStampConfig: TemplateConfig = TemplateConfig(),
    val selectedTemplateId: Long? = null,
    val recordLocation: Boolean = true,
    val embedGpsInExif: Boolean = true,
    /** Look up a place name for new photos (sends coordinates to the device's geocoding service). */
    val autoPlaceName: Boolean = true,
    /** Open the camera straight away when the app is launched from the launcher. */
    val openCameraOnLaunch: Boolean = true,
    /** False until the app has been opened once; the very first launch shows Home instead of the camera. */
    val launchedBefore: Boolean = false,
    val currentProjectId: Long? = null,
    val currentActivityId: Long? = null,
    val organizationName: String = "",
    val logoPath: String? = null,
    val preparedBy: String = "",
)

/** Last entitlement confirmed by Google Play. Used offline; refreshed whenever Play is reachable. */
data class EntitlementCache(
    val isPro: Boolean,
    val lastVerifiedAt: Long,
    val purchaseTokenHash: String?,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val basicStamp = stringPreferencesKey("basic_stamp_config")
        val selectedTemplate = longPreferencesKey("selected_template_id")
        val recordLocation = booleanPreferencesKey("record_location")
        val embedGps = booleanPreferencesKey("embed_gps_exif")
        val autoPlace = booleanPreferencesKey("auto_place_name")
        val openCamera = booleanPreferencesKey("open_camera_on_launch")
        val launchedBefore = booleanPreferencesKey("launched_before")
        val currentProject = longPreferencesKey("current_project_id")
        val currentActivity = longPreferencesKey("current_activity_id")
        val organizationName = stringPreferencesKey("organization_name")
        val logoPath = stringPreferencesKey("logo_path")
        val preparedBy = stringPreferencesKey("prepared_by")
        val proIsPro = booleanPreferencesKey("pro_is_pro")
        val proVerifiedAt = longPreferencesKey("pro_verified_at")
        val proTokenHash = stringPreferencesKey("pro_token_hash")
        val debugPro = booleanPreferencesKey("debug_pro_override")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            basicStampConfig = TemplateConfig.fromJson(p[Keys.basicStamp]),
            selectedTemplateId = p[Keys.selectedTemplate],
            recordLocation = p[Keys.recordLocation] ?: true,
            embedGpsInExif = p[Keys.embedGps] ?: true,
            autoPlaceName = p[Keys.autoPlace] ?: true,
            openCameraOnLaunch = p[Keys.openCamera] ?: true,
            launchedBefore = p[Keys.launchedBefore] ?: false,
            currentProjectId = p[Keys.currentProject],
            currentActivityId = p[Keys.currentActivity],
            organizationName = p[Keys.organizationName].orEmpty(),
            logoPath = p[Keys.logoPath],
            preparedBy = p[Keys.preparedBy].orEmpty(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    val entitlementCache: Flow<EntitlementCache?> = context.dataStore.data.map { p ->
        val verifiedAt = p[Keys.proVerifiedAt] ?: return@map null
        EntitlementCache(p[Keys.proIsPro] ?: false, verifiedAt, p[Keys.proTokenHash])
    }

    /** Debug builds only: lets QA exercise Pro features without a Play purchase. */
    val debugProOverride: Flow<Boolean> = context.dataStore.data.map { it[Keys.debugPro] ?: false }

    suspend fun setBasicStampConfig(config: TemplateConfig) = edit { it[Keys.basicStamp] = config.normalized().toJson() }

    suspend fun setSelectedTemplate(id: Long?) = edit { if (id == null) it.remove(Keys.selectedTemplate) else it[Keys.selectedTemplate] = id }

    suspend fun setRecordLocation(value: Boolean) = edit { it[Keys.recordLocation] = value }

    suspend fun setEmbedGps(value: Boolean) = edit { it[Keys.embedGps] = value }

    suspend fun setAutoPlaceName(value: Boolean) = edit { it[Keys.autoPlace] = value }

    suspend fun setOpenCameraOnLaunch(value: Boolean) = edit { it[Keys.openCamera] = value }

    suspend fun markLaunched() = edit { it[Keys.launchedBefore] = true }

    suspend fun setCaptureContext(projectId: Long?, activityId: Long?) = edit {
        if (projectId == null) it.remove(Keys.currentProject) else it[Keys.currentProject] = projectId
        if (activityId == null) it.remove(Keys.currentActivity) else it[Keys.currentActivity] = activityId
    }

    suspend fun setOrganizationName(value: String) = edit { it[Keys.organizationName] = value.trim() }

    suspend fun setLogoPath(value: String?) = edit { if (value == null) it.remove(Keys.logoPath) else it[Keys.logoPath] = value }

    suspend fun setPreparedBy(value: String) = edit { it[Keys.preparedBy] = value.trim() }

    suspend fun saveEntitlement(cache: EntitlementCache) = edit {
        it[Keys.proIsPro] = cache.isPro
        it[Keys.proVerifiedAt] = cache.lastVerifiedAt
        if (cache.purchaseTokenHash == null) it.remove(Keys.proTokenHash) else it[Keys.proTokenHash] = cache.purchaseTokenHash
    }

    suspend fun setDebugProOverride(value: Boolean) = edit { it[Keys.debugPro] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
