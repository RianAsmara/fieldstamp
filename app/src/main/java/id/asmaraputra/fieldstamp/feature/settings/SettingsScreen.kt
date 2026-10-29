package id.asmaraputra.fieldstamp.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import id.asmaraputra.fieldstamp.BuildConfig
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.PaywallRoute
import id.asmaraputra.fieldstamp.app.PrivacyRoute
import id.asmaraputra.fieldstamp.app.TemplateEditorRoute
import id.asmaraputra.fieldstamp.app.TemplatesRoute
import id.asmaraputra.fieldstamp.app.UiText
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.app.text
import id.asmaraputra.fieldstamp.core.billing.Entitlement
import id.asmaraputra.fieldstamp.core.billing.EntitlementSource
import id.asmaraputra.fieldstamp.core.billing.RestoreResult
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.ListRow
import id.asmaraputra.fieldstamp.core.designsystem.ProBadge
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.designsystem.SwitchRow
import id.asmaraputra.fieldstamp.core.image.BitmapIO
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.settings.AppSettings
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.feature.common.appFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class StorageUsage(val photos: Long, val reports: Long, val free: Long)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val entitlement: StateFlow<Entitlement> = c.billing.entitlement
    val debugPro: StateFlow<Boolean> = c.settings.debugProOverride.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    fun loadStorage() = viewModelScope.launch {
        _storage.value = withContext(Dispatchers.IO) {
            StorageUsage(
                photos = listOf(FileStore.Area.ORIGINAL, FileStore.Area.RENDERED, FileStore.Area.THUMB, FileStore.Area.TEMPLATE).sumOf { c.files.sizeOf(it) },
                reports = c.files.sizeOf(FileStore.Area.REPORT),
                free = c.files.usableBytes(),
            )
        }
    }

    fun setRecordLocation(v: Boolean) = viewModelScope.launch { c.settings.setRecordLocation(v) }
    fun setEmbedGps(v: Boolean) = viewModelScope.launch { c.settings.setEmbedGps(v) }
    fun setAutoPlace(v: Boolean) = viewModelScope.launch { c.settings.setAutoPlaceName(v) }
    fun setOpenCamera(v: Boolean) = viewModelScope.launch { c.settings.setOpenCameraOnLaunch(v) }
    fun setPreparedBy(v: String) = viewModelScope.launch { c.settings.setPreparedBy(v) }
    fun setOrganization(v: String) = viewModelScope.launch { c.settings.setOrganizationName(v) }
    fun setDebugPro(v: Boolean) = viewModelScope.launch { if (BuildConfig.DEBUG) c.settings.setDebugProOverride(v) }

    /** Copies the chosen logo into app storage, downscaled to a sensible size for a report header. */
    fun setLogo(uri: Uri, onError: (UiText) -> Unit) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val temp = c.files.newTempFile("img")
                c.appContext.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                val bitmap = BitmapIO.decodeUprightMaxEdge(temp, 600)
                temp.delete()
                val path = c.files.newRelativePath(FileStore.Area.BRANDING, "png")
                c.files.writeAtomically(path) { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                bitmap.recycle()
                val old = c.settings.current().logoPath
                c.settings.setLogoPath(path)
                c.files.delete(old)
            }
        }.onFailure { onError(text(R.string.logo_error)) }
    }

    fun removeLogo() = viewModelScope.launch {
        val old = c.settings.current().logoPath
        c.settings.setLogoPath(null)
        c.files.delete(old)
    }

    fun restore(onResult: (UiText) -> Unit) {
        if (_restoring.value) return
        _restoring.value = true
        viewModelScope.launch {
            val result = c.billing.restore()
            _restoring.value = false
            onResult(
                when (result) {
                    RestoreResult.Restored -> text(R.string.billing_restored)
                    RestoreResult.NothingToRestore -> text(R.string.billing_nothing_to_restore)
                    is RestoreResult.Failed -> text(R.string.billing_restore_failed)
                },
            )
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entitlement by vm.entitlement.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val restoring by vm.restoring.collectAsStateWithLifecycle()
    val debugPro by vm.debugPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (UiText) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg.resolve(context)) } }

    LaunchedEffect(Unit) { vm.loadStorage() }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setLogo(uri, show)
    }

    Scaffold(topBar = { FsTopBar(stringResource(R.string.nav_settings)) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_capture))
            SwitchRow(stringResource(R.string.settings_open_camera), s.openCameraOnLaunch, vm::setOpenCamera, supporting = stringResource(R.string.settings_open_camera_hint))
            SwitchRow(stringResource(R.string.settings_record_location), s.recordLocation, vm::setRecordLocation, supporting = stringResource(R.string.settings_record_location_hint))
            SwitchRow(stringResource(R.string.settings_embed_gps), s.embedGpsInExif, vm::setEmbedGps, supporting = stringResource(R.string.settings_embed_gps_hint))
            SwitchRow(stringResource(R.string.settings_auto_place), s.autoPlaceName, vm::setAutoPlace, supporting = stringResource(R.string.settings_auto_place_hint))
            ListRow(
                title = stringResource(R.string.settings_basic_stamp),
                supporting = stringResource(R.string.settings_basic_stamp_hint),
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(TemplateEditorRoute(templateId = -1)) },
            )
            ListRow(
                title = stringResource(R.string.templates_title),
                supporting = stringResource(R.string.settings_templates_hint),
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(TemplatesRoute) },
            )
            FsDivider()

            SectionHeader(stringResource(R.string.settings_reports))
            var preparedBy by rememberSaveable(s.preparedBy) { mutableStateOf(s.preparedBy) }
            OutlinedTextField(
                value = preparedBy,
                onValueChange = { preparedBy = it.take(120); vm.setPreparedBy(preparedBy) },
                label = { Text(stringResource(R.string.field_prepared_by_default)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            Row(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_branding), style = MaterialTheme.typography.titleSmall)
                if (!entitlement.isPro) { Spacer(Modifier.width(Spacing.sm)); ProBadge() }
            }
            Text(
                stringResource(if (entitlement.isPro) R.string.settings_branding_hint else R.string.settings_branding_hint_free),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            var org by rememberSaveable(s.organizationName) { mutableStateOf(s.organizationName) }
            OutlinedTextField(
                value = org,
                onValueChange = { org = it.take(120); vm.setOrganization(org) },
                label = { Text(stringResource(R.string.field_organization)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    val logo = appFile(s.logoPath)
                    if (logo != null && logo.isFile) AsyncImage(model = logo, contentDescription = stringResource(R.string.settings_logo), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(Spacing.md))
                TextButton(onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(if (s.logoPath == null) R.string.settings_logo_choose else R.string.settings_logo_change))
                }
                if (s.logoPath != null) TextButton(onClick = vm::removeLogo) { Text(stringResource(R.string.action_remove)) }
            }
            FsDivider()

            SectionHeader(stringResource(R.string.settings_pro))
            ListRow(
                title = stringResource(if (entitlement.isPro) R.string.pro_status_active else R.string.pro_status_free),
                supporting = proSupporting(entitlement),
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(PaywallRoute) },
            )
            TextButton(onClick = { vm.restore(show) }, enabled = !restoring, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                Text(stringResource(if (restoring) R.string.billing_restoring else R.string.billing_restore))
            }
            if (BuildConfig.DEBUG) {
                SwitchRow(stringResource(R.string.settings_debug_pro), debugPro, vm::setDebugPro, supporting = stringResource(R.string.settings_debug_pro_hint))
            }
            FsDivider()

            SectionHeader(stringResource(R.string.settings_storage))
            Text(
                storage?.let { stringResource(R.string.settings_storage_value, Formats.fileSize(it.photos), Formats.fileSize(it.reports), Formats.fileSize(it.free)) }
                    ?: stringResource(R.string.settings_storage_loading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            Text(
                stringResource(R.string.settings_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            FsDivider()

            SectionHeader(stringResource(R.string.settings_about))
            ListRow(
                title = stringResource(R.string.privacy_title),
                supporting = stringResource(R.string.settings_privacy_hint),
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                onClick = { nav.navigate(PrivacyRoute) },
            )
            ListRow(title = stringResource(R.string.settings_version), supporting = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun proSupporting(e: Entitlement): String {
    val verified = e.lastVerifiedAt?.let {
        val z = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        Formats.shortDate(z.toLocalDate())
    }
    return when (e.source) {
        EntitlementSource.PLAY_VERIFIED -> stringResource(R.string.pro_source_verified)
        EntitlementSource.CACHED -> stringResource(R.string.pro_source_cached, verified.orEmpty())
        EntitlementSource.DEBUG_OVERRIDE -> stringResource(R.string.pro_source_debug)
        EntitlementSource.NONE -> stringResource(R.string.pro_status_free_hint)
    }
}
