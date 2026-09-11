package com.beacon.admin.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beacon.admin.ui.theme.*
import com.beacon.shared.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceConfigDialog(
    geofence: GeofenceZone,
    availableDevices: List<Device>,
    availableGroups: List<Group>,
    onDismiss: () -> Unit,
    onSave: (GeofenceZone) -> Unit,
    onDelete: (String) -> Unit = {}
) {
    var name by remember { mutableStateOf(geofence.name) }
    var radius by remember { mutableStateOf(geofence.radiusMeters ?: 100.0) }
    var directionality by remember { mutableStateOf(geofence.directionality ?: Directionality.BOTH) }
    var assignedDeviceIds by remember { mutableStateOf(geofence.assignedDeviceIds.toSet()) }
    var assignedGroupIds by remember { mutableStateOf(geofence.assignedGroupIds.toSet()) }
    var alertOnEnter by remember { mutableStateOf(geofence.alertOnEnter) }
    var alertOnExit by remember { mutableStateOf(geofence.alertOnExit) }
    var alertFrequency by remember { mutableStateOf(geofence.alertFrequency) }
    
    // Temporal
    var expirationOption by remember { mutableStateOf("Never") }
    var activeDays by remember { mutableStateOf(geofence.activeDaysOfWeek?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = ObsidianBase,
            topBar = {
                TopAppBar(
                    title = { Text(if (geofence.id.isEmpty()) "New Geofence" else "Edit Geofence", color = TextPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBase),
                    actions = {
                        if (geofence.id.isNotEmpty()) {
                            IconButton(onClick = { onDelete(geofence.id); onDismiss() }) {
                                Text("Delete", color = BeaconCrimson)
                            }
                        }
                        TextButton(onClick = {
                            val finalFence = geofence.copy(
                                name = name.ifBlank { "New Geofence" },
                                radiusMeters = if (geofence.type == GeofenceType.RADIAL) radius else null,
                                directionality = if (geofence.type == GeofenceType.TRIPWIRE) directionality else null,
                                assignedDeviceIds = assignedDeviceIds.toList(),
                                assignedGroupIds = assignedGroupIds.toList(),
                                alertOnEnter = alertOnEnter,
                                alertOnExit = alertOnExit,
                                alertFrequency = alertFrequency,
                                activeDaysOfWeek = activeDays.toList().sorted(),
                                activeUntil = when (expirationOption) {
                                    "1 Hour" -> System.currentTimeMillis() + 3600000
                                    "24 Hours" -> System.currentTimeMillis() + 86400000
                                    "7 Days" -> System.currentTimeMillis() + 604800000
                                    else -> null
                                }
                            )
                            onSave(finalFence)
                            onDismiss()
                        }) {
                            Text("Save", color = BeaconCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // General Section
                item {
                    Column {
                        Text("General", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Fence Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BeaconCyan,
                                unfocusedBorderColor = GlassSurfaceBorder
                            )
                        )
                        Text(
                            text = "Type: ${geofence.type}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Type Specific Parameters
                if (geofence.type == GeofenceType.RADIAL) {
                    item {
                        Column {
                            Text("Radius: ${radius.toInt()}m", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                            Slider(
                                value = radius.toFloat(),
                                onValueChange = { radius = it.toDouble() },
                                valueRange = 50f..5000f,
                                steps = 99,
                                colors = SliderDefaults.colors(thumbColor = BeaconCyan, activeTrackColor = BeaconCyan)
                            )
                        }
                    }
                } else if (geofence.type == GeofenceType.TRIPWIRE) {
                    item {
                        Column {
                            Text("Directionality", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                            Directionality.entries.forEach { dir ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = directionality == dir,
                                        onClick = { directionality = dir },
                                        colors = RadioButtonDefaults.colors(selectedColor = BeaconCyan)
                                    )
                                    Text(dir.name, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }

                // Assignments
                item {
                    Text("Target Assignments", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                }

                items(availableGroups) { group ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = assignedGroupIds.contains(group.groupId),
                            onCheckedChange = { checked ->
                                assignedGroupIds = if (checked) assignedGroupIds + group.groupId else assignedGroupIds - group.groupId
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BeaconCyan)
                        )
                        Text("Group: ${group.name}", color = TextPrimary)
                    }
                }

                items(availableDevices) { device ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = assignedDeviceIds.contains(device.deviceId),
                            onCheckedChange = { checked ->
                                assignedDeviceIds = if (checked) assignedDeviceIds + device.deviceId else assignedDeviceIds - device.deviceId
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BeaconCyan)
                        )
                        Text("Device: ${device.deviceName}", color = TextPrimary)
                    }
                }

                // Temporal settings
                item {
                    Column {
                        Text("Expiration", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                        listOf("Never", "1 Hour", "24 Hours", "7 Days").forEach { opt ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = expirationOption == opt,
                                    onClick = { expirationOption = opt },
                                    colors = RadioButtonDefaults.colors(selectedColor = BeaconCyan)
                                )
                                Text(opt, color = TextPrimary)
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text("Active Days", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
                                val dayNum = index + 1
                                FilterChip(
                                    selected = activeDays.contains(dayNum),
                                    onClick = {
                                        activeDays = if (activeDays.contains(dayNum)) activeDays - dayNum else activeDays + dayNum
                                    },
                                    label = { Text(day) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BeaconCyan,
                                        selectedLabelColor = ObsidianBase,
                                        labelColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                }

                // Alerts
                item {
                    Column {
                        Text("Alert Settings", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = alertOnEnter, onCheckedChange = { alertOnEnter = it }, colors = CheckboxDefaults.colors(checkedColor = BeaconCyan))
                            Text("Alert on Enter", color = TextPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = alertOnExit, onCheckedChange = { alertOnExit = it }, colors = CheckboxDefaults.colors(checkedColor = BeaconCyan))
                            Text("Alert on Exit", color = TextPrimary)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
