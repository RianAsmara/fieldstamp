package id.asmaraputra.fieldstamp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.feature.camera.CameraScreen
import id.asmaraputra.fieldstamp.feature.camera.CaptureReviewScreen
import id.asmaraputra.fieldstamp.feature.home.HomeScreen
import id.asmaraputra.fieldstamp.feature.paywall.PaywallScreen
import id.asmaraputra.fieldstamp.feature.photos.PhotoDetailScreen
import id.asmaraputra.fieldstamp.feature.photos.PhotosScreen
import id.asmaraputra.fieldstamp.feature.projects.ProjectDetailScreen
import id.asmaraputra.fieldstamp.feature.projects.ProjectsScreen
import id.asmaraputra.fieldstamp.feature.reports.ReportBuilderScreen
import id.asmaraputra.fieldstamp.feature.reports.ReportCreateScreen
import id.asmaraputra.fieldstamp.feature.reports.ReportsScreen
import id.asmaraputra.fieldstamp.feature.settings.PrivacyScreen
import id.asmaraputra.fieldstamp.feature.settings.SettingsScreen
import id.asmaraputra.fieldstamp.feature.templates.TemplateEditorScreen
import id.asmaraputra.fieldstamp.feature.templates.TemplatesScreen
import kotlin.reflect.KClass

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch opens the camera; recreation (rotation, process restore) keeps the back stack.
        val openCamera = savedInstanceState == null && !intent.getBooleanExtra(EXTRA_SKIP_CAMERA, false)
        setContent {
            FieldStampTheme {
                FieldStampApp(openCameraOnStart = openCamera)
            }
        }
    }

    companion object {
        /** Lets instrumentation tests start on Home without the camera and its permission dialog. */
        const val EXTRA_SKIP_CAMERA = "skip_camera"
    }
}

private data class TopLevel(val route: Any, val type: KClass<*>, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val topLevel = listOf(
    TopLevel(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TopLevel(PhotosRoute, PhotosRoute::class, R.string.nav_photos, Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    TopLevel(ReportsRoute, ReportsRoute::class, R.string.nav_reports, Icons.Outlined.Description, Icons.Filled.Description),
    TopLevel(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun FieldStampApp(nav: NavHostController = rememberNavController(), openCameraOnStart: Boolean = false) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!openCameraOnStart) return@LaunchedEffect
        val settings = context.container.settings.current()
        // The first launch introduces Home; later launches go straight to the camera, on top of Home
        // so closing it lands there.
        if (!settings.launchedBefore) context.container.settings.markLaunched()
        else if (settings.openCameraOnLaunch) nav.navigate(CameraRoute)
    }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = topLevel.any { item -> destination?.hierarchy?.any { it.hasRoute(item.type) } == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    topLevel.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(item.type) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) {
            composable<HomeRoute> { HomeScreen(nav) }
            composable<PhotosRoute> { PhotosScreen(nav) }
            composable<ReportsRoute> { ReportsScreen(nav) }
            composable<SettingsRoute> { SettingsScreen(nav) }
            composable<CameraRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { CameraScreen(nav) }
            composable<CaptureReviewRoute> { CaptureReviewScreen(nav) }
            composable<PhotoDetailRoute> { PhotoDetailScreen(nav, it.toRoute<PhotoDetailRoute>().photoId) }
            composable<ProjectsRoute> { ProjectsScreen(nav) }
            composable<ProjectDetailRoute> { ProjectDetailScreen(nav, it.toRoute<ProjectDetailRoute>().projectId) }
            composable<TemplatesRoute> { TemplatesScreen(nav) }
            composable<TemplateEditorRoute> { TemplateEditorScreen(nav) }
            composable<ReportCreateRoute> { ReportCreateScreen(nav) }
            composable<ReportBuilderRoute> { ReportBuilderScreen(nav, it.toRoute<ReportBuilderRoute>().reportId) }
            composable<PaywallRoute> { PaywallScreen(nav) }
            composable<PrivacyRoute> { PrivacyScreen(nav) }
        }
    }
}
