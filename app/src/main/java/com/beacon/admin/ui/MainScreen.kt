package com.beacon.admin.ui

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.beacon.admin.screens.*
import com.beacon.admin.ui.devices.DevicesScreen
import com.beacon.admin.ui.geofence.GeofenceSosScreen
import com.beacon.admin.ui.screens.DeviceDetailsScreen
import com.beacon.admin.ui.theme.*

@Composable
fun MainScreen(
    intent: Intent? = null,
    onNavigateToAuth: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val unreadAlertCount by mainViewModel.unreadGeofenceAlertCount.collectAsStateWithLifecycle()

    val tabs = listOf(
        AdminTab.MAP,
        AdminTab.DEVICES,
        AdminTab.HOME,
        AdminTab.NOTIFICATIONS,
        AdminTab.SETTINGS
    )

    // Deep Link Logic
    LaunchedEffect(intent) {
        val deviceId = intent?.getStringExtra("deviceId")
        if (deviceId != null) {
            navController.navigate("geofence_sos?deviceId=$deviceId")
        }
    }

    Scaffold(
        containerColor = ObsidianBase,
        bottomBar = {
            NavigationBar(
                containerColor = GlassSurface,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    val isSelected = currentDestination?.hierarchy?.any { 
                        it.route == tab.route || 
                        (tab == AdminTab.DEVICES && it.route?.startsWith("devices") == true) ||
                        (tab == AdminTab.NOTIFICATIONS && it.route?.startsWith("history") == true) ||
                        (tab == AdminTab.DEVICES && it.route?.startsWith("device_details") == true)
                    } == true

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (tab == AdminTab.NOTIFICATIONS) {
                                mainViewModel.markAlertsAsRead()
                            }

                            val startDest = navController.graph.findStartDestination()
                            val isStartDest = tab.route == startDest.route

                            navController.navigate(tab.route) {
                                popUpTo(startDest.id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                if (!isStartDest) {
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == AdminTab.NOTIFICATIONS && unreadAlertCount > 0) {
                                        Badge(containerColor = BeaconCrimson) {
                                            Text(unreadAlertCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        AdminTab.MAP -> Icons.Rounded.Map
                                        AdminTab.DEVICES -> Icons.Rounded.Devices
                                        AdminTab.HOME -> Icons.Rounded.Home
                                        AdminTab.NOTIFICATIONS -> Icons.Rounded.Notifications
                                        AdminTab.SETTINGS -> Icons.Rounded.Settings
                                    },
                                    contentDescription = tab.title
                                )
                            }
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BeaconCyan,
                            selectedTextColor = BeaconCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = GlassSurfaceBorder
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AdminTab.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AdminTab.HOME.route) { 
                HomeScreen(
                    onNavigateToDevices = { filter ->
                        navController.navigate("devices?filter=$filter")
                    },
                    onNavigateToGeofence = { deviceId ->
                        val route = if (deviceId != null) "geofence_sos?deviceId=$deviceId" else "geofence_sos"
                        navController.navigate(route)
                    },
                    onNavigateToHistory = { deviceId, timestamp ->
                        val route = if (timestamp != null) "history/$deviceId?ts=$timestamp" else "history/$deviceId"
                        navController.navigate(route)
                    }
                ) 
            }
            composable(AdminTab.MAP.route) { 
                MapScreen() 
            }
            composable(
                route = "devices?filter={filter}",
                arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true })
            ) { 
                DevicesScreen(
                    onDeviceClick = { deviceId ->
                        navController.navigate("device_details/$deviceId")
                    }
                )
            }
            composable(
                route = "device_details/{deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                DeviceDetailsScreen(
                    deviceId = deviceId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHistory = { id -> navController.navigate("history/$id") },
                    onNavigateToGeofence = { id -> navController.navigate("geofence_sos?deviceId=$id") }
                )
            }
            composable(
                route = "geofence_sos?deviceId={deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType; nullable = true })
            ) { 
                GeofenceSosScreen() 
            }
            composable(AdminTab.NOTIFICATIONS.route) {
                HistoryScreen() 
            }
            composable(
                route = "history/{deviceId}?ts={ts}",
                arguments = listOf(
                    navArgument("deviceId") { type = NavType.StringType },
                    navArgument("ts") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { 
                HistoryScreen() 
            }
            composable(AdminTab.SETTINGS.route) { 
                SettingsScreen(onSignedOut = onNavigateToAuth)
            }
        }
    }
}
