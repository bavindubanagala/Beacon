package com.beacon.admin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.beacon.admin.auth.AuthManager
import com.beacon.admin.repository.AlertRepository
import com.beacon.admin.repository.DeviceRepository
import com.beacon.admin.repository.GroupRepository
import com.beacon.admin.repository.LocationRepository
import com.beacon.admin.repository.SettingsRepository
import com.beacon.admin.screens.AlertsScreen
import com.beacon.admin.screens.DeviceListScreen
import com.beacon.admin.screens.HistoryScreen
import com.beacon.admin.screens.MapScreen
import com.beacon.admin.screens.SettingsScreen
import com.beacon.admin.ui.theme.BeaconAdminTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var authManager: AuthManager
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var settingsRepository: SettingsRepository
    private val isAuthenticated = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager(this)
        deviceRepository = DeviceRepository(this)
        groupRepository = GroupRepository(this)
        locationRepository = LocationRepository(this)
        alertRepository = AlertRepository(this)
        settingsRepository = SettingsRepository(this)

        // Check if already authenticated
        isAuthenticated.value = authManager.isAuthenticated()

        setContent {
            BeaconAdminTheme {
                Surface(color = MaterialTheme.colors.background) {
                    if (isAuthenticated.value) {
                        MainApp(
                            authManager,
                            deviceRepository,
                            groupRepository,
                            locationRepository,
                            alertRepository,
                            settingsRepository
                        ) {
                            isAuthenticated.value = false
                        }
                    } else {
                        AuthScreen(authManager) {
                            isAuthenticated.value = true
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MainApp(
        authManager: AuthManager,
        deviceRepository: DeviceRepository,
        groupRepository: GroupRepository,
        locationRepository: LocationRepository,
        alertRepository: AlertRepository,
        settingsRepository: SettingsRepository,
        onSignOut: () -> Unit
    ) {
        val selectedTab = remember { mutableStateOf(0) }

        Scaffold(
            bottomBar = {
                BottomNavigation(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.primary
                ) {
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Devices") },
                        label = { Text("Devices") },
                        selected = selectedTab.value == 0,
                        onClick = { selectedTab.value = 0 }
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
                        label = { Text("Map") },
                        selected = selectedTab.value == 1,
                        onClick = { selectedTab.value = 1 }
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Edit, contentDescription = "History") },
                        label = { Text("History") },
                        selected = selectedTab.value == 2,
                        onClick = { selectedTab.value = 2 }
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Alert, contentDescription = "Alerts") },
                        label = { Text("Alerts") },
                        selected = selectedTab.value == 3,
                        onClick = { selectedTab.value = 3 }
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = selectedTab.value == 4,
                        onClick = { selectedTab.value = 4 }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab.value) {
                    0 -> DeviceListScreen(
                        deviceRepository,
                        groupRepository,
                        onDeviceClick = { /* TODO: show device details */ },
                        onAddDevice = { /* TODO: show add device dialog */ }
                    )
                    1 -> MapScreen()
                    2 -> HistoryScreen()
                    3 -> AlertsScreen(alertRepository)
                    4 -> SettingsScreen()
                }
            }
        }
    }

    @Composable
    private fun AuthScreen(
        authManager: AuthManager,
        onAuthSuccess: () -> Unit
    ) {
        val email = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val isLoading = remember { mutableStateOf(false) }
        val errorMessage = remember { mutableStateOf("") }
        val isSignUp = remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Beacon Admin",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = email.value,
                    onValueChange = { email.value = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading.value
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password.value,
                    onValueChange = { password.value = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading.value
                )

                if (errorMessage.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        errorMessage.value,
                        fontSize = 12.sp,
                        color = Color(0xFFE57373)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isLoading.value) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            isLoading.value = true
                            errorMessage.value = ""

                            lifecycleScope.launch {
                                val result = if (isSignUp.value) {
                                    authManager.signUp(email.value, password.value)
                                } else {
                                    authManager.signIn(email.value, password.value)
                                }

                                isLoading.value = false

                                if (result.isSuccess) {
                                    onAuthSuccess()
                                } else {
                                    errorMessage.value = result.exceptionOrNull()?.message ?: "Auth failed"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSignUp.value) "Sign Up" else "Sign In", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { isSignUp.value = !isSignUp.value },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isSignUp.value) "Already have an account?" else "Create new account",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
