package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    // New Per-Device Settings (Defaulting to current values if they exist, or standard defaults)
    var batteryThreshold by remember { mutableStateOf(20f) }
    var offlineTimeout by remember { mutableStateOf(300f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedMode == "interval") {
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
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = isEmergency, onCheckedChange = { isEmergency = it })
                    Text("Emergency Override", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Per-Device Thresholds
            Text("Alert Thresholds", style = MaterialTheme.typography.titleSmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
                        deviceRepository.setTrackingMode(
                            device.deviceId,
                            selectedMode,
                            interval.toInt(),
                            0, // autoRevert not implemented in this UI for now
                            isEmergency
                        )
                        // Note: batteryThreshold and offlineTimeout saving would be implemented here
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Apply Settings")
            }
        }
    }
}
