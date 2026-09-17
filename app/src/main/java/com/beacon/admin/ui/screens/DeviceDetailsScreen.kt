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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.devices.DeviceActionBottomSheet
import com.beacon.admin.ui.devices.DeviceUiModel
import com.beacon.admin.ui.devices.DevicesViewModel
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.components.BeaconMapComponent
import com.beacon.admin.ui.components.MapMarkerState
import com.beacon.shared.models.Device
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    deviceId: String,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (String) -> Unit,
    onNavigateToGeofence: (String) -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val device = devicesState.devices.find { it.id == deviceId }

    var showActionSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBase,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = device?.name?.ifBlank { "Device #$deviceId" } ?: "Device Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "ID: $deviceId",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
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
                actions = {
                    IconButton(onClick = { showActionSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Device Actions",
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
        if (device == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BeaconCyan)
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
                // Device Status Card
                StatusSummaryCard(device = device)

                // Interactive Map Section
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
                        centerOn = GeoPoint(device.latitude, device.longitude)
                    )
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
            }
        }

        if (showActionSheet && device != null) {
            DeviceActionBottomSheet(
                device = Device(deviceId = device.id, deviceName = device.name),
                onDismiss = { showActionSheet = false },
                onPing = { id ->
                    viewModel.sendLocationPing(id)
                    Toast.makeText(context, "Location ping sent", Toast.LENGTH_SHORT).show()
                },
                onUpdateProfile = { id, profile ->
                    viewModel.updateTrackingProfile(id, profile)
                    Toast.makeText(context, "Profile updated to $profile", Toast.LENGTH_SHORT).show()
                },
                onAssignGeofence = { id, gId ->
                    viewModel.assignGeofence(id, gId)
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
