package com.beacon.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.BeaconMapComponent
import com.beacon.admin.ui.components.ClearSosConfirmDialog
import com.beacon.admin.ui.components.MapMarkerState
import com.beacon.admin.ui.components.SyncProgressDialog
import com.beacon.admin.ui.devices.DeviceUiModel
import com.beacon.admin.ui.devices.DevicesViewModel
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.DeviceDetailsViewModel
import com.beacon.shared.models.ModeHistoryEntry
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val applyState by detailsViewModel.applyState.collectAsStateWithLifecycle()
    val modeHistory by detailsViewModel.modeHistory.collectAsStateWithLifecycle()
    val device = (devicesState as? com.beacon.admin.ui.devices.DevicesListState.Success)
        ?.devices?.find { it.id == deviceId }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showUnpairConfirmDialog by remember { mutableStateOf(false) }
    var recenterTarget by remember { mutableStateOf<GeoPoint?>(null) }
    var followEnabled by remember { mutableStateOf(true) }
    var recenterSignal by remember { mutableStateOf(0) }
    var showClearSos by remember { mutableStateOf(false) }
    val isLive = device?.trackingMode?.trim()?.uppercase() == "LIVE"

    if (showClearSos) {
        ClearSosConfirmDialog(
            deviceName = device?.name ?: "",
            onConfirm = {
                viewModel.clearSos(deviceId) { ok, msg ->
                    Toast.makeText(context, if (ok) "SOS cleared" else "Could not clear SOS: ${msg ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
                showClearSos = false
            },
            onDismiss = { showClearSos = false }
        )
    }

    LaunchedEffect(isLive) { if (isLive) followEnabled = true }

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
                            followTarget = if (isLive && followEnabled && (device.latitude != 0.0 || device.longitude != 0.0)) GeoPoint(device.latitude, device.longitude) else null,
                            isFollowing = isLive && followEnabled && (device.latitude != 0.0 || device.longitude != 0.0),
                            recenterSignal = recenterSignal,
                            onUserPanned = { followEnabled = false },
                            onRecenter = {
                                recenterTarget = GeoPoint(device.latitude, device.longitude)
                                followEnabled = true
                                recenterSignal += 1
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
                                followTarget = if (isLive && followEnabled && (device.latitude != 0.0 || device.longitude != 0.0)) GeoPoint(device.latitude, device.longitude) else null,
                                isFollowing = isLive && followEnabled && (device.latitude != 0.0 || device.longitude != 0.0),
                                recenterSignal = recenterSignal,
                                onUserPanned = { followEnabled = false },
                                onRecenter = {
                                    recenterTarget = GeoPoint(device.latitude, device.longitude)
                                    followEnabled = true
                                    recenterSignal += 1
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
                if (device.hasActiveSos) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BeaconCrimson),
                        colors = CardDefaults.cardColors(containerColor = BeaconCrimson.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SOS ACTIVE", color = BeaconCrimson, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { showClearSos = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BeaconCrimson)
                            ) {
                                Text("Clear SOS")
                            }
                        }
                    }
                }

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

                TrackingModeCard(
                    device = device,
                    applyState = applyState,
                    onApply = { mode, intervalMs, revertMs -> detailsViewModel.applyTrackingSettings(mode, intervalMs, revertMs) },
                    onStateHandled = { detailsViewModel.clearApplyState() }
                )

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
                        colors = ButtonDefaults.buttonColors(containerColor = if (device.is_paired) BeaconCrimson else BeaconCyan)
                    ) {
                        Icon(
                            if (device.is_paired) Icons.Rounded.Delete else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (device.is_paired) "Unpair Device" else "Delete Device", color = MaterialTheme.colorScheme.onError)
                    }
                }

                ModeHistoryCard(entries = modeHistory)
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

        // Unpair / Delete Confirmation Dialog
        if (showUnpairConfirmDialog && device != null) {
            AlertDialog(
                onDismissRequest = { showUnpairConfirmDialog = false },
                containerColor = ObsidianBase,
                title = { Text(if (device.is_paired) "Unpair Device" else "Delete this device?", color = BeaconCrimson) },
                text = {
                    Text(
                        if (device.is_paired) "Are you sure you want to unpair ${device.name ?: deviceId}? This device will be removed from your account."
                        else "This permanently deletes the device record and any SOS on it. This cannot be undone.",
                        color = TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnpairConfirmDialog = false
                            if (device.is_paired) {
                                detailsViewModel.confirmUnpairing()
                                viewModel.unpairDevice(deviceId)
                            } else {
                                detailsViewModel.confirmDeleteDevice()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconCrimson)
                    ) {
                        Text(if (device.is_paired) "Unpair" else "Delete", color = MaterialTheme.colorScheme.onError)
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

@Composable
private fun ModeHistoryCard(entries: List<ModeHistoryEntry>) {
    val liveCyan = Color(0xFF00E5FF)
    val scheduledViolet = Color(0xFF7C4DFF)
    val onlineNeutral = Color(0xFF81C784)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Mode history",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            val displayEntries = entries.take(20)
            if (displayEntries.isEmpty()) {
                Text(
                    text = "No changes recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                val dateFormat = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayEntries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = GlassSurfaceBorder,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        val modeUpper = entry.mode.trim().uppercase()
                        val dotColor = when (modeUpper) {
                            "LIVE" -> liveCyan
                            "SCHEDULED", "INTERVAL" -> scheduledViolet
                            "ONLINE" -> onlineNeutral
                            else -> scheduledViolet
                        }

                        val modeDisplayName = when (modeUpper) {
                            "LIVE" -> "Live"
                            "SCHEDULED", "INTERVAL" -> "Scheduled"
                            "ONLINE" -> "Online"
                            else -> modeUpper.lowercase().replaceFirstChar { it.uppercase() }
                        }

                        val delayText = if (modeUpper == "ONLINE" || entry.intervalMillis <= 0L) {
                            ""
                        } else {
                            " - ${formatModeDelay(entry.intervalMillis)}"
                        }

                        val titleLine = "$modeDisplayName$delayText"

                        val timeStr = if (entry.timestamp > 0L) dateFormat.format(Date(entry.timestamp)) else ""
                        val authorStr = when (entry.changedBy.trim()) {
                            "Admin" -> "by Admin"
                            "Auto-revert" -> "by Auto-revert"
                            "Geofence" -> "by Geofence"
                            "" -> ""
                            else -> if (entry.changedBy.startsWith("by ", ignoreCase = true)) entry.changedBy else "by ${entry.changedBy}"
                        }
                        val subtitleLine = listOf(timeStr, authorStr).filter { it.isNotBlank() }.joinToString(" ")

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleLine,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                )
                                if (subtitleLine.isNotBlank()) {
                                    Text(
                                        text = subtitleLine,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatModeDelay(ms: Long): String {
    if (ms <= 0L) return ""
    val sec = ms / 1000L
    if (sec < 60L) {
        return "$sec seconds"
    }
    val min = sec / 60L
    if (min < 60L) {
        return if (min == 1L) "1 minute" else "$min minutes"
    }
    val hr = min / 60L
    if (hr < 24L) {
        return if (hr == 1L) "1 hour" else "$hr hours"
    }
    val days = hr / 24L
    return if (days == 1L) "1 day" else "$days days"
}
