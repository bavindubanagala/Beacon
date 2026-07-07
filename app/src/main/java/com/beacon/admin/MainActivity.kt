package com.beacon.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.google.firebase.firestore.FirebaseFirestore
import com.beacon.admin.ui.theme.BeaconAdminTheme
import com.beacon.admin.screens.*

import com.beacon.admin.auth.AuthManager
import com.beacon.admin.repository.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Map : Screen("map_screen", "Map", Icons.Rounded.Map)
    object Devices : Screen("device_list", "Devices", Icons.Rounded.Devices)
    object Home : Screen("home_screen", "Home", Icons.Rounded.Home)
    object Alerts : Screen("alerts", "Alerts", Icons.Rounded.Notifications)
    object History : Screen("history_root", "History", Icons.Rounded.History)
    object Auth : Screen("auth", "Auth", Icons.Rounded.Lock)
}

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fenceRepository: FenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firestore = FirebaseFirestore.getInstance()

        authManager = AuthManager(this)
        deviceRepository = DeviceRepository(firestore)
        groupRepository = GroupRepository(firestore)
        locationRepository = LocationRepository(firestore)
        alertRepository = AlertRepository(firestore)
        settingsRepository = SettingsRepository(firestore)
        fenceRepository = FenceRepository(firestore)

        setContent {
            var isDarkMode by remember { mutableStateOf(authManager.isDarkMode()) }

            BeaconAdminTheme(darkTheme = isDarkMode) {
                // Request location permissions
                PermissionRequest()

                MainContent(
                    authManager = authManager,
                    deviceRepository = deviceRepository,
                    groupRepository = groupRepository,
                    locationRepository = locationRepository,
                    alertRepository = alertRepository,
                    fenceRepository = fenceRepository,
                    isDarkMode = isDarkMode,
                    onDarkModeChange = {
                        isDarkMode = it
                        authManager.setDarkMode(it)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    authManager: AuthManager,
    deviceRepository: DeviceRepository,
    groupRepository: GroupRepository,
    locationRepository: LocationRepository,
    alertRepository: AlertRepository,
    fenceRepository: FenceRepository,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    var isAuthenticated by remember { mutableStateOf(authManager.isAuthenticated()) }
    
    // Monitor auth changes
    LaunchedEffect(Unit) {
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val nowAuth = auth.currentUser != null
            if (isAuthenticated != nowAuth) {
                isAuthenticated = nowAuth
            }
        }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            if (isAuthenticated) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentDestination?.route) {
                                Screen.Home.route -> "Beacon Hub"
                                Screen.Map.route -> "Live Map"
                                Screen.Devices.route -> "Devices"
                                Screen.Alerts.route -> "Alerts"
                                Screen.History.route -> "History"
                                else -> "Beacon Admin"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        IconButton(onClick = { onDarkModeChange(!isDarkMode) }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime,
                                contentDescription = "Toggle Theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            if (isAuthenticated) {
                val showBottomBar = currentDestination?.route in listOf(
                    Screen.Home.route, Screen.Map.route, Screen.Devices.route, Screen.Alerts.route
                ) || currentDestination?.route?.startsWith(Screen.History.route) == true
                  || currentDestination?.route?.startsWith("map/") == true

                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        val items = listOf(Screen.Map, Screen.Devices, Screen.Home, Screen.Alerts, Screen.History)
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { }, // Icon-only
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = if (isAuthenticated) Screen.Home.route else Screen.Auth.route, 
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(authManager) {
                    isAuthenticated = true
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    deviceRepository = deviceRepository,
                    alertRepository = alertRepository,
                    onAddDeviceClick = {
                        navController.navigate(Screen.Devices.route)
                        // Note: In Phase 3/4 we'll make this specifically open the pairing flow
                    }
                )
            }
            composable(Screen.Devices.route) {
                DeviceListScreen(
                    authManager = authManager,
                    deviceRepository = deviceRepository,
                    groupRepository = groupRepository,
                    onDeviceHistoryClick = { device ->
                        navController.navigate(Screen.History.route + "/${device.deviceId}")
                    },
                    onDeviceMapClick = { device ->
                        navController.navigate("map/${device.deviceId}")
                    },
                    onAddDevice = { /* Handled inside screen */ }
                )
            }
            composable(Screen.Map.route) {
                MapScreen(
                    onBack = { navController.popBackStack() },
                    fenceRepository = fenceRepository
                )
            }
            composable("map/{deviceId}") { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId")
                MapScreen(
                    initialDeviceId = deviceId,
                    onBack = { navController.popBackStack() },
                    fenceRepository = fenceRepository
                )
            }
            composable(Screen.Alerts.route) {
                AlertsScreen(alertRepository = alertRepository)
            }
            composable(Screen.History.route + "/{deviceId}") { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                HistoryScreen(deviceId, locationRepository)
            }
            composable(Screen.History.route) {
                // Root history view if no device selected
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Select a device to view history")
                    }
                }
            }
            // Settings screen removed in favor of per-device settings (Phase 3)
        }
    }
}

@Composable
fun PermissionRequest() {
    val context = LocalContext.current
    val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            launcher.launch(permissions)
        }
    }
}
