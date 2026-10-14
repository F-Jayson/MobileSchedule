package com.example.mobileschedule.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

import com.example.mobileschedule.ui.today.TodayRoute

private enum class Destination(
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
    SettingsUpdateHost(settingsViewModel, showMirror, { showMirror = true }, { showMirror = false })
    Scaffold(
        bottomBar = {
            if (Destination.entries.any { it.route == entry?.destination?.route }) {
                Column(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()) {
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
                                selected = entry?.destination?.route == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(painterResource(destination.icon), contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                },
                                label = {
                                    Text(stringResource(destination.label), maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp))
                                },
                                modifier = Modifier.testTag("tab_${destination.route}"),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Destination.SCHEDULE.route, modifier = Modifier.padding(padding)) {
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
                SettingsRoute(onManageSchedule = { navController.navigate("settings/schedule-management") },
                    onDisplaySettings = { navController.navigate("schedule/display-settings") },
                    onSelectMirror = { showMirror = true }, viewModel = settingsViewModel)
            }
            composable("settings/schedule-management") {
                ScheduleManagementRoute(onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate("config/new") },
                    onEdit = { id -> navController.navigate("config/$id") },
                    onImport = { navController.navigate("import") },
                    viewModel = settingsViewModel)
            }
            composable("import") {
                ImportIntroRoute(onBack = { navController.popBackStack() },
                    onConfigure = { id -> navController.navigate(if (id == null) "config/new" else "config/$id") },
                    onContinue = { id -> navController.navigate("import/login/$id") })
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
                    ZhengfangOnlineReadRoute(semesterId, onExit = returnToSchedule,
                        onOpenSchedule = {
                            returnToSchedule()
                        })
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
}
