package com.beacon.admin.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beacon.shared.models.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionBottomSheet(
    device: Device,
    onDismiss: () -> Unit,
    onPing: (String) -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onAssignGeofence: (String, String?) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
            Text("Actions: ${device.deviceName}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onPing(device.deviceId); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("High-Priority Location Ping")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Tracking Profile", style = MaterialTheme.typography.labelLarge)
            
            val profiles = listOf("High Accuracy", "Balanced", "Battery Saver")
            var selectedProfile by remember { mutableStateOf(profiles[1]) }
            
            profiles.forEach { profile ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedProfile == profile, onClick = { 
                        selectedProfile = profile
                        onUpdateProfile(device.deviceId, profile)
                    })
                    Text(profile)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onAssignGeofence(device.deviceId, null); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("Assign Geofence")
            }
        }
    }
}
