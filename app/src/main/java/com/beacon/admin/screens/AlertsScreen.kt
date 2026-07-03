package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.shared.models.Alert
import com.beacon.admin.repository.AlertRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(alertRepository: AlertRepository) {

    val activeAlerts = remember { mutableStateOf<List<Alert>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val selectedTab = remember { mutableStateOf(0) }
    val errorMessage = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val result = alertRepository.getActiveAlerts()

        if (result.isSuccess) {
            activeAlerts.value = result.getOrDefault(emptyList())
        } else {
            errorMessage.value =
                result.exceptionOrNull()?.message ?: "Failed to load alerts"
        }

        isLoading.value = false
    }

    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Text(
                "Alerts",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (errorMessage.value.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFE57373)
                ) {
                    Text(
                        errorMessage.value,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            ScrollableTabRow(selectedTabIndex = selectedTab.value) {
                Tab(
                    selected = selectedTab.value == 0,
                    onClick = { selectedTab.value = 0 }
                ) {
                    Text("Active (${activeAlerts.value.size})", modifier = Modifier.padding(8.dp))
                }

                Tab(
                    selected = selectedTab.value == 1,
                    onClick = { selectedTab.value = 1 }
                ) {
                    Text("History", modifier = Modifier.padding(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab.value == 0) {

                if (activeAlerts.value.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No active alerts 🎉",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn {
                        items(activeAlerts.value) { alert ->
                            AlertCard(alert)
                        }
                    }
                }

            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("History coming soon", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: Alert) {

    val formattedTime = remember(alert.created_at) {
        val time = alert.created_at ?: 0L
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(time))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(modifier = Modifier.fillMaxWidth()) {

                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text = alert.alert_type,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = alert.device_id.take(8),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                val severityColor = when (alert.alert_severity) {
                    "CRITICAL" -> Color(0xFFE57373)
                    "WARNING" -> Color(0xFFFFC107)
                    else -> Color(0xFF29B6F6)
                }

                Box(
                    modifier = Modifier
                        .background(severityColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = alert.alert_severity,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formattedTime,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}
