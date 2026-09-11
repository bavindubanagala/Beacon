package com.beacon.admin.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beacon.admin.ui.components.BeaconMapComponent
import com.beacon.admin.ui.components.GlassCard
import com.beacon.admin.ui.theme.*
import com.beacon.admin.ui.viewmodels.HistoryViewModel
import com.beacon.shared.models.Location
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )
    
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var showDeviceMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance().apply { timeInMillis = it }
                        viewModel.onDateSelected(cal)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetContainerColor = ObsidianBase,
        sheetContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "Telemetry & Logs",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Telemetry Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryMiniCard("Dist", String.format("%.1f km", uiState.totalDistanceKm), Modifier.weight(1f))
                    TelemetryMiniCard("Avg Spd", String.format("%.0f kph", uiState.averageSpeedKph), Modifier.weight(1f))
                    TelemetryMiniCard("Max Spd", String.format("%.0f kph", uiState.maxSpeedKph), Modifier.weight(1f))
                    TelemetryMiniCard("Bat Δ", "${uiState.batteryDischarge}%", Modifier.weight(1f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Logs List
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.locations.reversed()) { loc ->
                        LogItem(loc, timeFormat)
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Route History", fontWeight = FontWeight.Bold) },
                actions = {
                    // Device Picker
                    Box {
                        TextButton(onClick = { showDeviceMenu = true }) {
                            val currentDevice = availableDevices.find { it.deviceId == uiState.deviceId }
                            Text(currentDevice?.deviceName ?: "Select Device", color = BeaconCyan)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = BeaconCyan)
                        }
                        DropdownMenu(
                            expanded = showDeviceMenu,
                            onDismissRequest = { showDeviceMenu = false },
                            modifier = Modifier.background(GlassSurface)
                        ) {
                            availableDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = { Text(device.deviceName, color = TextPrimary) },
                                    onClick = {
                                        viewModel.onDeviceSelected(device.deviceId)
                                        showDeviceMenu = false
                                    }
                                )
                            }
                        }
                    }
                    // Date Picker
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = "Pick Date", tint = BeaconCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBase)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Map
            val pathPoints = remember(uiState.locations) {
                uiState.locations.map { GeoPoint(it.latitude, it.longitude) }
            }
            val playbackPos = remember(uiState.locations, uiState.playbackIndex) {
                if (uiState.locations.isNotEmpty()) {
                    val loc = uiState.locations[uiState.playbackIndex]
                    GeoPoint(loc.latitude, loc.longitude)
                } else null
            }

            BeaconMapComponent(
                modifier = Modifier.fillMaxSize(),
                historicalPath = pathPoints,
                playbackMarker = playbackPos,
                initialLat = playbackPos?.latitude ?: 1.35,
                initialLng = playbackPos?.longitude ?: 103.87,
                initialZoom = 15.0
            )

            // Playback Controls Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .fillMaxWidth()
            ) {
                // Time Scrubber
                if (uiState.locations.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassSurface.copy(alpha = 0.8f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            timeFormat.format(Date(uiState.locations[uiState.playbackIndex].timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Slider(
                            value = uiState.playbackIndex.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toInt()) },
                            valueRange = 0f..(uiState.locations.size - 1).coerceAtLeast(0).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = BeaconCyan,
                                activeTrackColor = BeaconCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed Multiplier
                    PlaybackSpeedChip(uiState.playbackSpeed) { viewModel.setPlaybackSpeed(it) }
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    // Play/Pause
                    FloatingActionButton(
                        onClick = { viewModel.togglePlayback() },
                        containerColor = BeaconCyan,
                        contentColor = Color.Black,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Playback",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(64.dp)) // Offset to balance speed chip if needed
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniCard(title: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun LogItem(loc: Location, timeFormat: SimpleDateFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GlassSurface.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${loc.latitude}, ${loc.longitude}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("Speed: ${String.format("%.1f", loc.speed * 3.6)} kph", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Text(timeFormat.format(Date(loc.timestamp)), style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun PlaybackSpeedChip(currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    val speeds = listOf(1f, 2f, 5f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurface)
            .padding(4.dp)
    ) {
        speeds.forEach { speed ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (currentSpeed == speed) BeaconCyan else Color.Transparent)
                    .clickable { onSpeedChange(speed) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "${speed.toInt()}x",
                    color = if (currentSpeed == speed) Color.Black else TextPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
