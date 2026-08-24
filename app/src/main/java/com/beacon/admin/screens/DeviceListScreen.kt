package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beacon.admin.repository.DeviceRepository
import com.beacon.admin.repository.GroupRepository
import com.beacon.shared.models.Device
import com.beacon.shared.models.Group
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    authManager: com.beacon.admin.auth.AuthManager,
    deviceRepository: DeviceRepository,
    groupRepository: GroupRepository,
    onDeviceHistoryClick: (Device) -> Unit,
    onDeviceMapClick: (Device) -> Unit,
    onAddDevice: () -> Unit
) {
    val currentUserId = authManager.getCurrentUser()?.uid ?: ""
    val devices = remember { mutableStateOf<List<Device>>(emptyList()) }
    val groups = remember { mutableStateOf<List<Group>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val errorMessage = remember { mutableStateOf("") }
    val showAddDialog = remember { mutableStateOf(false) }
    val selectedDeviceForSettings = remember { mutableStateOf<Device?>(null) }
    val selectedDeviceToRemove = remember { mutableStateOf<Device?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(currentUserId) {
        if (currentUserId.isEmpty()) {
            isLoading.value = false
            return@DisposableEffect onDispose {}
        }
        
        val listener = deviceRepository.getDevicesListener(
            ownerId = currentUserId,
            onUpdate = { updatedDevices ->
                devices.value = updatedDevices
                isLoading.value = false
            },
            onError = { e ->
                errorMessage.value = "Error: ${e.message}"
                isLoading.value = false
            }
        )
        
        scope.launch {
            val g = groupRepository.getAllGroups()
            if (g.isSuccess) groups.value = g.getOrDefault(emptyList())
        }

        onDispose { listener.remove() }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "My Devices", 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showAddDialog.value = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Device")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (errorMessage.value.isNotEmpty()) {
                Text(errorMessage.value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }

            if (isLoading.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val pairedDevices = devices.value.filter { it.is_paired }
                val ungrouped = pairedDevices.filter { d ->
                    groups.value.none { it.deviceIds.contains(d.deviceId) }
                }

                if (pairedDevices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No devices paired yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showAddDialog.value = true }) {
                                Text("Pair a Device")
                            }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ungrouped.isNotEmpty()) {
                            item { 
                                Text(
                                    "Ungrouped", 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) 
                            }
                            items(ungrouped) { device ->
                                DeviceItem(
                                    device = device,
                                    onHistoryClick = onDeviceHistoryClick,
                                    onMapClick = onDeviceMapClick,
                                    onSettingsClick = { selectedDeviceForSettings.value = it },
                                    onRemoveClick = { selectedDeviceToRemove.value = it }
                                )
                            }
                        }
                        groups.value.forEach { group ->
                            item { 
                                Text(
                                    group.name, 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) 
                            }
                            val groupDevices = pairedDevices.filter { group.deviceIds.contains(it.deviceId) }
                            items(groupDevices) { device ->
                                DeviceItem(
                                    device = device,
                                    onHistoryClick = onDeviceHistoryClick,
                                    onMapClick = onDeviceMapClick,
                                    onSettingsClick = { selectedDeviceForSettings.value = it },
                                    onRemoveClick = { selectedDeviceToRemove.value = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedDeviceToRemove.value != null) {
        val device = selectedDeviceToRemove.value!!
        AlertDialog(
            onDismissRequest = { selectedDeviceToRemove.value = null },
            title = { Text("Remove Device") },
            text = { Text("Are you sure you want to remove ${device.deviceName}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = deviceRepository.removeDevice(device.deviceId)
                        if (result.isSuccess) {
                            selectedDeviceToRemove.value = null
                        } else {
                            errorMessage.value = "Failed to remove: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDeviceToRemove.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog.value) {
        AddDeviceDialog(
            onDismiss = { showAddDialog.value = false },
            onPair = { code, name ->
                scope.launch {
                    val result = deviceRepository.pairDevice(code, name, currentUserId)
                    if (result.isSuccess) {
                        showAddDialog.value = false
                    } else {
                        errorMessage.value = "Pairing failed: ${result.exceptionOrNull()?.message}"
                    }
                }
            }
        )
    }

    selectedDeviceForSettings.value?.let { device ->
        DeviceSettingsSheet(
            device = device,
            onDismiss = { selectedDeviceForSettings.value = null },
            deviceRepository = deviceRepository
        )
    }
}

@Composable
fun DeviceItem(
    device: Device, 
    onHistoryClick: (Device) -> Unit, 
    onMapClick: (Device) -> Unit,
    onSettingsClick: (Device) -> Unit,
    onRemoveClick: (Device) -> Unit
) {
    if (!device.is_paired) return
    
    val statusColor = when (device.trackingMode) {
        "live" -> MaterialTheme.colorScheme.primary
        "interval" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMapClick(device) },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Status edge accent
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(statusColor)
            )

            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (device.status == "online") Color(0xFF4CAF50) else Color.Gray,
                                    androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            device.deviceName.ifEmpty { "Unknown" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            text = device.trackingMode.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "BATTERY: ${device.batteryLevel}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onPair: (String, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pair New Device",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("6-Digit Pairing Code") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 18.sp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name (e.g. Dad's Phone)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (code.length == 6) onPair(code, name) },
                        enabled = code.length == 6,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Pair Device")
                    }
                }
            }
        }
    }
}
