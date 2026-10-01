package com.beacon.admin.ui.dialogs

import androidx.compose.foundation.clickable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceConfigDialog(
    geofence: GeofenceZone,
    availableDevices: List<Device>,
    availableGroups: List<DeviceGroup>,
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
    var arrivalLiveEnabled by remember { mutableStateOf(geofence.arrivalLiveEnabled) }
    var arrivalLiveSeconds by remember { mutableStateOf((geofence.arrivalLiveIntervalMillis / 1000L).toFloat().coerceIn(5f, 60f)) }
    var revertOnExit by remember { mutableStateOf(geofence.revertOnExit) }
    
    // Temporal
    val initialExpiration = if (geofence.activeUntil != null) "Keep" else "Never"
    var expirationOption by remember { mutableStateOf(initialExpiration) }
    var activeDays by remember { mutableStateOf(geofence.activeDaysOfWeek?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7)) }

    val keepLabel = remember(geofence.activeUntil) {
        if (geofence.activeUntil != null) {
            val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
            val formattedDate = sdf.format(Date(geofence.activeUntil))
            val isExpired = geofence.activeUntil < System.currentTimeMillis()
            if (isExpired) "Keep current (expired $formattedDate)" else "Keep current (ends $formattedDate)"
        } else {
            ""
        }
    }

    val expirationOptions = buildList {
        if (geofence.activeUntil != null) {
            add("Keep")
        }
        add("Never")
        add("1 Hour")
        add("24 Hours")
        add("7 Days")
    }

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
                                arrivalLiveEnabled = if (geofence.type == GeofenceType.RADIAL) arrivalLiveEnabled else false,
                                arrivalLiveIntervalMillis = arrivalLiveSeconds.toInt() * 1000L,
                                revertOnExit = revertOnExit,
                                activeDaysOfWeek = activeDays.toList().sorted(),
                                activeUntil = when (expirationOption) {
                                    "Keep" -> geofence.activeUntil
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Directionality.entries.forEach { dir ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { directionality = dir }
                                ) {
                                    RadioButton(
                                        selected = directionality == dir,
                                        onClick = { directionality = dir },
                                        colors = RadioButtonDefaults.colors(selectedColor = BeaconCyan)
                                    )
                                    Text(dir.name, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Stand at point A and look toward point B. A_TO_B means crossing from your left side to your right side. B_TO_A is the opposite. BOTH alerts for either direction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Assignments
                item {
                    Text("Target Assignments", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                }

                items(availableGroups) { group ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        assignedGroupIds = if (assignedGroupIds.contains(group.id)) assignedGroupIds - group.id else assignedGroupIds + group.id
                    }) {
                        Checkbox(
                            checked = assignedGroupIds.contains(group.id),
                            onCheckedChange = { checked ->
                                assignedGroupIds = if (checked) assignedGroupIds + group.id else assignedGroupIds - group.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BeaconCyan)
                        )
                        Text("Group: ${group.name}", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                items(availableDevices) { device ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        assignedDeviceIds = if (assignedDeviceIds.contains(device.deviceId)) assignedDeviceIds - device.deviceId else assignedDeviceIds + device.deviceId
                    }) {
                        Checkbox(
                            checked = assignedDeviceIds.contains(device.deviceId),
                            onCheckedChange = { checked ->
                                assignedDeviceIds = if (checked) assignedDeviceIds + device.deviceId else assignedDeviceIds - device.deviceId
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BeaconCyan)
                        )
                        Text("Device: ${device.deviceName}", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                // Temporal settings
                item {
                    Column {
                        Text("Expiration", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        expirationOptions.forEach { opt ->
                            val label = if (opt == "Keep") keepLabel else opt
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expirationOption = opt }
                            ) {
                                RadioButton(
                                    selected = expirationOption == opt,
                                    onClick = { expirationOption = opt },
                                    colors = RadioButtonDefaults.colors(selectedColor = BeaconCyan)
                                )
                                Text(label, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text("Active Days", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                        Spacer(modifier = Modifier.height(8.dp))
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

                // Arrival behaviour (Radial only)
                if (geofence.type == GeofenceType.RADIAL) {
                    item {
                        Column {
                            Text("Arrival behaviour", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { arrivalLiveEnabled = !arrivalLiveEnabled }) {
                                Checkbox(
                                    checked = arrivalLiveEnabled,
                                    onCheckedChange = { arrivalLiveEnabled = it },
                                    colors = CheckboxDefaults.colors(checkedColor = BeaconCyan)
                                )
                                Text("Switch to Live when a device enters", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                            }
                            if (arrivalLiveEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Live delay on arrival: ${arrivalLiveSeconds.toInt()} seconds",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Slider(
                                    value = arrivalLiveSeconds,
                                    onValueChange = { arrivalLiveSeconds = it },
                                    valueRange = 5f..60f,
                                    steps = 54,
                                    colors = SliderDefaults.colors(
                                        thumbColor = BeaconCyan,
                                        activeTrackColor = BeaconCyan
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { revertOnExit = !revertOnExit }) {
                                    Checkbox(
                                        checked = revertOnExit,
                                        onCheckedChange = { revertOnExit = it },
                                        colors = CheckboxDefaults.colors(checkedColor = BeaconCyan)
                                    )
                                    Text("Switch back to the previous mode when it leaves", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Applies to circle zones only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Alerts (Radial only)
                if (geofence.type == GeofenceType.RADIAL) {
                    item {
                        Column {
                            Text("Alert Settings", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { alertOnEnter = !alertOnEnter }) {
                                Checkbox(checked = alertOnEnter, onCheckedChange = { alertOnEnter = it }, colors = CheckboxDefaults.colors(checkedColor = BeaconCyan))
                                Text("Alert on Enter", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { alertOnExit = !alertOnExit }) {
                                Checkbox(checked = alertOnExit, onCheckedChange = { alertOnExit = it }, colors = CheckboxDefaults.colors(checkedColor = BeaconCyan))
                                Text("Alert on Exit", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }

                // Alert Frequency (all types)
                item {
                    Column {
                        Text("Alert frequency", style = MaterialTheme.typography.titleMedium, color = BeaconCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        val freqOptions = listOf(
                            AlertFrequency.EVERY_TIME to "Every time",
                            AlertFrequency.ONCE_PER_DAY to "Once per day",
                            AlertFrequency.ONCE_EVER to "Once ever"
                        )
                        freqOptions.forEach { (freq, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { alertFrequency = freq }
                            ) {
                                RadioButton(
                                    selected = alertFrequency == freq,
                                    onClick = { alertFrequency = freq },
                                    colors = RadioButtonDefaults.colors(selectedColor = BeaconCyan)
                                )
                                Text(label, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Limits how often alerts are sent. Switching to Live is not affected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
