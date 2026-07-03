package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.shared.models.Device
import com.beacon.shared.models.Group
import com.beacon.admin.repository.DeviceRepository
import com.beacon.admin.repository.GroupRepository

@Composable
fun DeviceListScreen(
    deviceRepository: DeviceRepository,
    groupRepository: GroupRepository,
    onDeviceClick: (Device) -> Unit,
    onAddDevice: () -> Unit
) {

    val devices = remember { mutableStateOf<List<Device>>(emptyList()) }
    val groups = remember { mutableStateOf<List<Group>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val expandedGroups = remember { mutableStateOf(setOf("ungrouped")) }
    val errorMessage = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val d = deviceRepository.getAllDevices()
        val g = groupRepository.getAllGroups()

        if (d.isSuccess && g.isSuccess) {
            devices.value = d.getOrDefault(emptyList())
            groups.value = g.getOrDefault(emptyList())
        } else {
            errorMessage.value =
                d.exceptionOrNull()?.message
                    ?: g.exceptionOrNull()?.message
                    ?: "Failed"
        }

        isLoading.value = false
    }

    Surface {
        Column(Modifier.fillMaxSize().padding(16.dp)) {

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("My Devices", fontSize = 24.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onAddDevice) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (errorMessage.value.isNotEmpty()) {
                Text(errorMessage.value, color = Color.Red)
                Spacer(Modifier.height(12.dp))
            }

            if (isLoading.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val ungrouped = devices.value.filter { d ->
                groups.value.none { it.deviceIds.contains(d.deviceId) }
            }

            LazyColumn {

                if (ungrouped.isNotEmpty()) {
                    item {
                        Text("Ungrouped (${ungrouped.size})", modifier = Modifier.padding(8.dp))
                    }

                    items(ungrouped) { device ->
                        DeviceItem(device, onDeviceClick)
                    }
                }

                groups.value.forEach { group ->

                    item {
                        Text(group.name, modifier = Modifier.padding(8.dp))
                    }

                    val groupDevices = devices.value.filter {
                        group.deviceIds.contains(it.deviceId)
                    }

                    items(groupDevices) { device ->
                        DeviceItem(device, onDeviceClick)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: Device, onClick: (Device) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        elevation = 4.dp
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(Modifier.weight(1f)) {
                Text(device.deviceName)
                Text(device.deviceId, fontSize = 11.sp, color = Color.Gray)
            }

            Text("${device.batteryLevel}%")

            IconButton(onClick = { onClick(device) }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
        }
    }
}
