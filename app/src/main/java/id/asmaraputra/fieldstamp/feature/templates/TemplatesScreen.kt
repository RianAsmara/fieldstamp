package id.asmaraputra.fieldstamp.feature.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil3.compose.SubcomposeAsyncImage
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.PaywallRoute
import id.asmaraputra.fieldstamp.app.TemplateEditorRoute
import id.asmaraputra.fieldstamp.app.UiText
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.app.text
import id.asmaraputra.fieldstamp.core.billing.Entitlement
import id.asmaraputra.fieldstamp.core.billing.EntitlementPolicy
import id.asmaraputra.fieldstamp.core.designsystem.ConfirmDialog
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsExtendedFab
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.ListRow
import id.asmaraputra.fieldstamp.core.designsystem.MissingImage
import id.asmaraputra.fieldstamp.core.designsystem.Notice
import id.asmaraputra.fieldstamp.core.designsystem.NoticeTone
import id.asmaraputra.fieldstamp.core.designsystem.ProBadge
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.data.StagedTemplate
import id.asmaraputra.fieldstamp.data.TemplateImportException
import id.asmaraputra.fieldstamp.feature.common.appFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplatesState(
    val templates: List<StampTemplate> = emptyList(),
    val selectedId: Long? = null,
    val entitlement: Entitlement = Entitlement.FREE,
    val loaded: Boolean = false,
    val missingSources: Set<Long> = emptySet(),
) {
    val canImport: Boolean get() = EntitlementPolicy.canImportTemplate(templates.size, entitlement)
}

class TemplatesViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<TemplatesState> = combine(c.templates.observeAll(), c.settings.settings, c.billing.entitlement) { templates, settings, ent ->
        TemplatesState(
            templates = templates,
            selectedId = settings.selectedTemplateId?.takeIf { id -> templates.any { it.id == id } },
            entitlement = ent,
            loaded = true,
            missingSources = templates.filterNot { c.templates.isSourceAvailable(it) }.map { it.id }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesState())

    fun select(id: Long?) = viewModelScope.launch {
        c.settings.setSelectedTemplate(id)
        if (id == null) c.templates.clearDefault() else c.templates.setDefault(id)
    }

    fun delete(id: Long) = viewModelScope.launch {
        if (state.value.selectedId == id) c.settings.setSelectedTemplate(null)
        c.templates.delete(id)
    }

    /** Non-transparent image waiting for the user to confirm the white-key conversion. */
    private val _keyCandidate = MutableStateFlow<StagedTemplate?>(null)
    val keyCandidate: StateFlow<StagedTemplate?> = _keyCandidate.asStateFlow()

    private val _converting = MutableStateFlow(false)
    val converting: StateFlow<Boolean> = _converting.asStateFlow()

    private fun route(t: StagedTemplate) = TemplateEditorRoute(templateId = 0, stagedName = t.file.name, stagedWidth = t.width, stagedHeight = t.height)

    fun stage(uri: android.net.Uri, onStaged: (TemplateEditorRoute) -> Unit, onError: (UiText) -> Unit) = viewModelScope.launch {
        runCatching { c.templates.stageImport(uri) }
            .onSuccess { onStaged(route(it)) }
            .onFailure { e ->
                if (e is TemplateImportException.NeedsWhiteKey) {
                    _keyCandidate.value = e.candidate
                    return@onFailure
                }
                onError(
                    when (e) {
                        is TemplateImportException.NotPng -> text(R.string.template_error_not_png)
                        is TemplateImportException.NoTransparency -> text(R.string.template_error_no_transparency)
                        is TemplateImportException.TooSmall -> text(R.string.template_error_too_small, TemplateRepositoryLimits.MIN)
                        is TemplateImportException.TooLarge -> text(R.string.template_error_too_large, TemplateRepositoryLimits.MAX)
                        else -> text(R.string.template_error_unreadable)
                    },
                )
            }
    }

    fun convertCandidate(onStaged: (TemplateEditorRoute) -> Unit, onError: (UiText) -> Unit) {
        val candidate = _keyCandidate.value ?: return
        if (_converting.value) return
        _converting.value = true
        viewModelScope.launch {
            runCatching { c.templates.stageWhiteKeyed(candidate) }
                .onSuccess { _keyCandidate.value = null; onStaged(route(it)) }
                .onFailure { e ->
                    _keyCandidate.value = null
                    candidate.file.delete()
                    onError(if (e is TemplateImportException.NoWhiteArea) text(R.string.template_error_no_white) else text(R.string.template_error_unreadable))
                }
            _converting.value = false
        }
    }

    fun dismissCandidate() {
        _keyCandidate.value?.let { c.templates.discard(it) }
        _keyCandidate.value = null
    }
}

/**
 * Every image type is selectable so files are never silently grayed out; PNG + transparency is checked after
 * picking and explained. octet-stream covers cloud/file apps that mislabel PNG files.
 */
private val TEMPLATE_PICKER_TYPES = arrayOf("image/*", "application/octet-stream")

private object TemplateRepositoryLimits {
    const val MIN = id.asmaraputra.fieldstamp.data.TemplateRepository.MIN_EDGE
    const val MAX = id.asmaraputra.fieldstamp.data.TemplateRepository.MAX_EDGE
}

