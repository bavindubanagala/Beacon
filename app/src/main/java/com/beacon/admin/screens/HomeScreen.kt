package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.HomeViewModel
import com.beacon.admin.ui.components.GlassCard
import com.beacon.admin.ui.utils.formatRelativeSyncTime
import java.util.*

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToDevices: (String?) -> Unit = {},
    onNavigateToGeofence: (String?) -> Unit = {},
    onNavigateToHistory: (String, Long?) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val recentAlerts by viewModel.activeAlerts.collectAsStateWithLifecycle()

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
            
            // 1. Live Metrics Grid
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Total Devices", "${metrics.totalDevices}", modifier = Modifier.weight(1f))
                    MetricCard(
                        title = "Online", 
                        value = "${metrics.onlineDevices}", 
                        color = BeaconCyan, 
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToDevices("online") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text("Create Zone", color = TextPrimary)
                    }
                    Button(
                        onClick = { onNavigateToDevices("map") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceBorder)
                    ) {
                        Text("View Map", color = TextPrimary)
                    }
                }
            }

            // 3. Real-Time Activity Feed
            item { Text("Recent Activity", style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
            
            if (recentAlerts.isEmpty()) {
                item {
                    Text(
                        "No recent activity events recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(recentAlerts.take(10)) { alert ->
                    val severityColor = when (alert.alert_severity.uppercase()) {
                        "CRITICAL" -> BeaconCrimson
                        "WARNING" -> BeaconAmber
                        else -> BeaconCyan
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { onNavigateToHistory(alert.device_id, alert.created_at) }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (alert.alert_type) {
                                    "SOS_ACTIVE" -> Icons.Rounded.Warning
                                    "GEOFENCE_EXIT" -> Icons.Rounded.GpsOff
                                    else -> Icons.Rounded.Notifications
                                },
                                contentDescription = null,
                                tint = severityColor
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(alert.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Device: ${alert.device_name} | ${formatRelativeSyncTime(alert.created_at)}", 
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
    color: androidx.compose.ui.graphics.Color = TextPrimary, 
    modifier: Modifier,
    onClick: () -> Unit = {}
) {
    GlassCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
            Text(title, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
    }
}
