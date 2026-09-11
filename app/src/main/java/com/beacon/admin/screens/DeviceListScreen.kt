package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.GlassCard
import com.beacon.admin.ui.components.StatusHaloBadge
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.DeviceItemState
import com.beacon.admin.ui.viewmodels.DeviceListUiState
import com.beacon.admin.ui.viewmodels.DeviceListViewModel
import com.beacon.shared.models.DeviceStatusLight

@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    onNavigateToDetails: (String) -> Unit = {},
    viewModel: DeviceListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is DeviceListUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BeaconCyan)
            }
        }
        is DeviceListUiState.Error -> {
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
        is DeviceListUiState.Success -> {
            DeviceListContent(
                state = state,
                onSearchChange = { viewModel.onSearchQueryChanged(it) },
                onFilterChange = { viewModel.onFilterTabSelected(it) },
                onNavigateToDetails = onNavigateToDetails,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun DeviceListContent(
    state: DeviceListUiState.Success,
    onSearchChange: (String) -> Unit,
    onFilterChange: (Int) -> Unit,
    onNavigateToDetails: (String) -> Unit,
    modifier: Modifier
) {
    val filterTabs = listOf("All", "Active", "Standby", "Offline")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search devices by name or ID...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = BeaconCyan) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GlassSurface,
                unfocusedContainerColor = GlassSurface,
                focusedBorderColor = BeaconCyan,
                unfocusedBorderColor = GlassSurfaceBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        // Filter Category Tabs
        ScrollableTabRow(
            selectedTabIndex = state.selectedFilterTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            filterTabs.forEachIndexed { index, title ->
                val isSelected = state.selectedFilterTab == index
                Tab(
                    selected = isSelected,
                    onClick = { onFilterChange(index) },
                    text = {
                        Text(
                            text = title,
                            color = if (isSelected) BeaconCyan else TextMuted,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )
            }
        }

        // Device Cards List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.filteredDevices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onClick = { onNavigateToDetails(device.id) }
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceItemState,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusHaloBadge(status = device.status)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                val modeLabel = when(device.status) {
                    DeviceStatusLight.GREEN_LIVE -> "Live Mode"
                    DeviceStatusLight.BLUE_INTERVAL -> "Interval: ${device.activePreset}"
                    DeviceStatusLight.YELLOW_IDLE -> "Standby"
                    DeviceStatusLight.RED_OFFLINE -> "Offline"
                    DeviceStatusLight.GRAY_UNPAIRED -> "Unpaired"
                }
                Text(
                    text = "$modeLabel • Ping ${device.lastPing}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (device.batteryPercentage < 20) Icons.Rounded.BatteryAlert else Icons.Rounded.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (device.batteryPercentage < 20) BeaconAmber else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${device.batteryPercentage}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}
