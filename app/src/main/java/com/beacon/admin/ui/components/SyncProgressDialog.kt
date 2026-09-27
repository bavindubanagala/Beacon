package com.beacon.admin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.SyncStatus
import com.beacon.admin.ui.viewmodels.SyncStepLog
import com.beacon.admin.ui.viewmodels.TelemetrySyncState

@Composable
fun SyncProgressDialog(
    state: TelemetrySyncState,
    onDismiss: () -> Unit
) {
    if (!state.isVisible) return

    AlertDialog(
        onDismissRequest = {
            if (!state.isSyncing) {
                onDismiss()
            }
        },
        containerColor = ObsidianBase,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Telemetry sync progress",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = BeaconCyan,
                    trackColor = GlassSurfaceBorder
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.steps) { step ->
                        SyncStepItem(step = step)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = !state.isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BeaconCyan,
                    disabledContainerColor = GlassSurfaceBorder
                )
            ) {
                Text(
                    text = if (state.isSyncing) "Syncing..." else "Close",
                    color = if (!state.isSyncing) MaterialTheme.colorScheme.onPrimary else TextMuted
                )
            }
        }
    )
}

@Composable
private fun SyncStepItem(step: SyncStepLog) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (step.status) {
                SyncStatus.PENDING -> {
                    Icon(
                        imageVector = Icons.Rounded.HourglassEmpty,
                        contentDescription = "Pending",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                SyncStatus.IN_PROGRESS -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BeaconCyan,
                        strokeWidth = 2.dp
                    )
                }
                SyncStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
                SyncStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = "Failed",
                        tint = BeaconCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.stepName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                )
                if (!step.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = step.errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(color = BeaconCrimson)
                    )
                }
            }
        }
    }
}
