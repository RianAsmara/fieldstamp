package id.asmaraputra.fieldstamp.feature.templates

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.toRoute
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.TemplateEditorRoute
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.core.designsystem.ConfirmDialog
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.Notice
import id.asmaraputra.fieldstamp.core.designsystem.NoticeTone
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.designsystem.SwitchRow
import id.asmaraputra.fieldstamp.core.image.BitmapIO
import id.asmaraputra.fieldstamp.core.image.StampData
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.BackdropStyle
import id.asmaraputra.fieldstamp.core.model.StampAnchor
import id.asmaraputra.fieldstamp.core.model.StampBox
import id.asmaraputra.fieldstamp.core.image.ColorSampler
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.width
import id.asmaraputra.fieldstamp.core.model.StampField
import id.asmaraputra.fieldstamp.core.model.StampTextColor
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import id.asmaraputra.fieldstamp.core.storage.FileStore
import id.asmaraputra.fieldstamp.data.PhotoFilter
import id.asmaraputra.fieldstamp.data.StagedTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class EditorMode { BASIC, EXISTING, NEW }

data class EditorState(
    val loaded: Boolean = false,
    val name: String = "",
    val config: TemplateConfig = TemplateConfig(),
    val templatePath: String? = null,
    val sourceMissing: Boolean = false,
    val dirty: Boolean = false,
)

class TemplateEditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<TemplateEditorRoute>()
    val mode = when {
        route.stagedName.isNotEmpty() -> EditorMode.NEW
        route.templateId < 0 -> EditorMode.BASIC
        else -> EditorMode.EXISTING
    }
    private val staged = if (mode == EditorMode.NEW) {
        StagedTemplate(File(c.files.dir(FileStore.Area.TMP), route.stagedName), route.stagedWidth, route.stagedHeight)
    } else null

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _previewFailed = MutableStateFlow(false)
    val previewFailed: StateFlow<Boolean> = _previewFailed.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private var background: File? = null
    private var sampleData: StampData? = null

    init {
        viewModelScope.launch {
            _state.value = when (mode) {
                EditorMode.BASIC -> EditorState(true, "", c.settings.current().basicStampConfig)
                EditorMode.NEW -> {
                    val sourceRel = staged?.file?.let { c.files.relativize(it) }
                    EditorState(true, "", TemplateConfig(), sourceRel, sourceMissing = staged?.file?.isFile != true)
                }
                EditorMode.EXISTING -> {
                    val t = c.templates.get(route.templateId)
                    if (t == null) EditorState(true, sourceMissing = true)
                    else EditorState(true, t.name, t.config, t.sourcePath, sourceMissing = !c.templates.isSourceAvailable(t))
                }
            }
            background = runCatching { previewBackground() }.getOrNull()
            if (background == null) _previewFailed.value = true
            sampleData = sampleData()
            renderPreview()
        }
    }

    /** The user's most recent photo when one exists; otherwise a neutral generated background. */
    private suspend fun previewBackground(): File = withContext(Dispatchers.IO) {
        val latest = c.photos.observe(PhotoFilter()).first().firstOrNull()?.photo
        latest?.let { c.files.resolve(it.originalPath) }?.takeIf { decodable(it) } ?: run {
            val relative = "${FileStore.Area.TMP.dir}/template_preview_bg.jpg"
            val file = c.files.resolve(relative)
            // Regenerate when missing, empty or corrupt (e.g. truncated by a power loss mid-write).
            if (!decodable(file)) {
                val bmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawRect(0f, 0f, 1600f, 1200f, Paint().apply { shader = LinearGradient(0f, 0f, 1600f, 1200f, 0xFF7C8A86.toInt(), 0xFF3F4B52.toInt(), Shader.TileMode.CLAMP) })
                try {
                    c.files.writeAtomically(relative) { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                } finally {
                    bmp.recycle()
                }
            }
            file
        }
    }

    private fun decodable(file: File): Boolean =
        file.isFile && file.length() > 0 && runCatching { BitmapIO.rawSize(file) }.isSuccess

    private suspend fun sampleData(): StampData {
        val settings = c.settings.current()
        val project = c.projects.observeProjects().first().firstOrNull { it.id == settings.currentProjectId }?.name
        val activity = c.projects.observeActivities().first().firstOrNull { it.id == settings.currentActivityId }?.name
        return StampData(
            captureTime = CaptureTime.now(),
            projectName = project ?: SAMPLE_PROJECT,
            activityName = activity ?: SAMPLE_ACTIVITY,
            caption = SAMPLE_CAPTION,
            category = SAMPLE_CATEGORY,
            location = GeoPoint(-6.175392, 106.827153, 8f, System.currentTimeMillis()),
            locationName = SAMPLE_PLACE,
        )
    }

    @OptIn(FlowPreview::class)
    private fun renderPreview() = viewModelScope.launch {
        state.debounce(200).collectLatest { s ->
            val bg = background ?: return@collectLatest
            val data = sampleData ?: return@collectLatest
            val templatePath = s.templatePath?.takeIf { !s.sourceMissing }
            runCatching { c.processor.preview(bg, templatePath, s.config, data, 1080, drawGuide = s.config.anchor == StampAnchor.CUSTOM) }
                .onSuccess { _preview.value = it; _previewFailed.value = false }
                .onFailure { _previewFailed.value = true }
        }
    }

    private fun edit(block: (EditorState) -> EditorState) { _state.value = block(_state.value).copy(dirty = true) }
    private fun config(block: (TemplateConfig) -> TemplateConfig) = edit { it.copy(config = block(it.config)) }

    fun setName(v: String) = edit { it.copy(name = v.take(80)) }
    fun toggle(field: StampField, enabled: Boolean) = config { cfg -> cfg.copy(fields = cfg.fields.map { if (it.field == field) it.copy(enabled = enabled) else it }) }
    fun move(field: StampField, delta: Int) = config { cfg ->
        val list = cfg.fields.toMutableList()
        val i = list.indexOfFirst { it.field == field }
        val j = i + delta
        if (i < 0 || j !in list.indices) cfg else cfg.copy(fields = list.apply { add(j, removeAt(i)) })
    }
    fun setAnchor(a: StampAnchor) {
        config { it.copy(anchor = a) }
        if (a == StampAnchor.CUSTOM) resampleIfSolid()
    }

    fun setBox(transform: (StampBox) -> StampBox) {
        config { it.copy(box = transform(it.box).normalized()) }
        resampleIfSolid()
    }

    fun setBackdropStyle(style: BackdropStyle) {
        config { it.copy(backdropStyle = style, backdrop = style != BackdropStyle.NONE) }
        if (style == BackdropStyle.SOLID) resampleIfSolid()
    }

    private var templatePixels: Triple<IntArray, Int, Int>? = null
    private var sampleJob: kotlinx.coroutines.Job? = null

    /**
     * With a solid backdrop on a template, fill with the template's own dominant color under the text area,
     * so placeholder text printed in the PNG (e.g. "[NAMA KEGIATAN]") is covered by a matching panel.
     */
    private fun resampleIfSolid() {
        val s = state.value
        if (s.config.effectiveBackdrop != BackdropStyle.SOLID) return
        val path = s.templatePath?.takeIf { !s.sourceMissing } ?: return
        sampleJob?.cancel()
        sampleJob = viewModelScope.launch(Dispatchers.Default) {
            val (px, w, h) = templatePixels ?: runCatching {
                val bmp = BitmapIO.decodeUprightMaxEdge(c.files.resolve(path), 800)
                val arr = IntArray(bmp.width * bmp.height)
                bmp.getPixels(arr, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                Triple(arr, bmp.width, bmp.height).also { bmp.recycle() }
            }.getOrNull()?.also { templatePixels = it } ?: return@launch
            val cfg = state.value.config
            val area = if (cfg.anchor == StampAnchor.CUSTOM) cfg.box.normalized() else StampBox(0f, 0.7f, 1f, 0.3f)
            val x0 = (area.left * w).toInt().coerceIn(0, w - 1)
            val y0 = (area.top * h).toInt().coerceIn(0, h - 1)
            val x1 = ((area.left + area.width) * w).toInt().coerceIn(x0 + 1, w)
            val y1 = ((area.top + area.height) * h).toInt().coerceIn(y0 + 1, h)
            val region = IntArray((x1 - x0) * (y1 - y0))
            var i = 0
            for (y in y0 until y1) for (x in x0 until x1) region[i++] = px[y * w + x]
            val color = ColorSampler.dominantOpaqueColor(region) ?: 0xFFFFFFFF.toInt()
            withContext(Dispatchers.Main) { config { it.copy(solidColor = color) } }
        }
    }
    fun setTextSize(v: Float) = config { it.copy(textSizePercent = v) }
    fun setColor(v: StampTextColor) = config { it.copy(textColor = v) }
    fun set24h(v: Boolean) = config { it.copy(use24Hour = v) }
    fun setCustomText(v: String) = config { it.copy(customText = v.take(120)) }

    val canSave: Boolean get() = mode == EditorMode.BASIC || state.value.name.isNotBlank()

    fun save(onDone: () -> Unit) {
        if (_saving.value || !canSave) return
        _saving.value = true
        viewModelScope.launch {
            val s = state.value
            when (mode) {
                EditorMode.BASIC -> c.settings.setBasicStampConfig(s.config)
                EditorMode.EXISTING -> c.templates.update(route.templateId, s.name, s.config)
                EditorMode.NEW -> staged?.let {
                    val id = c.templates.save(it, s.name, s.config)
                    if (c.settings.current().selectedTemplateId == null) c.settings.setSelectedTemplate(id)
                }
            }
            onDone()
        }
    }

    fun discard() { staged?.let { c.templates.discard(it) } }

    companion object {
        // Preview-only sample values; never stored on a photo.
        const val SAMPLE_PROJECT = "Contoh proyek"
        const val SAMPLE_ACTIVITY = "Contoh kegiatan"
        const val SAMPLE_CAPTION = "Contoh keterangan foto kegiatan"
        const val SAMPLE_CATEGORY = "Contoh"
        const val SAMPLE_PLACE = "Contoh nama lokasi, Kecamatan, Kabupaten"
    }
}

@Composable
fun TemplateEditorScreen(nav: NavController) {
    val vm = appViewModel { c, h -> TemplateEditorViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val previewFailed by vm.previewFailed.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    val close = {
        if (vm.mode == EditorMode.NEW) vm.discard()
        nav.popBackStack()
    }
    BackHandler(enabled = state.dirty || vm.mode == EditorMode.NEW) { confirmLeave = true }

    val title = when (vm.mode) {
        EditorMode.BASIC -> stringResource(R.string.template_basic_name)
        EditorMode.NEW -> stringResource(R.string.template_new_title)
        EditorMode.EXISTING -> state.name.ifBlank { stringResource(R.string.templates_title) }
    }

    Scaffold(
        topBar = {
            FsTopBar(title, onBack = { if (state.dirty || vm.mode == EditorMode.NEW) confirmLeave = true else nav.popBackStack() }) {
                TextButton(onClick = { vm.save { nav.popBackStack() } }, enabled = !saving && vm.canSave && state.loaded) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().height(320.dp).clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = preview
                when {
                    bmp != null -> Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.template_preview_description), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    previewFailed -> Text(
                        stringResource(R.string.template_preview_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.xl),
                    )
                    else -> CircularProgressIndicator()
                }
            }
            Text(
                stringResource(R.string.template_preview_sample_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
            if (state.sourceMissing && vm.mode != EditorMode.BASIC) {
                Notice(stringResource(R.string.template_source_missing_long), NoticeTone.ERROR, Modifier.padding(horizontal = Spacing.gutter))
            }
            if (vm.mode != EditorMode.BASIC) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    label = { Text(stringResource(R.string.field_template_name)) },
                    singleLine = true,
                    isError = state.name.isBlank() && state.dirty,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
            } else {
                Text(
                    stringResource(R.string.template_basic_editor_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
            }

            SectionHeader(stringResource(R.string.template_fields_heading))
            Text(
                stringResource(R.string.template_fields_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            val fields = state.config.normalized().fields
            fields.forEachIndexed { index, setting ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = setting.enabled, role = Role.Checkbox, onValueChange = { vm.toggle(setting.field, it) })
                        .padding(start = Spacing.sm, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = setting.enabled, onCheckedChange = null, modifier = Modifier.padding(Spacing.md))
                    Text(stringResource(fieldLabel(setting.field)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.move(setting.field, -1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
                    }
                    IconButton(onClick = { vm.move(setting.field, 1) }, enabled = index < fields.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
                    }
                }
            }
            if (fields.any { it.field == StampField.CUSTOM_TEXT && it.enabled }) {
                OutlinedTextField(
                    value = state.config.customText,
                    onValueChange = vm::setCustomText,
                    label = { Text(stringResource(R.string.field_custom_text)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
            }
            FsDivider()

            SectionHeader(stringResource(R.string.template_position_heading))
            FlowRow(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)) {
                StampAnchor.entries.forEach { anchor ->
                    FilterChip(selected = state.config.anchor == anchor, onClick = { vm.setAnchor(anchor) }, label = { Text(stringResource(anchorLabel(anchor))) })
                }
            }
            if (state.config.anchor == StampAnchor.CUSTOM) {
                Text(
                    stringResource(R.string.template_box_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
                val box = state.config.box.normalized()
                BoxSlider(R.string.template_box_left, box.left, 0f..0.95f) { v -> vm.setBox { it.copy(left = v) } }
                BoxSlider(R.string.template_box_top, box.top, 0f..0.95f) { v -> vm.setBox { it.copy(top = v) } }
                BoxSlider(R.string.template_box_width, box.width, 0.05f..1f) { v -> vm.setBox { it.copy(width = v) } }
                BoxSlider(R.string.template_box_height, box.height, 0.03f..1f) { v -> vm.setBox { it.copy(height = v) } }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(if (state.config.anchor == StampAnchor.CUSTOM) R.string.template_text_size_max else R.string.template_text_size, state.config.textSizePercent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            Slider(
                value = state.config.textSizePercent,
                onValueChange = vm::setTextSize,
                valueRange = TemplateConfig.MIN_TEXT_PERCENT..TemplateConfig.MAX_TEXT_PERCENT,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            Text(stringResource(R.string.template_text_color), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Spacing.gutter))
            Spacer(Modifier.height(Spacing.xs))
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth()) {
                StampTextColor.entries.forEachIndexed { i, color ->
                    SegmentedButton(
                        selected = state.config.textColor == color,
                        onClick = { vm.setColor(color) },
                        shape = SegmentedButtonDefaults.itemShape(i, StampTextColor.entries.size),
                    ) { Text(stringResource(if (color == StampTextColor.WHITE) R.string.color_white else R.string.color_black)) }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.template_backdrop), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Spacing.gutter))
            Spacer(Modifier.height(Spacing.xs))
            val hasTemplate = state.templatePath != null && !state.sourceMissing
            val styles = listOf(BackdropStyle.SHADE, BackdropStyle.SOLID, BackdropStyle.NONE)
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth()) {
                styles.forEachIndexed { i, style ->
                    SegmentedButton(
                        selected = state.config.effectiveBackdrop == style,
                        onClick = { vm.setBackdropStyle(style) },
                        shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                    ) {
                        Text(
                            stringResource(
                                when (style) {
                                    BackdropStyle.SHADE -> R.string.backdrop_shade
                                    BackdropStyle.SOLID -> if (hasTemplate) R.string.backdrop_template_color else R.string.backdrop_white
                                    BackdropStyle.NONE -> R.string.backdrop_none
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                stringResource(if (state.config.effectiveBackdrop == BackdropStyle.SOLID && hasTemplate) R.string.backdrop_template_hint else R.string.template_backdrop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            SwitchRow(stringResource(R.string.template_24h), state.config.use24Hour, vm::set24h)
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            body = stringResource(R.string.editor_discard_body),
            confirmLabel = stringResource(R.string.action_discard),
            destructive = true,
            onConfirm = { confirmLeave = false; close() },
            onDismiss = { confirmLeave = false },
        )
    }
}

fun fieldLabel(field: StampField): Int = when (field) {
    StampField.DATE_TIME -> R.string.stamp_field_datetime
    StampField.PROJECT -> R.string.stamp_field_project
    StampField.ACTIVITY -> R.string.stamp_field_activity
    StampField.CAPTION -> R.string.stamp_field_caption
    StampField.CATEGORY -> R.string.stamp_field_category
    StampField.COORDINATES -> R.string.stamp_field_coordinates
    StampField.LOCATION_NAME -> R.string.stamp_field_place
    StampField.CUSTOM_TEXT -> R.string.stamp_field_custom
}

private fun anchorLabel(anchor: StampAnchor): Int = when (anchor) {
    StampAnchor.TOP_LEFT -> R.string.anchor_top_left
    StampAnchor.TOP_RIGHT -> R.string.anchor_top_right
    StampAnchor.BOTTOM_LEFT -> R.string.anchor_bottom_left
    StampAnchor.BOTTOM_CENTER -> R.string.anchor_bottom_center
    StampAnchor.BOTTOM_RIGHT -> R.string.anchor_bottom_right
    StampAnchor.CUSTOM -> R.string.anchor_custom
}

@Composable
private fun BoxSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label, (value * 100).roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(112.dp),
        )
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
