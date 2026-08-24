package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.shared.models.Alert
import com.beacon.admin.repository.AlertRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsNone
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(authManager: com.beacon.admin.auth.AuthManager, alertRepository: AlertRepository) {

    val currentUserId = authManager.getCurrentUser()?.uid ?: ""
    val alerts = remember { mutableStateOf<List<Alert>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val isClearing = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // Filter Chips States
    val filters = listOf("All", "Unread", "Geofence", "Battery", "Offline")
    var selectedFilter by remember { mutableStateOf("All") }

    fun loadAlerts() {
        if (currentUserId.isEmpty()) return
        isLoading.value = true
        scope.launch {
            val result = alertRepository.getActiveAlerts(currentUserId)
            if (result.isSuccess) {
                alerts.value = result.getOrDefault(emptyList())
            } else {
                errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to load alerts"
            }
            isLoading.value = false
        }
    }

    LaunchedEffect(currentUserId) {
        loadAlerts()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Alerts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (alerts.value.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            isClearing.value = true
                            scope.launch {
                                val result = alertRepository.clearAllAlerts(currentUserId)
                                if (result.isSuccess) {
                                    alerts.value = emptyList()
                                } else {
                                    errorMessage.value = "Failed to clear alerts"
                                }
                                isClearing.value = false
                            }
                        },
                        enabled = !isClearing.value,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isClearing.value) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Clear All")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage.value.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            errorMessage.value,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { errorMessage.value = "" }) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.take(3).forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (alerts.value.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                androidx.compose.material.icons.Icons.Rounded.NotificationsNone,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No active alerts 🎉",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { loadAlerts() }) {
                                Text("Refresh")
                            }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(alerts.value) { alert ->
                            AlertCard(alert)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertCard(alert: Alert) {
    val formattedTime = remember(alert.created_at) {
        val time = alert.created_at
        SimpleDateFormat("HH:mm:ss · MMM dd", Locale.getDefault()).format(Date(time))
    }

    val severityColor = when (alert.alert_severity) {
        "CRITICAL" -> MaterialTheme.colorScheme.error
        "WARNING" -> Color(0xFFFBC02D)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(severityColor)
            )

            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alert.alert_type.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = severityColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            text = "DEVICE: ${alert.device_id.take(8).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    
                    Badge(
                        containerColor = severityColor.copy(alpha = 0.1f),
                        contentColor = severityColor
                    ) {
                        Text(
                            alert.alert_severity, 
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
