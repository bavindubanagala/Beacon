package com.example.beaconadmin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class DeviceItem(
    val id: String = "",
    val name: String = "Unknown Device",
    val status: String = "Offline",
    val batteryLevel: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastSeen: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    onDeviceClick: (String) -> Unit = {},
    onPairDeviceClick: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val listenerRegistration: ListenerRegistration = db.collection("devices")
            .addSnapshotListener { snapshot, error ->
                isLoading = false
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        val data = doc.data ?: emptyMap<String, Any>()
                        val lat = (data["latitude"] as? Number)?.toDouble()
                            ?: (data["lat"] as? Number)?.toDouble() ?: 0.0
                        val lng = (data["longitude"] as? Number)?.toDouble()
                            ?: (data["lng"] as? Number)?.toDouble() ?: 0.0
                        val battery = (data["batteryLevel"] as? Number)?.toDouble()
                            ?: (data["battery"] as? Number)?.toDouble() ?: 0.0
                        val lastSeenVal = (data["lastSeen"] as? Number)?.toLong()
                            ?: (data["updatedAt"] as? Number)?.toLong() ?: 0L

                        DeviceItem(
                            id = doc.id,
                            name = doc.getString("deviceName") ?: doc.getString("name") ?: "Device ${doc.id.take(4)}",
                            status = doc.getString("status") ?: if (doc.getBoolean("is_paired") == true || doc.getBoolean("paired") == true) "Paired" else "Offline",
                            batteryLevel = battery,
                            latitude = lat,
                            longitude = lng,
                            lastSeen = lastSeenVal
                        )
                    }
                    devices = list
                }
            }

        onDispose {
            listenerRegistration.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beacon Dashboard") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign Out"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPairDeviceClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Pair Device"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading devices...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (devices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No devices paired yet.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap '+' to pair a new device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceClick(device.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: DeviceItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: ${device.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.latitude != 0.0 || device.longitude != 0.0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Location: %.4f, %.4f".format(device.latitude, device.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = device.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (device.batteryLevel > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${device.batteryLevel.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
