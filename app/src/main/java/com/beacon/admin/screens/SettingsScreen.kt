package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.admin.auth.AuthManager
import com.beacon.admin.repository.DeviceRepository
import com.beacon.admin.repository.SettingsRepository
import com.beacon.shared.models.Device
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authManager: AuthManager,
    deviceRepository: DeviceRepository,
    settingsRepository: SettingsRepository,
    locationRepository: com.beacon.admin.repository.LocationRepository,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentUserId = authManager.getCurrentUser()?.uid ?: ""

    var defaultTrackingInterval by remember { mutableStateOf(60f) }
    var lowBatteryThreshold by remember { mutableStateOf(20f) }
    var offlineTimeout by remember { mutableStateOf(300f) }
    var historyRetentionDays by remember { mutableStateOf(90f) }

    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var showDeviceSelection by remember { mutableStateOf(false) }
    var selectedDeviceIds by remember { mutableStateOf(setOf<String>()) }

    // Load initial settings
    LaunchedEffect(Unit) {
        settingsRepository.getSettings().onSuccess { data ->
            (data["defaultTrackingInterval"] as? Number)?.let { defaultTrackingInterval = it.toFloat() }
            (data["lowBatteryThreshold"] as? Number)?.let { lowBatteryThreshold = it.toFloat() }
            (data["offlineTimeout"] as? Number)?.let { offlineTimeout = it.toFloat() }
            (data["historyRetentionDays"] as? Number)?.let { historyRetentionDays = it.toFloat() }
        }
        
        deviceRepository.getAllDevices(currentUserId).onSuccess {
            devices = it
            
            // Batch A4: Automatic History Pruning
            it.forEach { device ->
                locationRepository.pruneOldHistory(device.deviceId, historyRetentionDays.toInt())
            }
        }
    }

    if (showDeviceSelection) {
        AlertDialog(
            onDismissRequest = { showDeviceSelection = false },
            title = { Text("Select Devices") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    Text("Select which devices to apply these settings to:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(devices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedDeviceIds.contains(device.deviceId),
                                    onCheckedChange = { checked ->
                                        selectedDeviceIds = if (checked) {
                                            selectedDeviceIds + device.deviceId
                                        } else {
                                            selectedDeviceIds - device.deviceId
                                        }
                                    }
                                )
                                Text(device.deviceName.ifEmpty { "Unknown Device" })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            // 1. Save global settings
                            settingsRepository.updateSettings(mapOf(
                                "defaultTrackingInterval" to defaultTrackingInterval,
                                "lowBatteryThreshold" to lowBatteryThreshold,
                                "offlineTimeout" to offlineTimeout,
                                "historyRetentionDays" to historyRetentionDays
                            ))

                            // 2. Apply to selected devices
                            devices.filter { selectedDeviceIds.contains(it.deviceId) }.forEach { device ->
                                deviceRepository.updateDeviceSettings(
                                    deviceId = device.deviceId,
                                    mode = device.trackingMode,
                                    intervalSeconds = defaultTrackingInterval.toInt(),
                                    autoRevertSeconds = device.autoRevertSeconds,
                                    isEmergency = device.isEmergencyMode,
                                    batterySavingEnabled = device.batterySavingEnabled,
                                    stationaryIntervalMinutes = device.stationaryIntervalMinutes,
                                    lowBatteryPercent = lowBatteryThreshold.toInt(),
                                    offlineThresholdMinutes = (offlineTimeout / 60).toInt(),
                                    sosFallbackPhone = device.sosFallbackPhone
                                )
                            }
                            showDeviceSelection = false
                        }
                    }
                ) {
                    Text("Apply & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceSelection = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Text(
                    "Global Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 24.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            // Theme & Troubleshooting
            item {
                SettingCard(
                    title = "App Preferences",
                    content = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = isDarkMode,
                                    onCheckedChange = { onDarkModeChange(it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { /* TODO: Troubleshooting */ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text("Background Optimization Guide")
                            }
                        }
                    }
                )
            }

            // Tracking Interval
            item {
                SettingCard(
                    title = "Default Tracking Interval",
                    subtitle = "${defaultTrackingInterval.toInt()} seconds",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = defaultTrackingInterval,
                                onValueChange = { defaultTrackingInterval = it },
                                valueRange = 15f..3600f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Base interval for all active tracking",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            // Low Battery Threshold
            item {
                SettingCard(
                    title = "Low Battery Alert Threshold",
                    subtitle = "${lowBatteryThreshold.toInt()}%",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = lowBatteryThreshold,
                                onValueChange = { lowBatteryThreshold = it },
                                valueRange = 5f..50f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Notify when any device battery drops below this",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            // Offline Timeout
            item {
                SettingCard(
                    title = "Offline Alert Timeout",
                    subtitle = "${offlineTimeout.toInt()} seconds",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = offlineTimeout,
                                onValueChange = { offlineTimeout = it },
                                valueRange = 60f..3600f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Alert if a device hasn't checked in for this long",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            // History Retention
            item {
                SettingCard(
                    title = "Location History Retention",
                    subtitle = "${historyRetentionDays.toInt()} days",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = historyRetentionDays,
                                onValueChange = { historyRetentionDays = it },
                                valueRange = 1f..365f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Automatically delete location data older than this to save space and maintain privacy.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            // Cleanup Stale Devices
            item {
                var cleaning by remember { mutableStateOf(false) }
                var cleanResult by remember { mutableStateOf<Int?>(null) }
                
                SettingCard(
                    title = "Device Maintenance",
                    content = {
                        Column {
                            Text(
                                "Automatically remove devices that haven't been seen for more than 30 days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    cleaning = true
                                    scope.launch {
                                        val result = deviceRepository.cleanupInactiveDevices(currentUserId)
                                        cleanResult = result.getOrDefault(0)
                                        cleaning = false
                                    }
                                },
                                enabled = !cleaning,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (cleaning) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                                } else {
                                    Text("Cleanup Inactive Devices")
                                }
                            }
                            
                            cleanResult?.let { count ->
                                Text(
                                    "Removed $count inactive devices",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Save Button
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        selectedDeviceIds = devices.map { it.deviceId }.toSet()
                        showDeviceSelection = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Save & Apply to Devices", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Logout Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        authManager.signOut()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout")
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}
