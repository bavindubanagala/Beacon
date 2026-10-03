package com.beacon.admin.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.beacon.admin.ui.theme.BeaconCrimson
import com.beacon.admin.ui.theme.ObsidianBase
import com.beacon.admin.ui.theme.TextPrimary
import com.beacon.admin.ui.theme.TextMuted

@Composable
fun ClearSosConfirmDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBase,
        title = { Text("Clear SOS?", color = TextPrimary) },
        text = {
            Text(
                text = "Only clear this if ${deviceName.ifBlank { "this device" }} is safe. This stops the SOS alarm in the Admin app and on the Tracker.",
                color = TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BeaconCrimson)
            ) {
                Text("Clear SOS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep SOS active", color = TextMuted)
            }
        }
    )
}
