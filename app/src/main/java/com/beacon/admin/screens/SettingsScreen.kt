package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.admin.auth.AuthManager

@Composable
fun SettingsScreen(
    authManager: AuthManager,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val defaultTrackingInterval = remember { mutableStateOf(60f) }
    val lowBatteryThreshold = remember { mutableStateOf(20f) }
    val offlineTimeout = remember { mutableStateOf(300f) }

    Surface(color = MaterialTheme.colors.background) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Text(
                    "Settings",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Theme Setting
            item {
                SettingCard(
                    title = "Dark Mode",
                    content = {
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { onDarkModeChange(it) }
                        )
                    }
                )
            }

            // Tracking Interval
            item {
                SettingCard(
                    title = "Default Tracking Interval",
                    subtitle = "${defaultTrackingInterval.value.toInt()} seconds",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = defaultTrackingInterval.value,
                                onValueChange = { defaultTrackingInterval.value = it },
                                valueRange = 30f..300f,
                                steps = 26,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Range: 30 - 300 seconds",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                )
            }

            // Low Battery Threshold
            item {
                SettingCard(
                    title = "Low Battery Alert Threshold",
                    subtitle = "${lowBatteryThreshold.value.toInt()}%",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = lowBatteryThreshold.value,
                                onValueChange = { lowBatteryThreshold.value = it },
                                valueRange = 5f..50f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Notify when battery below threshold",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                )
            }

            // Offline Timeout
            item {
                SettingCard(
                    title = "Offline Alert Timeout",
                    subtitle = "${offlineTimeout.value.toInt()} seconds",
                    content = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = offlineTimeout.value,
                                onValueChange = { offlineTimeout.value = it },
                                valueRange = 60f..900f,
                                steps = 16,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Alert if device offline longer than",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                )
            }

            // Save Button
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { /* Save settings */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Settings")
                }
            }

            // Logout Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        authManager.signOut()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text("Logout")
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 14.sp
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}
