package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.GlassCard
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.utils.formatRelativeSyncTime
import com.beacon.admin.ui.viewmodels.AlertsUiState
import com.beacon.admin.ui.viewmodels.AlertsViewModel
import com.beacon.admin.ui.viewmodels.GeofenceEventUiModel
import com.beacon.shared.models.Alert
import com.beacon.shared.models.GeofenceEventType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onEventClick: (GeofenceEventUiModel) -> Unit = {},
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Security Alerts", "System Alerts")

    Scaffold(
        containerColor = ObsidianBase,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Security & Spatial Alerts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBase),
                    actions = {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(Icons.Rounded.FilterListOff, contentDescription = "Clear Filters", tint = TextMuted)
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = ObsidianBase,
                    contentColor = BeaconCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = BeaconCyan
                        )
                    },
                    divider = {}
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    text = title,
                                    color = if (selectedTabIndex == index) BeaconCyan else TextMuted,
                                    style = MaterialTheme.typography.labelLarge
                                )
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
            when (selectedTabIndex) {
                0 -> GeofenceAlertsTab(
                    uiState = uiState,
                    onTypeFilterChange = { viewModel.setEventTypeFilter(it) },
                    onEventClick = onEventClick
                )
                1 -> SystemAlertsTab(
                    alerts = uiState.alerts,
                    onResolve = { viewModel.resolveAlert(it) }
                )
            }
        }
    }
}

@Composable
private fun GeofenceAlertsTab(
    uiState: AlertsUiState,
    onTypeFilterChange: (GeofenceEventType?) -> Unit,
    onEventClick: (GeofenceEventUiModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = uiState.eventTypeFilter == null,
                    onClick = { onTypeFilterChange(null) },
                    label = { Text("ALL") },
                    colors = filterChipColors()
                )
            }
            GeofenceEventType.entries.forEach { type ->
                item {
                    FilterChip(
                        selected = uiState.eventTypeFilter == type,
                        onClick = { onTypeFilterChange(type) },
                        label = { Text(type.name.replace("_", " ")) },
                        colors = filterChipColors()
                    )
                }
            }
        }

        if (uiState.geofenceEvents.isEmpty()) {
            EmptyState(message = "No security events found matching filters.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.geofenceEvents) { model ->
                    GeofenceEventItem(
                        model = model,
                        onClick = { onEventClick(model) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemAlertsTab(
    alerts: List<Alert>,
    onResolve: (String) -> Unit
) {
    if (alerts.isEmpty()) {
        EmptyState(message = "No system alerts reported.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(alerts) { alert ->
                AlertCard(
                    alert = alert,
                    onResolve = { onResolve(alert.id) }
                )
            }
        }
    }
}

@Composable
fun GeofenceEventItem(
    model: GeofenceEventUiModel,
    onClick: () -> Unit
) {
    val event = model.event
    val (icon, color, title) = when (event.eventType) {
        GeofenceEventType.ENTER -> Triple(Icons.Rounded.Login, Color(0xFF4CAF50), "ENTERED ZONE")
        GeofenceEventType.EXIT -> Triple(Icons.Rounded.Logout, Color(0xFFF44336), "EXITED ZONE")
        GeofenceEventType.CROSS_A_TO_B, GeofenceEventType.CROSS_B_TO_A -> 
            Triple(Icons.Rounded.CompareArrows, BeaconCyan, "CROSSED TRIPWIRE")
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatRelativeSyncTime(event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${model.deviceName} → ${event.geofenceName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.5f", event.latitude)}, ${String.format("%.5f", event.longitude)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = "View on Map",
                    tint = BeaconCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.NotificationsNone,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
        }
    }
}

@Composable
fun AlertCard(
    alert: Alert,
    onResolve: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.alert_type,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (alert.is_read) TextMuted else BeaconCrimson,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatRelativeSyncTime(alert.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            if (!alert.is_read) {
                TextButton(onClick = onResolve) {
                    Text("Resolve", color = BeaconCyan)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = ObsidianBase,
    labelColor = TextMuted,
    selectedContainerColor = BeaconCyan,
    selectedLabelColor = ObsidianBase
)