@Composable
fun TemplatesScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> TemplatesViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var importError by remember { mutableStateOf<UiText?>(null) }
    val keyCandidate by vm.keyCandidate.collectAsStateWithLifecycle()
    val converting by vm.converting.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.stage(uri, onStaged = { nav.navigate(it) }, onError = { msg -> importError = msg })
    }

    Scaffold(
        topBar = { FsTopBar(stringResource(R.string.templates_title), onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FsExtendedFab(
                label = stringResource(R.string.template_import),
                icon = Icons.Outlined.Add,
                onClick = { if (state.canImport) picker.launch(TEMPLATE_PICKER_TYPES) else nav.navigate(PaywallRoute) },
                trailing = if (!state.canImport) { { ProBadge() } } else null,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.templates_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
            }
            item {
                Notice(
                    stringResource(R.string.templates_how_to),
                    NoticeTone.INFO,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                )
            }
            item { SectionHeader(stringResource(R.string.templates_active_heading)) }
            item {
                TemplateRow(
                    title = stringResource(R.string.template_basic_name),
                    supporting = stringResource(R.string.template_basic_desc),
                    selected = state.selectedId == null,
                    onSelect = { vm.select(null) },
                    thumbnail = {
                        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onEdit = { nav.navigate(TemplateEditorRoute(templateId = -1)) },
                    onDelete = null,
                )
                FsDivider(inset = true)
            }
            items(state.templates, key = { it.id }) { template ->
                val missing = template.id in state.missingSources
                TemplateRow(
                    title = template.name,
                    supporting = if (missing) stringResource(R.string.template_source_missing) else "${template.widthPx} × ${template.heightPx} px",
                    selected = state.selectedId == template.id,
                    onSelect = { if (!missing) vm.select(template.id) },
                    thumbnail = { TemplateThumb(if (missing) null else appFile(template.sourcePath)) },
                    onEdit = { nav.navigate(TemplateEditorRoute(templateId = template.id)) },
                    onDelete = { deleting = template.id },
                )
                FsDivider(inset = true)
            }
            if (!state.canImport) {
                item {
                    Notice(
                        stringResource(R.string.template_limit_notice, EntitlementPolicy.FREE_TEMPLATE_LIMIT),
                        NoticeTone.INFO,
                        modifier = Modifier.padding(Spacing.gutter),
                        action = { TextButton(onClick = { nav.navigate(PaywallRoute) }) { Text(stringResource(R.string.pro_learn_more)) } },
                    )
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (keyCandidate != null) {
        AlertDialog(
            onDismissRequest = { if (!converting) vm.dismissCandidate() },
            title = { Text(stringResource(R.string.template_key_title)) },
            text = { Text(stringResource(R.string.template_key_body)) },
            confirmButton = {
                TextButton(enabled = !converting, onClick = { vm.convertCandidate(onStaged = { nav.navigate(it) }, onError = { importError = it }) }) {
                    Text(stringResource(if (converting) R.string.template_key_working else R.string.template_key_confirm))
                }
            },
            dismissButton = { TextButton(enabled = !converting, onClick = vm::dismissCandidate) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.template_error_title)) },
            text = { Text(msg.resolve()) },
            confirmButton = {
                TextButton(onClick = { importError = null; picker.launch(TEMPLATE_PICKER_TYPES) }) { Text(stringResource(R.string.template_choose_other)) }
            },
            dismissButton = { TextButton(onClick = { importError = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.template_delete_title),
            body = stringResource(R.string.template_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.delete(id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TemplateRow(
    title: String,
    supporting: String,
    selected: Boolean,
    onSelect: () -> Unit,
    thumbnail: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    ListRow(
        title = title,
        supporting = if (selected) "${stringResource(R.string.template_in_use)} · $supporting" else supporting,
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                thumbnail()
            }
        },
        trailing = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        },
        onClick = onSelect,
    )
}

/** PNG thumbnail on a checkerboard-free neutral, so transparency is visible as the darker backdrop. */
@Composable
fun TemplateThumb(file: java.io.File?) {
    Box(Modifier.size(56.dp).background(Color(0xFF5B6570))) {
        if (file == null || !file.isFile) MissingImage(Modifier.fillMaxSize())
        else SubcomposeAsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), error = { MissingImage(Modifier.fillMaxSize()) })
    }
}

/** Template choice row + sheet used when reviewing or editing a photo. */
@Composable
fun TemplatePickerField(templates: List<StampTemplate>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = templates.firstOrNull { it.id == selectedId }
    id.asmaraputra.fieldstamp.feature.common.PickerField(
        label = stringResource(R.string.field_template),
        value = selected?.name ?: stringResource(R.string.template_basic_name),
        onClick = { open = true },
    )
    if (open) {
        id.asmaraputra.fieldstamp.feature.common.OptionSheet(
            title = stringResource(R.string.field_template),
            options = listOf(id.asmaraputra.fieldstamp.feature.common.PickerOption(null, stringResource(R.string.template_basic_name), stringResource(R.string.template_basic_desc))) +
                templates.map { id.asmaraputra.fieldstamp.feature.common.PickerOption(it.id, it.name, "${it.widthPx} × ${it.heightPx} px") },
            selectedId = selectedId,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}
