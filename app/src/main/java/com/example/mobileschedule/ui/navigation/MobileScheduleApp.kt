package com.example.mobileschedule.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

private enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    SCHEDULE("schedule", R.string.tab_schedule, R.drawable.ic_schedule),
    SETTINGS("settings", R.string.tab_settings, R.drawable.ic_settings),
}

@Composable
fun MobileScheduleApp() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            if (Destination.entries.any { it.route == entry?.destination?.route }) {
                NavigationBar {
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
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                            modifier = Modifier.testTag("tab_${destination.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Destination.SCHEDULE.route, modifier = Modifier.padding(padding)) {
            composable(Destination.SCHEDULE.route) {
                ScheduleRoute(onConfigure = { navController.navigate("config/new") },
                    onSettings = { navController.navigate(Destination.SETTINGS.route) },
                    onDisplaySettings = { navController.navigate("schedule/display-settings") },
                    onImport = { navController.navigate("import") })
            }
            composable("schedule/display-settings") {
                ScheduleDisplaySettingsRoute(onBack = { navController.popBackStack() })
            }
            composable(Destination.SETTINGS.route) {
                SettingsRoute(onCreate = { navController.navigate("config/new") },
                    onEdit = { id -> navController.navigate("config/$id") },
                    onImport = { navController.navigate("import") })
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
