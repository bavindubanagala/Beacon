package com.beacon.admin.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beacon.admin.ui.theme.BeaconCrimson
import com.beacon.shared.models.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionBottomSheet(
    device: Device,
    onDismiss: () -> Unit,
    onPing: (String) -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onAssignGeofence: (String, String?) -> Unit,
    onRenameClick: (String) -> Unit = {},
    onUnpairClick: (String) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actions: ${device.deviceName.ifBlank { device.deviceId }}",
                style = MaterialTheme.typography.titleLarge
            )

            Button(
                onClick = {
                    onPing(device.deviceId)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("High-Priority Location Ping")
            }

            Text("Tracking Profile", style = MaterialTheme.typography.labelLarge)
            
            val profiles = listOf("High Accuracy", "Balanced", "Battery Saver")
            var selectedProfile by remember { mutableStateOf(profiles[1]) }
            
            profiles.forEach { profile ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedProfile == profile,
                        onClick = { 
                            selectedProfile = profile
                            onUpdateProfile(device.deviceId, profile)
                        }
                    )
                    Text(profile)
                }
            }

            Button(
                onClick = {
                    onAssignGeofence(device.deviceId, null)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Assign Geofence")
            }

            OutlinedButton(
                onClick = {
                    onRenameClick(device.deviceId)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rename Device")
            }

            Button(
                onClick = {
                    onUnpairClick(device.deviceId)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BeaconCrimson
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unpair Device", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
