package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.beacon.shared.models.Device
import com.beacon.shared.models.Group
import com.beacon.admin.repository.DeviceRepository
import com.beacon.admin.repository.GroupRepository
import kotlinx.coroutines.launch

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
    val expandedGroups = remember { mutableStateOf<Set<String>>(setOf("ungrouped")) }
    val errorMessage = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val devicesResult = deviceRepository.getAllDevices()
        val groupsResult = groupRepository.getAllGroups()

        if (devicesResult.isSuccess && groupsResult.isSuccess) {
            devices.value = devicesResult.getOrNull() ?: emptyList()
            groups.value = groupsResult.getOrNull() ?: emptyList()
        } else {
            errorMessage.value = devicesResult.exceptionOrNull()?.message
                ?: groupsResult.exceptionOrNull()?.message
                ?: "Failed to load devices"
        }

        isLoading.value = false
    }

    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "My Devices",
                    fontSize = 24.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onAddDevice() }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Device"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Message
            if (errorMessage.value.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFE57373),
                    elevation = 4.dp
                ) {
                    Text(
                        errorMessage.value,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Loading State
            if (isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (devices.value.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No devices. Tap + to add one.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            } else {
                // Device List by Groups
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Ungrouped devices
                    val ungroupedDevices = devices.value.filter { device ->
                        groups.value.none { group -> group.device_ids.contains(device.device_id) }
                    }

                    if (ungroupedDevices.isNotEmpty()) {
                        item {
                            GroupHeader(
                                groupName = "Ungrouped",
                                isExpanded = expandedGroups.value.contains("ungrouped"),
                                deviceCount = ungroupedDevices.size,
                                onToggle = { expanded ->
                                    val newSet = expandedGroups.value.toMutableSet()
                                    if (expanded) {
                                        newSet.add("ungrouped")
                                    } else {
                                        newSet.remove("ungrouped")
                                    }
                                    expandedGroups.value = newSet
                                }
                            )
                        }

                        if (expandedGroups.value.contains("ungrouped")) {
                            items(ungroupedDevices) { device ->
                                DeviceListItem(device) { onDeviceClick(device) }
                            }
                        }
                    }

                    // Grouped devices
                    groups.value.forEach { group ->
                        item {
                            GroupHeader(
                                groupName = group.name,
                                isExpanded = expandedGroups.value.contains(group.group_id),
                                deviceCount = group.device_ids.size,
                                onToggle = { expanded ->
                                    val newSet = expandedGroups.value.toMutableSet()
                                    if (expanded) {
                                        newSet.add(group.group_id)
                                    } else {
                                        newSet.remove(group.group_id)
                                    }
                                    expandedGroups.value = newSet
                                }
                            )
                        }

                        if (expandedGroups.value.contains(group.group_id)) {
                            val groupDevices = devices.value.filter { device ->
                                group.device_ids.contains(device.device_id)
                            }
                            items(groupDevices) { device ->
                                DeviceListItem(device) { onDeviceClick(device) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupHeader(
    groupName: String,
    isExpanded: Boolean,
    deviceCount: Int,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        backgroundColor = Color(0xFFF5F5F5),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                groupName,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                deviceCount.toString(),
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onToggle(!isExpanded) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Group"
                )
            }
        }
    }
}

@Composable
fun DeviceListItem(device: Device, onDeviceClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device Icon & Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.device_name,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    device.device_id.take(8),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Indicators
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            when {
                                device.battery_level >= 50 -> Color(0xFF4CAF50)
                                device.battery_level >= 20 -> Color(0xFFFFC107)
                                else -> Color(0xFFE57373)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        device.battery_level.toString(),
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Online Status
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (device.status == "online") Color(0xFF4CAF50) else Color(0xFFBDBDBD)
                        )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Menu
            IconButton(onClick = { onDeviceClick() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
        }
    }
}
