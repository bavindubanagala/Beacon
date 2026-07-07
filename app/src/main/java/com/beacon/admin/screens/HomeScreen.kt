package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.admin.repository.DeviceRepository
import com.beacon.admin.repository.AlertRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    deviceRepository: DeviceRepository,
    alertRepository: AlertRepository,
    onAddDeviceClick: () -> Unit
) {
    var deviceCount by remember { mutableStateOf(0) }
    var onlineCount by remember { mutableStateOf(0) }
    var activeAlertCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Simple counts for the summary
        val dResult = deviceRepository.getAllDevices()
        if (dResult.isSuccess) {
            val devices = dResult.getOrDefault(emptyList())
            deviceCount = devices.size
            onlineCount = devices.count { it.status == "online" }
        }
        
        val aResult = alertRepository.getActiveAlerts()
        if (aResult.isSuccess) {
            activeAlertCount = aResult.getOrDefault(emptyList()).size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "System Overview",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            fontWeight = FontWeight.Bold
        )

        // Stats Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "Devices",
                value = deviceCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Online",
                value = onlineCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Alerts",
                value = activeAlertCount.toString(),
                modifier = Modifier.weight(1f),
                isAlert = activeAlertCount > 0
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Primary Action
        Button(
            onClick = onAddDeviceClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add New Device", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.weight(1f))
        
        // Technical feel - small footer
        Text(
            text = "BEACON CONTROL HUB V2.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, isAlert: Boolean = false) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
