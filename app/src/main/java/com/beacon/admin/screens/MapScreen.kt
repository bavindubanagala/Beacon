package com.beacon.admin.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.beacon.shared.constants.RealtimeDBPaths
import com.beacon.admin.repository.FenceRepository
import com.beacon.shared.models.Fence
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

val CartoDbDark = object : OnlineTileSourceBase(
    "CartoDbDark", 0, 20, 256, ".png",
    arrayOf("https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/", "https://c.basemaps.cartocdn.com/dark_all/")
) {
    override fun getTileURLString(pTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pTileIndex) + "/" + MapTileIndex.getX(pTileIndex) + "/" + MapTileIndex.getY(pTileIndex) + mImageFilenameEnding
    }
}

val CartoDbPositron = object : OnlineTileSourceBase(
    "CartoDbPositron", 0, 20, 256, ".png",
    arrayOf("https://a.basemaps.cartocdn.com/light_all/", "https://b.basemaps.cartocdn.com/light_all/", "https://c.basemaps.cartocdn.com/light_all/")
) {
    override fun getTileURLString(pTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pTileIndex) + "/" + MapTileIndex.getX(pTileIndex) + "/" + MapTileIndex.getY(pTileIndex) + mImageFilenameEnding
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    initialDeviceId: String? = null,
    onBack: () -> Unit = {},
    fenceRepository: FenceRepository
) {
    val context = LocalContext.current
    val isDarkMode = androidx.compose.foundation.isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    
    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))

    val liveLocations = remember { mutableStateMapOf<String, Map<String, Any>>() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCentered by remember { mutableStateOf(false) }
    
    // Filters
    val filters = listOf("Live", "Offline", "Not Tracking", "Fences")
    val selectedFilters = remember { mutableStateListOf("Live", "Fences") }

    // Geofences
    val fences = remember { mutableStateListOf<Fence>() }
    var showFenceSheet by remember { mutableStateOf(false) }
    var selectedFence by remember { mutableStateOf<Fence?>(null) }

    LaunchedEffect(Unit) {
        fenceRepository.getAllFences().onSuccess {
            fences.clear()
            fences.addAll(it)
        }
    }

    DisposableEffect(Unit) {
        val database = com.beacon.admin.repository.RealtimeLocationRepository.getInstance()
        val liveRef = database.getReference(RealtimeDBPaths.LIVE_LOCATIONS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val deviceId = child.key ?: return@forEach
                    val data = child.value as? Map<String, Any> ?: return@forEach
                    liveLocations[deviceId] = data
                    
                    // Update Markers
                    mapView?.let { mv ->
                        val lat = data["latitude"] as? Double ?: 0.0
                        val lon = data["longitude"] as? Double ?: 0.0
                        if (lat != 0.0 && lon != 0.0) {
                            val marker = Marker(mv)
                            marker.position = GeoPoint(lat, lon)
                            marker.title = deviceId
                            mv.overlays.add(marker)
                            
                            if (!hasCentered || deviceId == initialDeviceId) {
                                mv.controller.setCenter(GeoPoint(lat, lon))
                                if (deviceId == initialDeviceId) hasCentered = true
                            }
                            mv.invalidate()
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        liveRef.addValueEventListener(listener)
        onDispose { liveRef.removeEventListener(listener) }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(
                    onClick = {
                        selectedFence = Fence(
                            centerLat = mapView?.mapCenter?.latitude ?: 0.0,
                            centerLng = mapView?.mapCenter?.longitude ?: 0.0
                        )
                        showFenceSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Rounded.AddLocation, contentDescription = "Add Fence")
                }

                FloatingActionButton(
                    onClick = {
                        val lastLoc = liveLocations.values.firstOrNull()
                        if (lastLoc != null) {
                            val lat = lastLoc["latitude"]
                            val lon = lastLoc["longitude"]
                            val uri = Uri.parse("google.navigation:q=$lat,$lon")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Rounded.Directions, contentDescription = "Directions")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(if (isDarkMode) CartoDbDark else CartoDbPositron)
                        setMultiTouchControls(true)
                        val defaultZoom = if (initialDeviceId != null) 18.0 else 14.0
                        controller.setZoom(defaultZoom)
                        mapView = this
                        
                        // Click events for selecting fences or adding new ones
                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                // Logic to select existing fence could go here
                                return false
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean {
                                selectedFence = Fence(centerLat = p.latitude, centerLng = p.longitude)
                                showFenceSheet = true
                                return true
                            }
                        })
                        overlays.add(eventsOverlay)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mv ->
                    mv.setTileSource(if (isDarkMode) CartoDbDark else CartoDbPositron)
                    mv.overlays.filterIsInstance<Polygon>().forEach { mv.overlays.remove(it) }
                    
                    if (selectedFilters.contains("Fences")) {
                        fences.forEach { fence ->
                            val polygon = Polygon(mv)
                            polygon.points = Polygon.pointsAsCircle(GeoPoint(fence.centerLat, fence.centerLng), fence.radiusMeters)
                            polygon.title = fence.name
                            
                            if (fence.type == "zone") {
                                polygon.fillPaint.color = 0x2200D9E8.toInt()
                                polygon.outlinePaint.color = 0xFF00D9E8.toInt()
                            } else {
                                polygon.fillPaint.color = Color.Transparent.hashCode()
                                polygon.outlinePaint.color = 0xFF8B7FFF.toInt()
                            }
                            polygon.outlinePaint.strokeWidth = 2f
                            
                            polygon.setOnClickListener { _, _, _ ->
                                selectedFence = fence
                                showFenceSheet = true
                                true
                            }
                            mv.overlays.add(polygon)
                        }
                    }
                    mv.invalidate()
                }
            )

            // Filter Chips Overlay
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilters.contains(filter),
                        onClick = {
                            if (selectedFilters.contains(filter)) selectedFilters.remove(filter)
                            else selectedFilters.add(filter)
                        },
                        label = { Text(filter) }
                    )
                }
            }

            // Attribution
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).padding(bottom = 16.dp)
            ) {
                Text(
                    "© OpenStreetMap contributors",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    if (showFenceSheet && selectedFence != null) {
        FenceEditSheet(
            fence = selectedFence!!,
            onDismiss = { showFenceSheet = false; selectedFence = null },
            onSave = { updatedFence ->
                scope.launch {
                    fenceRepository.saveFence(updatedFence)
                    fenceRepository.getAllFences().onSuccess {
                        fences.clear()
                        fences.addAll(it)
                    }
                }
                showFenceSheet = false
                selectedFence = null
            },
            onDelete = { id ->
                scope.launch {
                    fenceRepository.deleteFence(id)
                    fenceRepository.getAllFences().onSuccess {
                        fences.clear()
                        fences.addAll(it)
                    }
                }
                showFenceSheet = false
                selectedFence = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenceEditSheet(
    fence: Fence,
    onDismiss: () -> Unit,
    onSave: (Fence) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(fence.name) }
    var type by remember { mutableStateOf(fence.type) }
    var radius by remember { mutableStateOf(fence.radiusMeters.toFloat()) }
    var alertOnEnter by remember { mutableStateOf(fence.alertOnEnter) }
    var alertOnExit by remember { mutableStateOf(fence.alertOnExit) }
    var alertFrequency by remember { mutableStateOf(fence.alertFrequency) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = if (fence.id.isEmpty()) "New Geofence" else "Edit Geofence",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Fence Name (e.g. Home)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Fence Type", style = MaterialTheme.typography.titleSmall)
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

            Text("Radius: ${radius.toInt()}m", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Slider(
                value = radius,
                onValueChange = { radius = it },
                valueRange = 10f..1000f
            )

            if (type == "zone") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alertOnEnter, onCheckedChange = { alertOnEnter = it })
                    Text("Alert on Enter", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alertOnExit, onCheckedChange = { alertOnExit = it })
                    Text("Alert on Exit", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Alert Frequency", style = MaterialTheme.typography.titleSmall)
                // Simplified dropdown/choice
                listOf("every_time" to "Every time", "once_ever" to "Once ever", "once_per_day" to "Once per day").forEach { (freq, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = alertFrequency == freq, onClick = { alertFrequency = freq })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onSave(fence.copy(
                        name = name,
                        type = type,
                        radiusMeters = radius.toDouble(),
                        alertOnEnter = alertOnEnter,
                        alertOnExit = alertOnExit,
                        alertFrequency = alertFrequency
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Geofence")
            }

            if (fence.id.isNotEmpty()) {
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
