package id.asmaraputra.fieldstamp.feature.home

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.core.view.WindowCompat
import id.asmaraputra.fieldstamp.core.designsystem.FsCard
import id.asmaraputra.fieldstamp.core.designsystem.Motion
import id.asmaraputra.fieldstamp.core.designsystem.PlexMono
import id.asmaraputra.fieldstamp.core.designsystem.pressScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.CameraRoute
import id.asmaraputra.fieldstamp.app.PhotoDetailRoute
import id.asmaraputra.fieldstamp.app.PhotosRoute
import id.asmaraputra.fieldstamp.app.ProjectsRoute
import id.asmaraputra.fieldstamp.app.ReportBuilderRoute
import id.asmaraputra.fieldstamp.app.ReportCreateRoute
import id.asmaraputra.fieldstamp.app.TemplatesRoute
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.core.designsystem.CaptureButton
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.LoadingState
import id.asmaraputra.fieldstamp.core.designsystem.FsDivider
import id.asmaraputra.fieldstamp.core.designsystem.ListRow
import id.asmaraputra.fieldstamp.core.designsystem.PhotoThumbnail
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.feature.common.appFile
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.PhotoListItem
import id.asmaraputra.fieldstamp.core.model.Project
import id.asmaraputra.fieldstamp.core.model.StampTemplate
import id.asmaraputra.fieldstamp.feature.common.ProjectActivityFields
import id.asmaraputra.fieldstamp.feature.common.rememberPhotoImport
import id.asmaraputra.fieldstamp.feature.reports.StatusIcon
import id.asmaraputra.fieldstamp.feature.reports.statusLabel
import id.asmaraputra.fieldstamp.feature.templates.TemplatePickerField
import id.asmaraputra.fieldstamp.report.ReportSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val projects: List<Project> = emptyList(),
    val activities: List<FieldActivity> = emptyList(),
    val projectId: Long? = null,
    val activityId: Long? = null,
    val todayPhotos: List<PhotoListItem> = emptyList(),
    val templates: List<StampTemplate> = emptyList(),
    val selectedTemplateId: Long? = null,
    val totalPhotos: Int = 0,
    val latestReport: ReportSummary? = null,
    val loaded: Boolean = false,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val today = flow { emit(LocalDate.now()) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(
        c.settings.settings,
        c.projects.observeProjects(),
        c.projects.observeActivities(),
        today.flatMapLatest { day -> c.photos.observeDay(day) },
        combine(c.templates.observeAll(), c.photos.observeCount(), c.reports.observeSummaries(), ::Triple),
    ) { settings, projects, activities, todayPhotos, (templates, count, reports) ->
        HomeState(
            projects = projects,
            activities = activities,
            projectId = settings.currentProjectId?.takeIf { id -> projects.any { it.id == id } },
            activityId = settings.currentActivityId?.takeIf { id -> activities.any { it.id == id } },
            todayPhotos = todayPhotos,
            templates = templates,
            selectedTemplateId = settings.selectedTemplateId?.takeIf { id -> templates.any { it.id == id } },
            totalPhotos = count,
            latestReport = reports.maxByOrNull { it.report.updatedAt },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setProject(id: Long?) = viewModelScope.launch {
        // Changing the project clears an activity that belongs to a different project.
        val activity = state.value.activities.firstOrNull { it.id == state.value.activityId }
        c.settings.setCaptureContext(id, activity?.takeIf { it.projectId == id }?.id)
    }

    fun setActivity(id: Long?) = viewModelScope.launch { c.settings.setCaptureContext(state.value.projectId, id) }

    fun setTemplate(id: Long?) = viewModelScope.launch { c.settings.setSelectedTemplate(id) }

    fun createProject(name: String) = viewModelScope.launch {
        val id = c.projects.createProject(name, "")
        c.settings.setCaptureContext(id, null)
    }

    fun createActivity(name: String) = viewModelScope.launch {
        val id = c.projects.createActivity(state.value.projectId, name, "")
        c.settings.setCaptureContext(state.value.projectId, id)
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val import = rememberPhotoImport(nav) {
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.import_failed)) }
    }
    LightStatusBarIcons()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // The capture dock stays fixed above the navigation bar: the camera is always one tap away.
        bottomBar = { CaptureDock(onCapture = { nav.navigate(CameraRoute) }, onImport = import) },
        // The band draws behind the status bar and pads itself.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.loaded) {
            LoadingState(Modifier.padding(padding).statusBarsPadding())
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Band(state.today, state.todayPhotos.size)

            // The context card overlaps the band's lower edge: what the next photo is filed under.
            FsCard(Modifier.padding(horizontal = Spacing.gutter).overlapAbove(BAND_OVERLAP)) {
                ProjectActivityFields(
                    projects = state.projects,
                    activities = state.activities,
                    projectId = state.projectId,
                    activityId = state.activityId,
                    onProjectChange = vm::setProject,
                    onActivityChange = vm::setActivity,
                    onCreateProject = vm::createProject,
                    onCreateActivity = vm::createActivity,
                )
                FsDivider(inset = true)
                TemplatePickerField(templates = state.templates, selectedId = state.selectedTemplateId, onSelect = vm::setTemplate)
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(
                text = stringResource(R.string.home_today),
                trailing = if (state.todayPhotos.isEmpty()) null else {
                    { TextButton(onClick = { nav.navigate(PhotosRoute) }) { Text(stringResource(R.string.action_see_all)) } }
                },
            )
            if (state.todayPhotos.isEmpty()) {
                EmptyToday(Modifier.padding(horizontal = Spacing.gutter))
            } else {
                TodayStrip(state.todayPhotos, onOpen = { nav.navigate(PhotoDetailRoute(it)) })
            }

            state.latestReport?.let { latest ->
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader(stringResource(R.string.home_latest_report))
                FsCard(Modifier.padding(horizontal = Spacing.gutter)) {
                    ListRow(
                        title = latest.report.title,
                        supporting = statusLabel(latest.report) + " · " + pluralStringResource(R.plurals.photo_count, latest.photoCount, latest.photoCount),
                        titleMaxLines = 2,
                        leading = { StatusIcon(latest.report) },
                        trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                        onClick = { nav.navigate(ReportBuilderRoute(latest.report.id)) },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(stringResource(R.string.home_manage))
            FsCard(Modifier.padding(horizontal = Spacing.gutter)) {
                ListRow(
                    title = stringResource(R.string.projects_title),
                    supporting = if (state.projects.isEmpty()) stringResource(R.string.home_projects_empty)
                    else pluralStringResource(R.plurals.project_count, state.projects.size, state.projects.size),
                    leading = { RowIcon(Icons.Outlined.Folder) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    onClick = { nav.navigate(ProjectsRoute) },
                )
                FsDivider(inset = true)
                ListRow(
                    title = stringResource(R.string.templates_title),
                    supporting = if (state.templates.isEmpty()) stringResource(R.string.home_templates_empty)
                    else pluralStringResource(R.plurals.template_count, state.templates.size, state.templates.size),
                    leading = { RowIcon(Icons.Outlined.Layers) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    onClick = { nav.navigate(TemplatesRoute) },
                )
                FsDivider(inset = true)
                ListRow(
                    title = stringResource(R.string.report_create),
                    supporting = stringResource(R.string.home_report_hint),
                    leading = { RowIcon(Icons.Outlined.PostAdd) },
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    onClick = { nav.navigate(ReportCreateRoute) },
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
        // Scrolled content never shows through the status bar: it keeps the band color.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(FieldStampTheme.extended.band))
        }
    }
}

private val BAND_OVERLAP = 40.dp
private val HERO_SIZE = DpSize(200.dp, 266.dp)
private val THUMB_SIZE = DpSize(132.dp, 176.dp)

/** The band is dark teal in both themes, so status bar icons stay light while Home is shown. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/** Places the element [amount] higher and gives that space back, so it overlaps whatever is above it. */
private fun Modifier.overlapAbove(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
}

/** Identity band: weekday and zone, the date as the headline, and today's photo count. */
@Composable
private fun Band(today: LocalDate, todayCount: Int) {
    val ext = FieldStampTheme.extended
    val zone = Formats.zoneLabel(ZonedDateTime.now())
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Formats.locale).uppercase(Formats.locale)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(ext.band)
            .statusBarsPadding()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xl, bottom = Spacing.xl + BAND_OVERLAP),
    ) {
        Text("$weekday · $zone", style = FieldStampTheme.mono.label, color = ext.onBandMuted)
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                Formats.longDate(today),
                style = MaterialTheme.typography.headlineMedium,
                color = ext.onBand,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Spacer(Modifier.width(Spacing.md))
            TodayCounter(todayCount)
        }
    }
}

/** Big count with a rolling digit change, so a new photo visibly lands here after saving. */
@Composable
private fun TodayCounter(count: Int) {
    val ext = FieldStampTheme.extended
    val label = pluralStringResource(R.plurals.photo_count_today, count, count)
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics { contentDescription = label }) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(Motion.MEDIUM, easing = Motion.enter)) { if (up) it else -it } + fadeIn(tween(Motion.SHORT)))
                    .togetherWith(slideOutVertically(tween(Motion.SHORT, easing = Motion.exit)) { if (up) -it else it } + fadeOut(tween(Motion.INSTANT)))
                    .using(SizeTransform(clip = true))
            },
            label = "todayCount",
        ) { value ->
            Text(value.toString(), style = MaterialTheme.typography.displaySmall.copy(fontFamily = PlexMono), color = ext.onBand)
        }
        Text(stringResource(R.string.home_today_count_label), style = MaterialTheme.typography.labelMedium, color = ext.onBandMuted)
    }
}

