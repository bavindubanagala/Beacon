package com.beacon.admin.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.admin.ui.theme.BeaconCrimson
import com.beacon.admin.ui.viewmodels.DeviceDetailsViewModel
import com.beacon.shared.models.Device

enum class TimeUnit(val label: String, val millisPerUnit: Long) {
    SECONDS("Seconds", 1_000L),
    MINUTES("Minutes", 60_000L),
    HOURS("Hours", 3_600_000L),
    DAYS("Days", 86_400_000L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionBottomSheet(
    device: Device,
    onDismiss: () -> Unit = {},
    onPing: (String) -> Unit = {},
    onUpdateProfile: (String, String) -> Unit = { _, _ -> },
    onAssignGeofence: (String, String?) -> Unit = { _, _ -> },
    onRenameClick: (String) -> Unit = {},
    onUnpairClick: (String) -> Unit = {},
    viewModel: DeviceDetailsViewModel? = null,
    onDismissRequest: () -> Unit = onDismiss,
    onSetTrackingMode: (String, String) -> Unit = { dId, mode -> viewModel?.setTrackingMode(dId, mode) },
    onSetScheduledInterval: (String, Long) -> Unit = { dId, ms -> viewModel?.setScheduledInterval(dId, ms) },
    onSetLiveInterval: (String, Long) -> Unit = { dId, ms -> viewModel?.setLiveInterval(dId, ms) }
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actions: ${device.deviceName.ifBlank { device.deviceId }}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            TrackingModeSection(
                deviceId = device.deviceId,
                currentMode = device.trackingMode.uppercase(),
                scheduledIntervalMillis = device.scheduledIntervalMillis,
                liveIntervalMillis = device.liveIntervalMillis,
                onSetTrackingMode = onSetTrackingMode,
                onSetScheduledInterval = onSetScheduledInterval,
                onSetLiveInterval = onSetLiveInterval,
                viewModel = viewModel
            )

            Button(
                onClick = {
                    onPing(device.deviceId)
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("High-Priority Location Ping")
            }

            Button(
                onClick = {
                    onAssignGeofence(device.deviceId, null)
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Assign Geofence")
            }

            OutlinedButton(
                onClick = {
                    onRenameClick(device.deviceId)
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rename Device")
            }

            Button(
                onClick = {
                    onUnpairClick(device.deviceId)
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BeaconCrimson
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unpair Device", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

@Composable
fun TrackingModeSection(
    deviceId: String,
    currentMode: String,
    scheduledIntervalMillis: Long,
    liveIntervalMillis: Long,
    onSetTrackingMode: (String, String) -> Unit = { _, _ -> },
    onSetScheduledInterval: (String, Long) -> Unit = { _, _ -> },
    onSetLiveInterval: (String, Long) -> Unit = { _, _ -> },
    viewModel: DeviceDetailsViewModel? = null
) {
    val liveCyan = Color(0xFF00E5FF)
    val scheduledViolet = Color(0xFF7C4DFF)
    val onlineNeutral = Color(0xFF81C784)

    var selectedMode by remember(currentMode) { mutableStateOf(currentMode.uppercase()) }

    val handleSetTrackingMode = { mode: String ->
        selectedMode = mode
        onSetTrackingMode(deviceId, mode)
        viewModel?.setTrackingMode(deviceId, mode)
    }

    val handleSetScheduledInterval = { millis: Long ->
        onSetScheduledInterval(deviceId, millis)
        viewModel?.setScheduledInterval(deviceId, millis)
    }

    val handleSetLiveInterval = { millis: Long ->
        onSetLiveInterval(deviceId, millis)
        viewModel?.setLiveInterval(deviceId, millis)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Tracking Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val modes = listOf("SCHEDULED", "LIVE", "ONLINE")
            modes.forEach { mode ->
                val isSelected = selectedMode == mode
                val accentColor = when (mode) {
                    "LIVE" -> liveCyan
                    "SCHEDULED" -> scheduledViolet
                    else -> onlineNeutral
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            handleSetTrackingMode(mode)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (isSelected) accentColor else Color.Gray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedMode) {
            "SCHEDULED" -> {
                ScheduledIntervalControls(
                    initialMillis = scheduledIntervalMillis,
                    onSave = { millis -> handleSetScheduledInterval(millis) }
                )
            }
            "LIVE" -> {
                LiveIntervalControls(
                    initialMillis = liveIntervalMillis,
                    onSave = { millis -> handleSetLiveInterval(millis) }
                )
            }
            "ONLINE" -> {
                Text(
                    text = "Not tracking right now. You can still request this device's location anytime, and it quietly checks in every 5 minutes to confirm it's connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ScheduledIntervalControls(
    initialMillis: Long,
    onSave: (Long) -> Unit
) {
    val (bestValue, bestUnit) = remember(initialMillis) {
        val ms = if (initialMillis <= 0) 900_000L else initialMillis
        when {
            ms % 86_400_000L == 0L -> (ms / 86_400_000L) to TimeUnit.DAYS
            ms % 3_600_000L == 0L -> (ms / 3_600_000L) to TimeUnit.HOURS
            ms % 60_000L == 0L -> (ms / 60_000L) to TimeUnit.MINUTES
            else -> (ms / 1_000L) to TimeUnit.SECONDS
        }
    }

    var numberText by remember(initialMillis) { mutableStateOf(bestValue.toString()) }
    var selectedUnit by remember(initialMillis) { mutableStateOf(bestUnit) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = numberText,
            onValueChange = { input ->
                if (input.all { it.isDigit() } && input.length <= 6) {
                    numberText = input
                }
            },
            label = { Text("Check-in Every") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (isFocused && !focusState.isFocused) {
                        val num = numberText.toLongOrNull() ?: 15L
                        val calculatedMs = (num * selectedUnit.millisPerUnit).coerceAtLeast(15_000L)
                        onSave(calculatedMs)
                    }
                    isFocused = focusState.isFocused
                }
        )

        Box {
            OutlinedButton(onClick = { expandedDropdown = true }) {
                Text(selectedUnit.label)
            }
            DropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false }
            ) {
                TimeUnit.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(unit.label) },
                        onClick = {
                            selectedUnit = unit
                            expandedDropdown = false
                            val num = numberText.toLongOrNull() ?: 15L
                            val calculatedMs = (num * unit.millisPerUnit).coerceAtLeast(15_000L)
                            onSave(calculatedMs)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveIntervalControls(
    initialMillis: Long,
    onSave: (Long) -> Unit
) {
    var sliderValue by remember(initialMillis) {
        mutableStateOf((initialMillis.coerceIn(5_000L, 60_000L) / 1000L).toFloat())
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Updating every ${sliderValue.toInt()} seconds",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF00E5FF),
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = 5f..60f,
            steps = 55,
            onValueChangeFinished = {
                onSave(sliderValue.toLong() * 1000L)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFF00E5FF)
            )
        )
    }
}
