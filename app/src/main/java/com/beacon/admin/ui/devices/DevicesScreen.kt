package com.beacon.admin.ui.devices

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.DeviceGroupsViewModel
import com.beacon.shared.models.Device
import com.beacon.shared.models.DeviceGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onDeviceClick: (String) -> Unit = {},
    devicesViewModel: DevicesViewModel = hiltViewModel(),
    groupsViewModel: DeviceGroupsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val devicesState by devicesViewModel.devicesState.collectAsStateWithLifecycle()
    val pairResult by devicesViewModel.pairResult.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showPairDialog by remember { mutableStateOf(false) }
    var selectedDeviceForQuickActions by remember { mutableStateOf<DeviceUiModel?>(null) }

    LaunchedEffect(pairResult) {
        when (val result = pairResult) {
            is PairResult.Success -> {
                Toast.makeText(context, "Device paired successfully!", Toast.LENGTH_SHORT).show()
                showPairDialog = false
                devicesViewModel.resetPairResult()
            }
            is PairResult.Error -> {
                Toast.makeText(context, "Pairing Failed: ${result.message}", Toast.LENGTH_LONG).show()
                devicesViewModel.resetPairResult()
            }
            else -> {}
        }
    }

    val filteredDevices = remember(devicesState, searchQuery, selectedFilter) {
        devicesState.devices.filter { device ->
            val matchesSearch = device.name.contains(searchQuery, ignoreCase = true) ||
                    device.id.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "Online" -> device.isOnline
                "Offline" -> !device.isOnline
                "SOS" -> device.hasActiveSos
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        containerColor = ObsidianBase,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPairDialog = true },
                containerColor = BeaconCyan,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Pair New Device")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Device Fleet",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name or ID...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BeaconCyan,
                    unfocusedBorderColor = GlassSurfaceBorder,
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("All", "Online", "Offline", "SOS")
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BeaconCyan.copy(alpha = 0.2f),
                            selectedLabelColor = BeaconCyan,
                            containerColor = GlassSurface,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = GlassSurfaceBorder,
                            selectedBorderColor = BeaconCyan,
                            enabled = true,
                            selected = selectedFilter == filter
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Device List
            when {
                devicesState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BeaconCyan)
                    }
                }

                filteredDevices.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No devices match filter" else "No devices registered",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredDevices, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                onClick = { onDeviceClick(device.id) },
                                onMoreActionsClick = { selectedDeviceForQuickActions = device }
                            )
                        }
                    }
                }
            }
        }

        // Pair Device Dialog
        if (showPairDialog) {
            PairDeviceDialog(
                isLoading = pairResult is PairResult.Loading,
                onDismiss = {
                    if (pairResult !is PairResult.Loading) {
                        showPairDialog = false
                        devicesViewModel.resetPairResult()
                    }
                },
                onConfirm = { code ->
                    devicesViewModel.pairDevice(code)
                }
            )
        }

        // Quick Actions Bottom Sheet (Optional overflow)
        selectedDeviceForQuickActions?.let { device ->
            DeviceActionBottomSheet(
                device = com.beacon.shared.models.Device(deviceId = device.id, deviceName = device.name),
                onDismiss = { selectedDeviceForQuickActions = null },
                onPing = { id -> devicesViewModel.sendLocationPing(id) },
                onUpdateProfile = { id, profile -> devicesViewModel.updateTrackingProfile(id, profile) },
                onAssignGeofence = { id, gId -> devicesViewModel.assignGeofence(id, gId) }
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceUiModel,
    onClick: () -> Unit,
    onMoreActionsClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (device.hasActiveSos) BeaconCrimson else GlassSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            device.hasActiveSos -> BeaconCrimson
                            device.isOnline -> BeaconCyan
                            else -> TextMuted
                        }
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifBlank { "Device #${device.id.take(6)}" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "ID: ${device.id} • ${device.lastSeenAgo}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = when {
                        device.batteryLevel > 80 -> Icons.Rounded.BatteryFull
                        device.batteryLevel > 30 -> Icons.Rounded.Battery4Bar
                        else -> Icons.Rounded.BatteryAlert
                    },
                    contentDescription = null,
                    tint = if (device.batteryLevel < 20) BeaconCrimson else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${device.batteryLevel}%",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
                )
            }

            IconButton(onClick = onMoreActionsClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Quick Actions",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
private fun PairDeviceDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val isValid = text.trim().isNotBlank() && !isLoading

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBase,
        title = { Text("Pair New Device", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Device ID or Code") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeaconCyan,
                        unfocusedBorderColor = GlassSurfaceBorder,
                        focusedLabelColor = BeaconCyan,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = BeaconCyan
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onConfirm(text.trim().uppercase())
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BeaconCyan,
                    disabledContainerColor = GlassSurfaceBorder
                )
            ) {
                Text("Pair", color = if (isValid) MaterialTheme.colorScheme.onPrimary else TextMuted)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
