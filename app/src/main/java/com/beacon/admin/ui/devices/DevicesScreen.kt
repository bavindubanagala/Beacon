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
import com.beacon.admin.ui.components.ClearSosConfirmDialog
import com.beacon.admin.ui.components.SyncProgressDialog
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.DeviceGroupsViewModel

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
    val telemetrySyncState by devicesViewModel.telemetrySyncState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showPairDialog by remember { mutableStateOf(false) }

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

    val devices = (devicesState as? DevicesListState.Success)?.devices.orEmpty()
    val filteredDevices = remember(devices, searchQuery, selectedFilter) {
        devices.filter { device ->
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
                devicesState is DevicesListState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BeaconCyan)
                    }
                }

                devicesState is DevicesListState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text((devicesState as DevicesListState.Error).message, color = BeaconCrimson)
                    }
                }

                devicesState is DevicesListState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No devices registered",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                devicesState is DevicesListState.Success -> if (filteredDevices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No devices match filter", color = TextMuted)
                    }
                } else {
                    var sosToClear by remember { mutableStateOf<DeviceUiModel?>(null) }
                    if (sosToClear != null) {
                        ClearSosConfirmDialog(
                            deviceName = sosToClear!!.name,
                            onConfirm = {
                                devicesViewModel.clearSos(sosToClear!!.id) { ok, msg ->
                                    Toast.makeText(context, if (ok) "SOS cleared" else "Could not clear SOS: ${msg ?: "unknown error"}", Toast.LENGTH_LONG).show()
                                }
                                sosToClear = null
                            },
                            onDismiss = { sosToClear = null }
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredDevices, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                onClick = { onDeviceClick(device.id) },
                                onManualSyncClick = {
                                    devicesViewModel.triggerFullTelemetrySync(device.id)
                                },
                                onClearSosClick = { sosToClear = device }
                            )
                        }
                    }
                }
            }
        }

        // Telemetry Sync Progress Modal Dialog
        if (telemetrySyncState.isVisible) {
            SyncProgressDialog(
                state = telemetrySyncState,
                onDismiss = { devicesViewModel.dismissSyncDialog() }
            )
        }

        // Pair Device Dialog
        if (showPairDialog) {
            AddDeviceDialog(
                isLoading = pairResult is PairResult.Loading,
                onDismiss = {
                    if (pairResult !is PairResult.Loading) {
                        showPairDialog = false
                        devicesViewModel.resetPairResult()
                    }
                },
                onConfirm = { code, name ->
                    devicesViewModel.pairDevice(code, name)
                }
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceUiModel,
    onClick: () -> Unit,
    onManualSyncClick: () -> Unit,
    onClearSosClick: () -> Unit
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
                    text = device.lastSeenAgo,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
                if (device.modeLabel.isNotBlank()) {
                    Text(
                        text = device.modeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(color = BeaconCyan)
                    )
                }
                if (!device.is_paired) {
                    Text(
                        text = "Unpaired - open the device to delete it",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }
                if (device.hasActiveSos) {
                    TextButton(
                        onClick = onClearSosClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = BeaconCrimson),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Text("Clear SOS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            IconButton(onClick = onManualSyncClick) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Manual Location Sync",
                    tint = BeaconCyan,
                    modifier = Modifier.size(20.dp)
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
        }
    }
}

@Composable
private fun PairDeviceDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (code: String, name: String) -> Unit
) {
    AddDeviceDialog(
        isLoading = isLoading,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
