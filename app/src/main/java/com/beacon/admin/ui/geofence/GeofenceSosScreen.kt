package com.beacon.admin.ui.geofence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.BeaconMapComponent
import com.beacon.admin.ui.theme.BeaconCrimson
import com.beacon.admin.ui.theme.GlassSurface
import com.beacon.shared.models.GeofenceType
import org.osmdroid.util.GeoPoint

@Composable
fun GeofenceSosScreen(viewModel: GeofenceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var draftCenter by remember { mutableStateOf(GeoPoint(1.35, 103.87)) }
    var radius by remember { mutableStateOf(500.0) }
    
    // Deep link response: Focus on target device if provided
    LaunchedEffect(uiState.targetDeviceId) {
        if (uiState.targetDeviceId != null && uiState.targetDeviceId != "create") {
            // Logic to fetch device location and center map
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.error != null) {
            Text(
                text = uiState.error ?: "Unable to load geofences",
                color = BeaconCrimson,
                modifier = Modifier.padding(16.dp)
            )
        } else if (!uiState.isLoading && uiState.sosAlerts.isEmpty() && uiState.zones.isEmpty()) {
            Text(
                text = "No geofences or SOS alerts",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        uiState.sosAlerts.forEach { alert ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = BeaconCrimson.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SOS ALERT", color = BeaconCrimson)
                    Button(onClick = { viewModel.resolveSos(alert["id"] as String) }) {
                        Text("Acknowledge")
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            BeaconMapComponent(
                modifier = Modifier.fillMaxSize(),
                initialLat = 1.35,
                initialLng = 103.87,
                isCreationMode = uiState.isCreationMode,
                draftGeofenceType = GeofenceType.RADIAL,
                draftCenter = draftCenter,
                draftRadiusMeters = radius,
                onDraftCenterMoved = { draftCenter = it }
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Radius: ${radius.toInt()}m")
            Slider(value = radius.toFloat(), onValueChange = { radius = it.toDouble() }, valueRange = 50f..5000f)
            Button(onClick = { /* save logic */ }, modifier = Modifier.fillMaxWidth()) {
                Text(if (uiState.isCreationMode) "Save Safe Zone" else "Open Management")
            }
        }
    }
}
