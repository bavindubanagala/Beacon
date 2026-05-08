package com.beacon.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val historyAlerts = remember { mutableStateOf<List<Alert>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val selectedTab = remember { mutableStateOf(0) }
    val errorMessage = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val activeResult = alertRepository.getActiveAlerts()
        if (activeResult.isSuccess) {
            activeAlerts.value = activeResult.getOrNull() ?: emptyList()
        } else {
            errorMessage.value = activeResult.exceptionOrNull()?.message ?: "Failed to load alerts"
        }
        isLoading.value = false
    }

    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Text(
                "Alerts",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Error Message
            if (errorMessage.value.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFE57373),
                    elevation = 4.dp
                ) {
                    Text(
                        errorMessage.value,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab.value,
                modifier = Modifier.fillMaxWidth()
            ) {
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

            // Loading State
            if (isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab.value == 0) {
                // Active Alerts
                if (activeAlerts.value.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No active alerts. Great!",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(activeAlerts.value) { alert ->
                            AlertCard(alert, alertRepository)
                        }
                    }
                }
            } else {
                // History Tab
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Alert history coming soon",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: Alert, alertRepository: AlertRepository) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        alert.alert_type,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        alert.device_id.take(8),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Severity Badge
                Box(
                    modifier = Modifier
                        .background(
                            when (alert.alert_severity) {
                                "CRITICAL" -> Color(0xFFE57373)
                                "WARNING" -> Color(0xFFFFC107)
                                else -> Color(0xFF29B6F6)
                            }
                        )
                        .padding(4.dp)
                ) {
                    Text(
                        alert.alert_severity,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp
            Text(
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(alert.created_at ?: 0)),
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { /* Resolve action */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resolve")
                }
                Spacer(modifier = Modifier.padding(4.dp))
                Button(
                    onClick = { /* Dismiss action */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

import androidx.compose.material.MaterialTheme
