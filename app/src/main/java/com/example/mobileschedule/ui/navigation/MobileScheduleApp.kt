package com.example.mobileschedule.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mobileschedule.R
import com.example.mobileschedule.ui.importentry.ImportIntroRoute
import com.example.mobileschedule.ui.importentry.ZhengfangOnlineReadRoute
import com.example.mobileschedule.ui.schedule.ScheduleRoute
import com.example.mobileschedule.ui.schedule.ScheduleDisplaySettingsRoute
import com.example.mobileschedule.ui.settings.SettingsConfigRoute
import com.example.mobileschedule.ui.settings.SettingsRoute
import com.example.mobileschedule.ui.settings.SettingsHomeViewModel
import com.example.mobileschedule.ui.settings.SettingsUpdateHost
import com.example.mobileschedule.ui.settings.ScheduleManagementRoute
import com.example.mobileschedule.ui.about.AboutRoute
import com.example.mobileschedule.ui.about.ChangelogRoute
import com.example.mobileschedule.ui.about.LicensesRoute
import com.example.mobileschedule.ui.today.TodayRoute
import com.example.mobileschedule.ui.widget.WidgetSettingsRoute

internal enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    SCHEDULE("schedule", R.string.tab_schedule, R.drawable.ic_schedule),
    TODAY("today", R.string.tab_today, R.drawable.ic_today),
    SETTINGS("settings", R.string.tab_settings, R.drawable.ic_settings),
}

