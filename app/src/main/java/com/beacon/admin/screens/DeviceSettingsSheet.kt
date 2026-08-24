package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beacon.shared.models.Device
import com.beacon.admin.repository.DeviceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsSheet(
    device: Device,
    onDismiss: () -> Unit,
    deviceRepository: DeviceRepository
) {
    val scope = rememberCoroutineScope()
    
    // Tracking Mode States
    var selectedMode by remember { mutableStateOf(device.trackingMode) }
    var interval by remember { mutableStateOf(device.intervalSeconds.toFloat()) }
    var isEmergency by remember { mutableStateOf(device.isEmergencyMode) }
    var autoRevertMinutes by remember { mutableStateOf(device.autoRevertSeconds.toFloat() / 60f) }
    
    var batteryThreshold by remember { mutableStateOf(device.alertThresholds.lowBatteryPercent.toFloat()) }
    var offlineTimeout by remember { mutableStateOf(device.alertThresholds.offlineThresholdMinutes.toFloat() * 60f) }
    var batterySavingEnabled by remember { mutableStateOf(device.batterySavingEnabled) }
    var stationaryIntervalMinutes by remember { mutableStateOf(device.stationaryIntervalMinutes) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Remove Device") },
            text = { Text("Are you sure you want to remove ${device.deviceName}? This action cannot be undone and all history will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        deviceRepository.removeDevice(device.deviceId)
                        showDeleteConfirmation = false
                        onDismiss()
                    }
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Device Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove Device")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Battery Status", style = MaterialTheme.typography.labelMedium)
                        Text("${device.batteryLevel}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = {
                        scope.launch {
                            // Trigger refresh command logic
                            deviceRepository.updateDeviceSettings(
                                deviceId = device.deviceId,
                                mode = device.trackingMode,
                                intervalSeconds = device.intervalSeconds,
                                autoRevertSeconds = device.autoRevertSeconds,
                                isEmergency = device.isEmergencyMode,
                                batterySavingEnabled = device.batterySavingEnabled,
                                stationaryIntervalMinutes = device.stationaryIntervalMinutes,
                                lowBatteryPercent = device.alertThresholds.lowBatteryPercent,
                                offlineThresholdMinutes = device.alertThresholds.offlineThresholdMinutes,
                                sosFallbackPhone = device.sosFallbackPhone ?: ""
                            )
                        }
                    }) {
                        Text("Refresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tracking Mode Section
            Text("Tracking Mode", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off" to "Off", "interval" to "Interval", "live" to "Live").forEach { (mode, label) ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (selectedMode == "interval") {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Interval: ${interval.toInt()}s", 
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Slider(
                    value = interval,
                    onValueChange = { interval = it },
                    valueRange = 15f..3600f,
                    steps = 0
                )
            }

            if (selectedMode == "live") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isEmergency, onCheckedChange = { isEmergency = it })
                    Text("Emergency Override", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Auto-revert to Interval after: ${autoRevertMinutes.toInt()} min",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Slider(
                    value = autoRevertMinutes,
                    onValueChange = { autoRevertMinutes = it },
                    valueRange = 0f..120f,
                    steps = 0
                )
                Text("Set to 0 to disable auto-revert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))

            // Smart Battery Saving
            Text("Smart Battery Saving", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = batterySavingEnabled, onCheckedChange = { batterySavingEnabled = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable automatic stationary mode", style = MaterialTheme.typography.bodyMedium)
            }
            
            if (batterySavingEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Stationary Interval", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        FilterChip(
                            selected = stationaryIntervalMinutes == minutes,
                            onClick = { stationaryIntervalMinutes = minutes },
                            label = { Text("${minutes}m") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))

            // Per-Device Thresholds
            Text("Alert Thresholds", style = MaterialTheme.typography.titleSmall)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Low Battery Alert: ${batteryThreshold.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Slider(
                value = batteryThreshold,
                onValueChange = { batteryThreshold = it },
                valueRange = 5f..50f
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Offline Timeout: ${offlineTimeout.toInt()}s",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Slider(
                value = offlineTimeout,
                onValueChange = { offlineTimeout = it },
                valueRange = 60f..1800f
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    scope.launch {
                        deviceRepository.updateDeviceSettings(
                            deviceId = device.deviceId,
                            mode = selectedMode,
                            intervalSeconds = interval.toInt(),
                            autoRevertSeconds = (autoRevertMinutes * 60).toInt(), 
                            isEmergency = isEmergency,
                            batterySavingEnabled = batterySavingEnabled,
                            stationaryIntervalMinutes = stationaryIntervalMinutes,
                            lowBatteryPercent = batteryThreshold.toInt(),
                            offlineThresholdMinutes = (offlineTimeout / 60).toInt(),
                            sosFallbackPhone = device.sosFallbackPhone ?: ""
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Apply Settings")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Remove Device")
            }
        }
    }
}
