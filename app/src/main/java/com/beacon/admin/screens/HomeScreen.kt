package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.GlassCard
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.utils.buildModeLabel
import com.beacon.admin.ui.utils.formatRelativeSyncTime
import com.beacon.admin.ui.utils.getStatusUiConfig
import com.beacon.admin.ui.utils.normalizeTrackingMode
import com.beacon.admin.ui.viewmodels.HomeViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToDevices: (String?) -> Unit = {},
    onNavigateToGeofence: (String?) -> Unit = {},
    onNavigateToHistory: (String, Long?) -> Unit = { _, _ -> },
    onOpenDevice: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val recentActivity by viewModel.recentActivity.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveSelectedId = if (selectedDeviceId != null && devices.any { it.deviceId == selectedDeviceId }) {
        selectedDeviceId
    } else {
        devices.firstOrNull()?.deviceId
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBase,
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToDevices("pair") }, containerColor = BeaconCyan) {
                Icon(Icons.Rounded.Add, contentDescription = "Quick Action")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Text("Dashboard", style = MaterialTheme.typography.headlineMedium, color = TextPrimary) }

            // Device Story Avatars Row
            item {
                if (devices.isEmpty()) {
                    Text(
                        text = "No devices paired yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(devices, key = { it.deviceId }) { device ->
                            val isSelected = device.deviceId == effectiveSelectedId
                            val ringColor = if (device.isEmergencyMode) BeaconCrimson else getStatusUiConfig(device.statusLight).first
                            val ringThickness = if (device.isEmergencyMode) 4.dp else if (isSelected) 3.dp else 2.dp
                            val fillColor = device.customColor?.let { Color(it).copy(alpha = 0.25f) } ?: GlassSurface
                            val initialLetter = device.deviceName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable { selectedDeviceId = device.deviceId },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .border(ringThickness, ringColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(if (isSelected) ringThickness + 4.dp else ringThickness)
                                            .clip(CircleShape)
                                            .background(fillColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initialLetter,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = device.deviceName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) TextPrimary else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Big Swipeable Device Card Pager
            item {
                if (devices.isNotEmpty()) {
                    val initialPageIndex = remember(devices) {
                        (devices.indexOfFirst { it.deviceId == effectiveSelectedId }).coerceAtLeast(0)
                    }
                    val pagerState = rememberPagerState(
                        initialPage = initialPageIndex,
                        pageCount = { devices.size }
                    )

                    // Sync when user taps an avatar
                    LaunchedEffect(effectiveSelectedId) {
                        if (effectiveSelectedId != null && devices.isNotEmpty()) {
                            val targetIndex = devices.indexOfFirst { it.deviceId == effectiveSelectedId }
                            if (targetIndex in devices.indices && targetIndex != pagerState.currentPage) {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        }
                    }

                    // Sync when pager settles on a page
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }.collect { page ->
                            if (page in devices.indices) {
                                val settledDeviceId = devices[page].deviceId
                                if (selectedDeviceId != settledDeviceId) {
                                    selectedDeviceId = settledDeviceId
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            pageSpacing = 12.dp,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            key = { page -> if (page in devices.indices) devices[page].deviceId else page },
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            if (page in devices.indices) {
                                val device = devices[page]
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (device.isEmergencyMode) {
                                                Modifier.border(2.dp, BeaconCrimson, RoundedCornerShape(16.dp))
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    onClick = { onOpenDevice(device.deviceId) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Top row: Name + Status chip
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = device.deviceName,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            val (statusColor, statusText) = getStatusUiConfig(device.statusLight)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GlassSurfaceBorder)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(statusColor)
                                                )
                                                Text(
                                                    text = statusText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextPrimary
                                                )
                                            }
                                        }

                                        // Second line: Tracking mode label
                                        val normalizedMode = normalizeTrackingMode(device.trackingMode)
                                        val modeLabel = buildModeLabel(
                                            normalizedMode,
                                            device.scheduledIntervalMillis,
                                            device.liveIntervalMillis
                                        )
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (normalizedMode == "LIVE") BeaconCyan else TextSecondary
                                        )

                                        // Row with Stat blocks: Battery + Last seen
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            // Battery
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Battery",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${device.batteryLevel}%",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val batteryProgress = (device.batteryLevel / 100f).coerceIn(0f, 1f)
                                                val indicatorColor = if (device.batteryLevel < 20) BeaconAmber else BeaconCyan
                                                LinearProgressIndicator(
                                                    progress = { batteryProgress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp)),
                                                    color = indicatorColor,
                                                    trackColor = GlassSurfaceBorder
                                                )
                                            }

                                            // Last seen
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Last seen",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = formatRelativeSyncTime(device.lastSeenTimestamp),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                        }

                                        // Row of two buttons: Open device & History
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { onOpenDevice(device.deviceId) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceBorder)
                                            ) {
                                                Text("Open device", color = TextPrimary)
                                            }
                                            Button(
                                                onClick = { onNavigateToHistory(device.deviceId, null) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceBorder)
                                            ) {
                                                Text("History", color = TextPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Indicator dots below pager
                        if (devices.size >= 2) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(devices.size) { index ->
                                    val isCurrentPage = pagerState.currentPage == index
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrentPage) BeaconCyan else GlassSurfaceBorder)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Compact Metrics Row
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        title = "Total Devices",
                        value = "${metrics.totalDevices}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Online",
                        value = "${metrics.onlineDevices}",
                        color = BeaconCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToDevices("online") }
                    )
                    MetricCard(
                        title = "Active SOS",
                        value = "${metrics.activeSos}",
                        color = if (metrics.activeSos > 0) BeaconCrimson else TextPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToGeofence(null) }
                    )
                    MetricCard(
                        title = "Low Battery",
                        value = "${metrics.lowBatteryCount}",
                        color = if (metrics.lowBatteryCount > 0) BeaconAmber else TextPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToDevices("low_battery") }
                    )
                }
            }

            // 2. Quick Action Shortcuts
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onNavigateToGeofence("create") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddLocationAlt,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Zone", color = TextPrimary)
                    }
                    Button(
                        onClick = { onNavigateToDevices("map") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Map,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Map", color = TextPrimary)
                    }
                }
            }

            // 3. Real-Time Activity Feed
            item { Text("Recent Activity", style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
            
            if (recentActivity.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All quiet. No recent activity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                items(recentActivity.take(10)) { item ->
                    val severityColor = when (item.severity.uppercase()) {
                        "CRITICAL" -> BeaconCrimson
                        "WARNING" -> BeaconAmber
                        else -> BeaconCyan
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { onNavigateToHistory(item.deviceId, item.timestamp) }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(severityColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = when (item.type) {
                                    "SOS_ACTIVE" -> Icons.Rounded.Warning
                                    "ENTER" -> Icons.Rounded.LocationOn
                                    "EXIT", "GEOFENCE_EXIT" -> Icons.Rounded.GpsOff
                                    "CROSS_A_TO_B", "CROSS_B_TO_A" -> Icons.Rounded.SwapHoriz
                                    else -> Icons.Rounded.Notifications
                                },
                                contentDescription = null,
                                tint = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(item.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Device: ${item.deviceName} | ${formatRelativeSyncTime(item.timestamp)}", 
                                    style = MaterialTheme.typography.bodySmall, 
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String, 
    value: String, 
    color: Color = TextPrimary, 
    modifier: Modifier,
    onClick: () -> Unit = {}
) {
    GlassCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
