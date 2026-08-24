package com.beacon.tracker.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else null

    var batteryIgnored by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    LaunchedEffect(Unit) {
        // Battery check on resume
        batteryIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Beacon Tracker Setup", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("To provide accurate tracking, please grant these permissions.", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { locationPermissions.launchMultiplePermissionRequest() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !locationPermissions.allPermissionsGranted
        ) {
            Text(if (locationPermissions.allPermissionsGranted) "Location Granted" else "Grant Location")
        }

        if (notificationPermission != null) {
            Button(
                onClick = { notificationPermission.launchPermissionRequest() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !notificationPermission.status.isGranted
            ) {
                Text(if (notificationPermission.status.isGranted) "Notifications Granted" else "Grant Notifications")
            }
        }

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !batteryIgnored
        ) {
            Text(if (batteryIgnored) "Battery Optimization Disabled" else "Disable Battery Optimization")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth(),
            enabled = locationPermissions.allPermissionsGranted && (notificationPermission?.status?.isGranted ?: true) && batteryIgnored
        ) {
            Text("Complete Setup")
        }
    }
}
