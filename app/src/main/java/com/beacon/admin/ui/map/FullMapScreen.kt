package com.beacon.admin.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.BeaconMapComponent
import com.beacon.admin.ui.theme.BeaconCrimson
import com.beacon.admin.ui.theme.BeaconCyan
import com.beacon.admin.ui.theme.GlassSurface
import com.beacon.admin.ui.theme.ObsidianBase
import com.beacon.admin.ui.theme.TextMuted
import com.beacon.admin.ui.theme.TextPrimary
import com.beacon.admin.ui.utils.formatRelativeSyncTime
import com.beacon.admin.ui.utils.getStatusUiConfig
import com.beacon.admin.ui.viewmodels.MapUiState
import com.beacon.admin.ui.viewmodels.MapViewModel
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullMapScreen(
    viewModel: MapViewModel
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val markers by viewModel.mapMarkers.collectAsStateWithLifecycle()
    
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    when (val state = uiState) {
        is MapUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(ObsidianBase), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BeaconCyan)
            }
        }
        is MapUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().background(ObsidianBase).padding(32.dp),
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
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = 120.dp,
                sheetContainerColor = ObsidianBase,
                sheetContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Devices",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(devices) { device ->
                                DeviceDrawerRow(
                                    name = device.deviceName.ifEmpty { "Device ${device.deviceId.take(4)}" },
                                    status = device.statusLight,
                                    lastSeen = device.lastSeenTimestamp,
                                    onClick = {
                                        viewModel.selectPin(device.deviceId)
                                    }
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    BeaconMapComponent(
                        modifier = Modifier.fillMaxSize(),
                        initialLat = MapViewModel.DEFAULT_SRI_LANKA_CENTER.first,
                        initialLng = MapViewModel.DEFAULT_SRI_LANKA_CENTER.second,
                        initialZoom = 13.0,
                        markers = markers,
                        onMarkerClick = { id -> viewModel.selectPin(id) },
                        centerOn = state.centerOn?.let { GeoPoint(it.first, it.second) },
                        targetZoom = state.targetZoom
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceDrawerRow(
    name: String,
    status: com.beacon.shared.models.DeviceStatusLight,
    lastSeen: Long,
    onClick: () -> Unit
) {
    val (statusColor, statusLabel) = getStatusUiConfig(status)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassSurface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                text = "$statusLabel • ${formatRelativeSyncTime(lastSeen)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}
