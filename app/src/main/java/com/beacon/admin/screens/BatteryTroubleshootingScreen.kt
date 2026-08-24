package com.beacon.admin.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BatteryTroubleshootingScreen(onDismiss: () -> Unit) {
    val manufacturer = Build.MANUFACTURER
    
    val instructions = when (manufacturer.lowercase()) {
        "xiaomi" -> "1. Open Security App.\n2. Tap 'Battery' or 'Permissions'.\n3. Enable 'Autostart' for Beacon.\n4. Set Battery Saver to 'No restrictions'."
        "huawei" -> "1. Open Phone Manager.\n2. Tap 'Battery' > 'App launch'.\n3. Manage manually: Enable 'Auto-launch' and 'Run in background'."
        "oppo", "realme" -> "1. Open Settings.\n2. Tap 'Battery' > 'App battery management'.\n3. Find Beacon and enable 'Allow background activity'."
        "vivo", "iqoo" -> "1. Open iManager.\n2. Tap 'App info' > 'Battery'.\n3. Set to 'High background power usage'."
        "oneplus" -> "1. Open Settings.\n2. Tap 'Battery' > 'Battery optimization'.\n3. Find Beacon and set to 'Don't optimize'."
        else -> "Background processes are often killed by aggressive battery management on your device. Please find Beacon in your phone's 'Battery' or 'App Management' settings and enable 'Background Activity', 'Autostart', or 'Don't Optimize'."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Device Settings: $manufacturer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(instructions, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text("For more details, visit dontkillmyapp.com", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