/** Latest photo large, earlier ones smaller; new photos scale in and the row makes room. */
@Composable
private fun TodayStrip(photos: List<PhotoListItem>, onOpen: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        itemsIndexed(photos, key = { _, item -> item.photo.id }) { index, item ->
            val size = if (index == 0) HERO_SIZE else THUMB_SIZE
            val interaction = remember { MutableInteractionSource() }
            // Time sits under the thumbnail so it never covers the stamp printed on the photo.
            Column(
                Modifier
                    .animateItem(fadeInSpec = tween(Motion.MEDIUM), placementSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .width(size.width),
            ) {
                PhotoThumbnail(
                    file = appFile(item.photo.thumbnailPath),
                    contentDescription = item.photo.caption.ifBlank { stringResource(R.string.photo_no_caption) },
                    modifier = Modifier
                        .size(size)
                        .pressScale(interaction)
                        .clickable(interactionSource = interaction, indication = LocalIndication.current) { onOpen(item.photo.id) },
                )
                Text(
                    Formats.time(item.photo.captureTime.zoned),
                    style = FieldStampTheme.mono.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** Empty today: a viewfinder with a stamp bar, the same picture as the app icon, plus one sentence. */
@Composable
private fun EmptyToday(modifier: Modifier = Modifier) {
    FsCard(modifier) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            val ink = MaterialTheme.colorScheme.primary
            Canvas(Modifier.size(72.dp).clearAndSetSemantics {}) {
                val stroke = 3.dp.toPx()
                val arm = size.minDimension * 0.28f
                val inset = stroke / 2
                val (w, h) = size.width - inset to size.height - inset
                val corners = listOf(
                    Offset(inset, inset) to listOf(Offset(inset + arm, inset), Offset(inset, inset + arm)),
                    Offset(w, inset) to listOf(Offset(w - arm, inset), Offset(w, inset + arm)),
                    Offset(inset, h) to listOf(Offset(inset + arm, h), Offset(inset, h - arm)),
                    Offset(w, h) to listOf(Offset(w - arm, h), Offset(w, h - arm)),
                )
                corners.forEach { (corner, ends) -> ends.forEach { drawLine(ink, corner, it, stroke) } }
                val barH = size.height * 0.16f
                drawRoundRect(
                    color = ink.copy(alpha = 0.35f),
                    topLeft = Offset(size.width * 0.2f, size.height * 0.62f),
                    size = Size(size.width * 0.6f, barH),
                    cornerRadius = CornerRadius(barH / 3),
                )
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_today_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.home_today_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Leading icon on a tinted tile, so grouped rows scan by shape before text. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CaptureDock(onCapture: () -> Unit, onImport: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(
                onClick = onImport,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(Spacing.capture),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.action_import_photo))
            }
            CaptureButton(
                label = stringResource(R.string.action_take_photo),
                onClick = onCapture,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
