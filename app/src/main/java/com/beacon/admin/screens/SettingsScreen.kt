package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.GlassCard
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onSignedOut: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBase,
        topBar = {
            TopAppBar(
                title = { Text("Fleet & Admin Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBase)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Fleet Default Tracking Policies
            SettingsSection(title = "Fleet Tracking Policies", icon = Icons.Rounded.Devices) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Default Tracking Interval", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    TrackingIntervalSelector(
                        selected = uiState.defaultTrackingInterval,
                        onSelected = { viewModel.updateDefaultTrackingInterval(it) }
                    )
                    
                    Text("Low Battery Warning Threshold: ${uiState.lowBatteryThreshold.toInt()}%", 
                        style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Slider(
                        value = uiState.lowBatteryThreshold,
                        onValueChange = { viewModel.updateLowBatteryThreshold(it) },
                        valueRange = 5f..30f,
                        colors = SliderDefaults.colors(thumbColor = BeaconCyan, activeTrackColor = BeaconCyan)
                    )

                    SettingToggleItem(
                        title = "SOS Auto-Escalation",
                        subtitle = "Automatically alert all admins on SOS",
                        checked = uiState.autoEscalateSos,
                        onCheckedChange = { viewModel.toggleAutoEscalateSos(it) }
                    )
                }
            }

            // 2. Account & Security Controls
            SettingsSection(title = "Account & Security", icon = Icons.Rounded.Security) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountInfoRow("Email", uiState.adminEmail)
                    AccountInfoRow("Role", uiState.adminRole)
                    AccountInfoRow("Last Login", uiState.lastLogin)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { viewModel.signOut(onSignedOut) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BeaconCrimson.copy(alpha = 0.2f), contentColor = BeaconCrimson),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }

            // 3. App Preferences & Maintenance
            SettingsSection(title = "System & Maintenance", icon = Icons.Rounded.Settings) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SystemStatusItem("Firestore Connection", uiState.firestoreConnected)
                    SystemStatusItem("Sync Queue", uiState.offlineSyncQueueSize == 0, "Empty")
                    
                    OutlinedButton(
                        onClick = { viewModel.clearMapCache() },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Map Tile Cache", color = TextPrimary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BeaconCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun TrackingIntervalSelector(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("High Accuracy", "Balanced", "Battery Saver")
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(GlassSurfaceBorder.copy(alpha = 0.3f)),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { option ->
            val isSelected = selected == option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) BeaconCyan else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(option, color = if (isSelected) Color.Black else TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingToggleItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = BeaconCyan))
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SystemStatusItem(label: String, isHealthy: Boolean, customValue: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isHealthy) BeaconCyan else BeaconCrimson))
            Spacer(modifier = Modifier.width(8.dp))
            Text(customValue ?: if (isHealthy) "Healthy" else "Error", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
    }
}
