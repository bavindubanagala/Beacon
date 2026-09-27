package com.beacon.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.BeaconMapComponent
import com.beacon.admin.ui.components.MapMarkerState
import com.beacon.admin.ui.components.SyncProgressDialog
import com.beacon.admin.ui.devices.DeviceUiModel
import com.beacon.admin.ui.devices.DevicesViewModel
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.DeviceDetailsViewModel
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    deviceId: String,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (String) -> Unit,
    onNavigateToGeofence: (String) -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
    detailsViewModel: DeviceDetailsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val telemetrySyncState by detailsViewModel.telemetrySyncState.collectAsStateWithLifecycle()
    val detailsUiState by detailsViewModel.uiState.collectAsStateWithLifecycle()
    val device = (devicesState as? com.beacon.admin.ui.devices.DevicesListState.Success)
        ?.devices?.find { it.id == deviceId }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showUnpairConfirmDialog by remember { mutableStateOf(false) }
    var recenterTarget by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(detailsViewModel) {
        detailsViewModel.unpairSuccessEvents.collect {
            Toast.makeText(context, "Device successfully unpaired", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }

    Scaffold(
        containerColor = ObsidianBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = device?.name?.ifBlank { "Device Details" } ?: "Device Details",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GlassSurface,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        if (devicesState is com.beacon.admin.ui.devices.DevicesListState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BeaconCyan)
            }
        } else if (devicesState is com.beacon.admin.ui.devices.DevicesListState.Error) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text((devicesState as com.beacon.admin.ui.devices.DevicesListState.Error).message, color = BeaconCrimson)
            }
        } else if (device == null) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Device not found", color = TextMuted)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Device ID & Manual Sync Header Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device ID",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = deviceId,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BeaconCyan
                                )
                            )
                        }

                        IconButton(
                            onClick = {
                                detailsViewModel.triggerFullTelemetrySync(deviceId)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Manual Location Sync",
                                tint = BeaconCyan
                            )
                        }
                    }
                }

                // Device Status Card
                StatusSummaryCard(device = device)

                // Interactive Map Section
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GlassSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        BeaconMapComponent(
                            modifier = Modifier.fillMaxSize(),
                            initialLat = device.latitude,
                            initialLng = device.longitude,
                            initialZoom = 15.0,
                            markers = listOf(
                                MapMarkerState(
                                    id = device.id,
                                    title = device.name,
                                    latitude = device.latitude,
                                    longitude = device.longitude,
                                    status = if (device.hasActiveSos) com.beacon.shared.models.DeviceStatus.RED_OFFLINE 
                                             else if (device.isOnline) com.beacon.shared.models.DeviceStatus.GREEN_LIVE
                                             else com.beacon.shared.models.DeviceStatus.YELLOW_IDLE,
                                    accuracy = device.accuracy
                                )
                            ),
                            centerOn = recenterTarget,
                            onRecenter = {
                                recenterTarget = GeoPoint(device.latitude, device.longitude)
                            }
                        )
                    }

                    Surface(
                        onClick = { detailsViewModel.toggleFullScreenMap() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = GlassSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder)
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "Full screen map",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (detailsUiState.isFullScreenMap) {
                    Dialog(
                        onDismissRequest = { detailsViewModel.toggleFullScreenMap() },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ObsidianBase)
                        ) {
                            BeaconMapComponent(
                                modifier = Modifier.fillMaxSize(),
                                initialLat = device.latitude,
                                initialLng = device.longitude,
                                initialZoom = 15.0,
                                markers = listOf(
                                    MapMarkerState(
                                        id = device.id,
                                        title = device.name,
                                        latitude = device.latitude,
                                        longitude = device.longitude,
                                        status = if (device.hasActiveSos) com.beacon.shared.models.DeviceStatus.RED_OFFLINE 
                                                 else if (device.isOnline) com.beacon.shared.models.DeviceStatus.GREEN_LIVE
                                                 else com.beacon.shared.models.DeviceStatus.YELLOW_IDLE,
                                        accuracy = device.accuracy
                                    )
                                ),
                                centerOn = recenterTarget,
                                onRecenter = {
                                    recenterTarget = GeoPoint(device.latitude, device.longitude)
                                }
                            )

                            Surface(
                                onClick = { detailsViewModel.toggleFullScreenMap() },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = GlassSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder)
                            ) {
                                Box(modifier = Modifier.padding(8.dp)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close full screen map",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Telemetry Metrics Grid
                TelemetryGrid(device = device)

                // Quick Action Shortcuts
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToHistory(deviceId) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BeaconCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BeaconCyan)
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Route History")
                    }

                    OutlinedButton(
                        onClick = { onNavigateToGeofence(deviceId) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BeaconCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BeaconCyan)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Geofencing")
                    }
                }

                // Page Body Action Controls & Settings Section
                Text(
                    text = "Management & Configuration",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Tracking Profile",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )

                        val profiles = listOf("High Accuracy", "Balanced", "Battery Saver")
                        var selectedProfile by remember(device.trackingProfile) {
                            mutableStateOf(profiles.find { it.equals(device.trackingProfile, ignoreCase = true) } ?: profiles[1])
                        }

                        profiles.forEach { profile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedProfile == profile,
                                    onClick = {
                                        selectedProfile = profile
                                        viewModel.updateTrackingProfile(deviceId, profile)
                                        detailsViewModel.updateTrackingMode(profile.lowercase().replace(" ", "_"))
                                        Toast.makeText(context, "Tracking profile set to $profile", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                Text(
                                    text = profile,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Inline Action Buttons (Rename & Unpair)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            renameText = device.name
                            showRenameDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BeaconCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BeaconCyan)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rename")
                    }

                    Button(
                        onClick = { showUnpairConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconCrimson)
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unpair Device", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }

        // Telemetry Sync Progress Modal Dialog
        if (telemetrySyncState.isVisible) {
            SyncProgressDialog(
                state = telemetrySyncState,
                onDismiss = { detailsViewModel.dismissSyncDialog() }
            )
        }

        // Rename Device Dialog
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                containerColor = ObsidianBase,
                title = { Text("Rename Device", color = TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Device Name") },
                        singleLine = true,
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
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newName = renameText.trim()
                            if (newName.isNotBlank()) {
                                detailsViewModel.updateDeviceName(newName)
                                showRenameDialog = false
                                Toast.makeText(context, "Device renamed to $newName", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconCyan)
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }

        // Unpair Confirmation Dialog
        if (showUnpairConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUnpairConfirmDialog = false },
                containerColor = ObsidianBase,
                title = { Text("Unpair Device", color = BeaconCrimson) },
                text = {
                    Text(
                        "Are you sure you want to unpair ${device?.name ?: deviceId}? This device will be removed from your account.",
                        color = TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnpairConfirmDialog = false
                            detailsViewModel.confirmUnpairing()
                            viewModel.unpairDevice(deviceId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconCrimson)
                    ) {
                        Text("Unpair", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnpairConfirmDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusSummaryCard(device: DeviceUiModel) {
    Surface(
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
                    .size(14.dp)
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
                    text = if (device.isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (device.isOnline) BeaconCyan else TextMuted
                    )
                )
                Text(
                    text = "Last seen: ${device.lastSeenAgo}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }

            if (device.hasActiveSos) {
                Surface(
                    color = BeaconCrimson.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BeaconCrimson)
                ) {
                    Text(
                        text = "SOS ACTIVE",
                        color = BeaconCrimson,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryGrid(device: DeviceUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryMetricCard(
                title = "Battery",
                value = "${device.batteryLevel}%",
                icon = Icons.Rounded.BatteryChargingFull,
                modifier = Modifier.weight(1f)
            )
            TelemetryMetricCard(
                title = "Speed",
                value = device.speedFormatted,
                icon = Icons.Rounded.Speed,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryMetricCard(
                title = "Signal",
                value = device.signalFormatted,
                icon = Icons.Rounded.CellTower,
                modifier = Modifier.weight(1f)
            )
            TelemetryMetricCard(
                title = "Profile",
                value = device.trackingProfile,
                icon = Icons.Rounded.Tune,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BeaconCyan,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        }
    }
}