@Composable
fun MobileScheduleApp(
    initialCourseId: Long? = null,
    onClearInitialCourseId: () -> Unit = {},
    initialTab: String? = null,
    onClearInitialTab: () -> Unit = {},
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val settingsViewModel: SettingsHomeViewModel = hiltViewModel()
    var showMirror by remember { mutableStateOf(false) }
    val navigationHeight = 48.dp + 16.dp * (LocalDensity.current.fontScale.coerceAtLeast(1f) - 1f)
    LaunchedEffect(settingsViewModel) { settingsViewModel.checkUpdateOnStartup() }
    LaunchedEffect(initialCourseId) {
        if (initialCourseId != null && initialCourseId > 0) {
            if (entry?.destination?.route != Destination.SCHEDULE.route) {
                navController.navigate(Destination.SCHEDULE.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            val targetRoute = when (initialTab) {
                "today" -> Destination.TODAY.route
                "schedule" -> Destination.SCHEDULE.route
                "settings" -> Destination.SETTINGS.route
                else -> null
            }
            if (targetRoute != null && entry?.destination?.route != targetRoute) {
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onClearInitialTab()
        }
    }
    SettingsUpdateHost(settingsViewModel, showMirror, { showMirror = true }, { showMirror = false })

    AdaptiveNavigationScaffold(
        currentRoute = entry?.destination?.route,
        onNavigate = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) {
        AppNavHost(
            navController = navController,
            settingsViewModel = settingsViewModel,
            initialCourseId = initialCourseId,
            onClearInitialCourseId = onClearInitialCourseId,
            onShowMirror = { showMirror = true },
        )
    }
}

@Composable
internal fun AdaptiveNavigationScaffold(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    isExpandedOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val isExpanded = isExpandedOverride ?: (maxWidth >= 840.dp)
        val showNavigation = Destination.entries.any { it.route == currentRoute }

        if (isExpanded) {
            Row(Modifier.fillMaxSize()) {
                if (showNavigation) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("main_navigation_rail"),
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Spacer(Modifier.height(20.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_schedule),
                                contentDescription = "App Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                        },
                    ) {
                        Destination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = { onNavigate(destination.route) },
                                icon = {
                                    Icon(
                                        painterResource(destination.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(destination.label),
                                        maxLines = 1,
                                    )
                                },
                                modifier = Modifier.testTag("rail_${destination.route}"),
                            )
                        }
                    }

                    VerticalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    content()
                }
            }
        } else {
            val navigationHeight = 48.dp + 16.dp * (LocalDensity.current.fontScale.coerceAtLeast(1f) - 1f)
            Scaffold(
                bottomBar = {
                    if (showNavigation) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .navigationBarsPadding(),
                        ) {
                            androidx.compose.material3.HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            NavigationBar(
                                modifier = Modifier.height(navigationHeight).testTag("main_bottom_navigation"),
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets(0),
                            ) {
                                Destination.entries.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentRoute == destination.route,
                                        onClick = { onNavigate(destination.route) },
                                        icon = {
                                            Icon(
                                                painterResource(destination.icon),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        label = {
                                            Text(
                                                stringResource(destination.label),
                                                maxLines = 1,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                            )
                                        },
                                        modifier = Modifier.testTag("tab_${destination.route}"),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    settingsViewModel: SettingsHomeViewModel,
    initialCourseId: Long?,
    onClearInitialCourseId: () -> Unit,
    onShowMirror: () -> Unit,
) {
    NavHost(navController, startDestination = Destination.SCHEDULE.route) {
        composable(Destination.SCHEDULE.route) {
            ScheduleRoute(
                onConfigure = { navController.navigate("config/new") },
                onSettings = { navController.navigate(Destination.SETTINGS.route) },
                onImport = { navController.navigate("import") },
                initialArrangementId = initialCourseId,
                onClearInitialArrangementId = onClearInitialCourseId,
            )
        }
        composable(Destination.TODAY.route) {
            TodayRoute(
                onConfigure = { navController.navigate("config/new") },
                onSettings = { navController.navigate(Destination.SETTINGS.route) },
                onImport = { navController.navigate("import") },
            )
        }
        composable("schedule/display-settings") {
            ScheduleDisplaySettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(Destination.SETTINGS.route) {
            SettingsRoute(
                onManageSchedule = { navController.navigate("settings/schedule-management") },
                onDisplaySettings = { navController.navigate("schedule/display-settings") },
                onWidgetSettings = { navController.navigate("settings/widget") },
                onAbout = { navController.navigate("settings/about") },
                onChangelog = { navController.navigate("settings/changelog") },
                onSelectMirror = onShowMirror,
                viewModel = settingsViewModel,
            )
        }
        composable("settings/widget") {
            WidgetSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable("settings/about") {
            AboutRoute(
                onBack = { navController.popBackStack() },
                onOpenLicenses = { navController.navigate("settings/about/licenses") },
                onOpenChangelog = { navController.navigate("settings/changelog") },
            )
        }
        composable("settings/about/licenses") {
            LicensesRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings/changelog") {
            ChangelogRoute(
                onBack = { navController.popBackStack() },
                onStartUpdate = { info ->
                    settingsViewModel.startDownload(info)
                },
            )
        }
        composable("settings/schedule-management") {
            ScheduleManagementRoute(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate("config/new") },
                onEdit = { id -> navController.navigate("config/$id") },
                onImport = { navController.navigate("import") },
                viewModel = settingsViewModel,
            )
        }
        composable("import") {
            ImportIntroRoute(
                onBack = { navController.popBackStack() },
                onConfigure = { id -> navController.navigate(if (id == null) "config/new" else "config/$id") },
                onContinue = { id -> navController.navigate("import/login/$id") },
            )
        }
        composable("import/login/{semesterId}") { loginEntry ->
            val semesterId = loginEntry.arguments?.getString("semesterId")?.toLongOrNull()
            if (semesterId != null) {
                val returnToSchedule: () -> Unit = {
                    navController.navigate(Destination.SCHEDULE.route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                ZhengfangOnlineReadRoute(
                    semesterId,
                    onExit = returnToSchedule,
                    onOpenSchedule = returnToSchedule,
                )
            }
        }
        composable("config/new") {
            SettingsConfigRoute(null, onBack = { navController.popBackStack() })
        }
        composable("config/{semesterId}") { configEntry ->
            val semesterId = configEntry.arguments?.getString("semesterId")?.toLongOrNull()
            SettingsConfigRoute(semesterId, onBack = { navController.popBackStack() })
        }
    }
}
