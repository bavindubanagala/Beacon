package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beacon.shared.models.Fence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenceEditSheet(
    fence: Fence,
    onDismiss: () -> Unit,
    onSave: (Fence) -> Unit,
    onMove: (Fence) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(fence.name) }
    var type by remember { mutableStateOf(fence.type) }
    var radius by remember { mutableStateOf(fence.radiusMeters.toFloat()) }
    var alertOnEnter by remember { mutableStateOf(fence.alertOnEnter) }
    var alertOnExit by remember { mutableStateOf(fence.alertOnExit) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (fence.id.isEmpty()) "Create Fence" else "Edit Fence",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Fence Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Home, Office") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Type", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zone" to "Zone", "checkpoint" to "Checkpoint").forEach { (t, label) ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Radius: ${radius.toInt()}m", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = radius,
                onValueChange = { radius = it },
                valueRange = 50f..1000f,
                steps = 18 // (1000-50)/50 - 1 = 18 steps for 50m increments if starting from 50
            )

            if (type == "zone") {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Alerts", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alertOnEnter, onCheckedChange = { alertOnEnter = it })
                    Text("Alert on Enter", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alertOnExit, onCheckedChange = { alertOnExit = it })
                    Text("Alert on Exit", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onSave(
                        fence.copy(
                            name = name,
                            type = type,
                            radiusMeters = radius.toDouble(),
                            alertOnEnter = alertOnEnter,
                            alertOnExit = alertOnExit
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Save Fence")
            }

            if (fence.id.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onMove(
                            fence.copy(
                                name = name,
                                type = type,
                                radiusMeters = radius.toDouble(),
                                alertOnEnter = alertOnEnter,
                                alertOnExit = alertOnExit
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Reposition Fence")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onDelete(fence.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Fence")
                }
            }
        }
    }
}
