package com.beacon.admin.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.BeaconMapComponent
import com.beacon.admin.ui.components.GlassCard
import com.beacon.admin.ui.components.StatusHaloBadge
import com.beacon.admin.ui.dialogs.GeofenceConfigDialog
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.utils.getStatusUiConfig
import com.beacon.admin.ui.viewmodels.MapPinState
import com.beacon.admin.ui.viewmodels.MapUiState
import com.beacon.admin.ui.viewmodels.MapViewModel
import com.beacon.shared.models.DeviceStatusLight
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val markers by viewModel.mapMarkers.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is MapUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BeaconCyan)
            }
        }
        is MapUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (state.isAuthError || state.isPermissionDenied) 
                        Icons.Rounded.Security else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = if (state.isAuthError || state.isPermissionDenied) BeaconCrimson else TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (state.isAuthError) "Session Expired" 
                          else if (state.isPermissionDenied) "Access Denied" 
                          else "Monitoring Offline",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
        is MapUiState.Success -> {
            MapContent(
                state = state,
                markers = markers,
                viewModel = viewModel,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapContent(
    state: MapUiState.Success,
    markers: List<com.beacon.admin.ui.components.MapMarkerState>,
    viewModel: MapViewModel,
    modifier: Modifier
) {
    val selectedPin = state.pins.find { it.id == state.selectedPinId }

    val initialCamera = remember(state.pins) {
        val validPins = state.pins.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        val center = when {
            validPins.size == 1 -> validPins[0].latitude to validPins[0].longitude
            else -> MapViewModel.DEFAULT_SRI_LANKA_CENTER
        }
        val zoom = if (validPins.size == 1) MapViewModel.DETAIL_ZOOM else MapViewModel.DEFAULT_ZOOM
        center to zoom
    }

    LaunchedEffect(state.centerOn) {
        if (state.centerOn != null) {
            viewModel.clearCentering()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ObsidianBase)) {
        // Real Map Surface
        BeaconMapComponent(
            initialLat = initialCamera.first.first,
            initialLng = initialCamera.first.second,
            initialZoom = initialCamera.second,
            mapStyle = state.selectedMapStyle,
            markers = markers,
            geofences = state.geofences,
            isCreationMode = state.isCreationMode,
            draftGeofenceType = state.draftType,
            draftCenter = state.draftCenter,
            draftRadiusMeters = state.draftRadius,
            draftPointA = state.draftPointA,
            draftPointB = state.draftPointB,
            onDraftCenterMoved = { viewModel.setDraftCenter(it) },
            onDraftPointAMoved = { viewModel.setDraftPointA(it) },
            onDraftPointBMoved = { viewModel.setDraftPointB(it) },
            onMarkerClick = { markerId ->
                viewModel.selectPin(markerId)
            },
            centerOn = state.centerOn?.let { GeoPoint(it.first, it.second) },
            targetZoom = state.targetZoom
        )

        // Top Floating Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCard(shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusHaloBadge(status = DeviceStatusLight.GREEN_LIVE, size = 6.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${state.totalActiveCount} Devices Active", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Layer Controls
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var showLayerMenu by remember { mutableStateOf(false) }
                var showGroupFilter by remember { mutableStateOf(false) }
                var showDeviceFilter by remember { mutableStateOf(false) }
                var showAddFenceMenu by remember { mutableStateOf(false) }

                val groupSheetState = rememberModalBottomSheetState()
                val deviceSheetState = rememberModalBottomSheetState()

                if (!state.isCreationMode) {
                    Box {
                        FloatingMapIconButton(
                            icon = Icons.Rounded.AddLocationAlt,
                            active = false,
                            onClick = { showAddFenceMenu = true }
                        )

                        DropdownMenu(
                            expanded = showAddFenceMenu,
                            onDismissRequest = { showAddFenceMenu = false },
                            modifier = Modifier.background(GlassSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Radial Zone", color = TextPrimary) },
                                onClick = {
                                    viewModel.startGeofenceCreation(com.beacon.shared.models.GeofenceType.RADIAL)
                                    showAddFenceMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tripwire", color = TextPrimary) },
                                onClick = {
                                    viewModel.startGeofenceCreation(com.beacon.shared.models.GeofenceType.TRIPWIRE)
                                    showAddFenceMenu = false
                                }
                            )
                        }
                    }
                }

                FloatingMapIconButton(
                    icon = Icons.Rounded.PinDrop,
                    active = state.isGeofencesVisible,
                    onClick = { viewModel.toggleGeofences() }
                )

                FloatingMapIconButton(
                    icon = Icons.Default.GroupWork,
                    active = state.selectedGroups.isNotEmpty(),
                    onClick = { showGroupFilter = true }
                )

                FloatingMapIconButton(
                    icon = Icons.Default.Smartphone,
                    active = state.selectedDeviceIds.isNotEmpty(),
                    onClick = { showDeviceFilter = true }
                )
                
                Box {
                    FloatingMapIconButton(
                        icon = Icons.Rounded.Layers,
                        active = state.selectedMapStyle != "Standard",
                        onClick = { showLayerMenu = true }
                    )

                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false },
                        modifier = Modifier.background(GlassSurface)
                    ) {
                        listOf("Standard", "Satellite", "Topographic").forEach { style ->
                            DropdownMenuItem(
                                text = { Text(style, color = TextPrimary) },
                                onClick = {
                                    viewModel.updateMapStyle(style)
                                    showLayerMenu = false
                                }
                            )
                        }
                    }
                }

                if (showGroupFilter) {
                    ModalBottomSheet(
                        onDismissRequest = { showGroupFilter = false },
                        sheetState = groupSheetState,
                        containerColor = ObsidianBase,
                        contentColor = TextPrimary
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Filter by Group",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (state.availableGroups.isEmpty()) {
                                Text(
                                    "No groups available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.availableGroups) { group ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(group, style = MaterialTheme.typography.bodyLarge)
                                            Checkbox(
                                                checked = state.selectedGroups.contains(group),
                                                onCheckedChange = { viewModel.toggleGroupFilter(group) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = BeaconCyan,
                                                    uncheckedColor = GlassSurfaceBorder
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }

                if (showDeviceFilter) {
                    ModalBottomSheet(
                        onDismissRequest = { showDeviceFilter = false },
                        sheetState = deviceSheetState,
                        containerColor = ObsidianBase,
                        contentColor = TextPrimary
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Filter by Device",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (state.allDevicesForFilter.isEmpty()) {
                                Text(
                                    "No devices available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.allDevicesForFilter) { device ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(device.deviceName, style = MaterialTheme.typography.bodyLarge)
                                                Text(device.deviceId, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                            }
                                            Checkbox(
                                                checked = state.selectedDeviceIds.contains(device.deviceId),
                                                onCheckedChange = { viewModel.toggleDeviceFilter(device.deviceId) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = BeaconCyan,
                                                    uncheckedColor = GlassSurfaceBorder
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        // Bottom Right Floating Recenter Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (selectedPin != null) 200.dp else 24.dp, end = 16.dp)
        ) {
            FloatingMapIconButton(
                icon = Icons.Rounded.CenterFocusStrong,
                active = false,
                onClick = { viewModel.recenterOnSelected() }
            )
        }

        // Quick Telemetry Sheet Overlay
        AnimatedVisibility(
            visible = selectedPin != null && !state.isCreationMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            selectedPin?.let { pin ->
                DeviceQuickSheet(
                    pin = pin,
                    onDismiss = { viewModel.selectPin(null) },
                    onNavigate = { /* Launch Maps intent */ }
                )
            }
        }

        // Creation Confirmation Banner
        var showConfigDialog by remember { mutableStateOf(false) }
        var editingFence by remember { mutableStateOf<com.beacon.shared.models.GeofenceZone?>(null) }

        if (state.isCreationMode && state.draftType != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = ObsidianBase.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Place geofence", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                        Text(if (state.draftType == com.beacon.shared.models.GeofenceType.RADIAL) "Radial Zone" else "Tripwire", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.cancelCreation() }) {
                            Text("Cancel", color = BeaconCrimson)
                        }
                        Button(
                            onClick = {
                                val initialFence = com.beacon.shared.models.GeofenceZone(
                                    name = "New Fence",
                                    type = state.draftType!!,
                                    centerLat = state.draftCenter?.latitude,
                                    centerLng = state.draftCenter?.longitude,
                                    radiusMeters = if (state.draftType == com.beacon.shared.models.GeofenceType.RADIAL) state.draftRadius else null,
                                    pointALat = state.draftPointA?.latitude,
                                    pointALng = state.draftPointA?.longitude,
                                    pointBLat = state.draftPointB?.latitude,
                                    pointBLng = state.draftPointB?.longitude
                                )
                                editingFence = initialFence
                                showConfigDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BeaconCyan)
                        ) {
                            Text("Configure")
                        }
                    }
                }
            }
        }

        if (showConfigDialog && editingFence != null) {
            GeofenceConfigDialog(
                geofence = editingFence!!,
                availableDevices = state.allDevicesForFilter,
                availableGroups = state.allGroups,
                onDismiss = { showConfigDialog = false },
                onSave = { 
                    viewModel.saveGeofence(it)
                    showConfigDialog = false
                },
                onDelete = { viewModel.deleteGeofence(it) }
            )
        }
    }
}

@Composable
private fun FloatingMapIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) BeaconCyan else GlassSurfaceBorder)
    ) {
        Box(modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) BeaconCyan else TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DeviceQuickSheet(
    pin: MapPinState,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusHaloBadge(status = pin.status)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = pin.deviceName, style = MaterialTheme.typography.titleMedium)
                        val (_, label) = getStatusUiConfig(pin.status)
                        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = BeaconCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${pin.speed.toInt()} km/h", style = MaterialTheme.typography.bodyMedium)
                }
                Text("GPS Accuracy: ${pin.accuracy.toInt()}m", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val gmmIntentUri = Uri.parse("google.navigation:q=${pin.latitude},${pin.longitude}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    context.startActivity(mapIntent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BeaconCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Directions, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Directions", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
