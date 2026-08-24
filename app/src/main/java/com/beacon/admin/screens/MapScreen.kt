package com.beacon.admin.screens

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    authManager: com.beacon.admin.auth.AuthManager,
    deviceRepository: com.beacon.admin.repository.DeviceRepository,
    fenceRepository: FenceRepository,
    initialDeviceId: String? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUserId = authManager.getCurrentUser()?.uid ?: ""
    val isDarkMode = androidx.compose.foundation.isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
    }
    val liveLocations = remember { mutableStateMapOf<String, Map<String, Any>>() }
    val ownedDeviceIds = remember { mutableStateListOf<String>() }
    val markers = remember { mutableStateMapOf<String, Marker>() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCentered by remember { mutableStateOf(false) }

    val eventsOverlay = remember { 
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint) = false
            override fun longPressHelper(p: GeoPoint) = false
        }) 
    }

    // Filters
    val filters = listOf("Live", "Offline", "Not Tracking", "Fences")
    val selectedFilters = remember { mutableStateListOf("Live", "Fences") }
    // Jump to Device Menu
    var showDeviceMenu by remember { mutableStateOf(false) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    val ownedDevices = remember { mutableStateListOf<com.beacon.shared.models.Device>() }
    // Geofences
    val fences = remember { mutableStateListOf<Fence>() }
    var showFenceSheet by remember { mutableStateOf(false) }
    var selectedFence by remember { mutableStateOf<Fence?>(null) }
    var draggingFence by remember { mutableStateOf<Fence?>(null) }
    // Bottom Sheet for Device Info
    var showDeviceSheet by remember { mutableStateOf<com.beacon.shared.models.Device?>(null) }

    DisposableEffect(currentUserId) {
        if (currentUserId.isEmpty()) return@DisposableEffect onDispose {}

        // 1. Listen for Firestore Device changes
        val dListener = deviceRepository.getDevicesListener(
            ownerId = currentUserId,
            onUpdate = { devices ->
                android.util.Log.d("MapScreen", "Owned devices updated: ${devices.map { it.deviceId }}")
                ownedDevices.clear()
                ownedDevices.addAll(devices)
                ownedDeviceIds.clear()
                ownedDeviceIds.addAll(devices.map { it.deviceId })
            },
            onError = { e ->
                android.util.Log.e("MapScreen", "Error listening for devices: ${e.message}")
            }
        )
        // 2. Listen for Realtime DB Location changes
        val database = com.beacon.admin.repository.RealtimeLocationRepository.getInstance()
        val liveRef = database.getReference(RealtimeDBPaths.LIVE_LOCATIONS)
        val rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val deviceId = child.key ?: return@forEach
                    val data = child.value as? Map<String, Any> ?: return@forEach

                    // Always store the data, even if not "owned" yet (to handle race conditions)
                    liveLocations[deviceId] = data

                    // Centering Logic for specific device request
                    if (deviceId == initialDeviceId && !hasCentered) {
                        val lat = (data["latitude"] as? Number)?.toDouble() ?: 0.0
                        val lon = (data["longitude"] as? Number)?.toDouble() ?: 0.0
                        if (lat != 0.0 && lon != 0.0) {
                            mapView?.controller?.setCenter(GeoPoint(lat, lon))
                            hasCentered = true
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("MapScreen", "Database error: ${error.message}")
            }
        }
        liveRef.addValueEventListener(rtdbListener)
        val fJob = scope.launch {
            fenceRepository.getAllFences().onSuccess {
                fences.clear()
                fences.addAll(it)
            }
        }
        onDispose {
            dListener.remove()
            liveRef.removeEventListener(rtdbListener)
            fJob.cancel()
        }
    }
    // Reactive Marker Management
    LaunchedEffect(ownedDeviceIds.size, liveLocations.size) {
        val mv = mapView ?: return@LaunchedEffect

        // Remove markers for devices no longer owned
        val currentIds = markers.keys.toList()
        currentIds.forEach { id ->
            if (!ownedDeviceIds.contains(id)) {
                markers[id]?.let { mv.overlays.remove(it) }
                markers.remove(id)
            }
        }
        // Add/Update markers for devices
        ownedDeviceIds.forEach { id ->
            // If we are in a device-specific map, only show that device
            if (initialDeviceId != null && id != initialDeviceId) return@forEach
            
            val data = liveLocations[id] ?: return@forEach
            val status = data["status"] as? String ?: "offline"
            
            // Filter Logic
            val isLive = status == "online"
            val isOffline = status == "offline"
            val isNotTracking = (data["trackingMode"] as? String) == "off"
            
            if (isLive && !selectedFilters.contains("Live")) return@forEach
            if (isOffline && !selectedFilters.contains("Offline")) return@forEach
            if (isNotTracking && !selectedFilters.contains("Not Tracking")) return@forEach

            val lat = (data["latitude"] as? Number)?.toDouble() ?: 0.0
            val lon = (data["longitude"] as? Number)?.toDouble() ?: 0.0

            if (lat != 0.0 && lon != 0.0) {
                val marker = markers[id] ?: Marker(mv).apply {
                    title = ownedDevices.find { it.deviceId == id }?.deviceName ?: id
                    markers[id] = this
                    mv.overlays.add(this)
                }
                marker.position = GeoPoint(lat, lon)
            }
        }
        mv.invalidate()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    // Jump to Device Effect
    LaunchedEffect(selectedDeviceId) {
        val id = selectedDeviceId ?: return@LaunchedEffect
        val data = liveLocations[id]

        if (data == null) {
            snackbarHostState.showSnackbar("Device location unavailable (Offline)")
        } else {
            val lat = (data["latitude"] as? Number)?.toDouble() ?: 0.0
            val lon = (data["longitude"] as? Number)?.toDouble() ?: 0.0
            if (lat != 0.0 && lon != 0.0) {
                mapView?.controller?.animateTo(GeoPoint(lat, lon))
            } else {
                snackbarHostState.showSnackbar("GPS coordinates unknown for this device")
            }
        }
        selectedDeviceId = null
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (draggingFence == null) {
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
                            val lastLoc = if (initialDeviceId != null) liveLocations[initialDeviceId] else liveLocations.values.firstOrNull()
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

                    // NEW: Info button for specific device
                    if (initialDeviceId != null) {
                        FloatingActionButton(
                            onClick = {
                                ownedDevices.find { it.deviceId == initialDeviceId }?.let {
                                    showDeviceSheet = it
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = "Info")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        val defaultZoom = if (initialDeviceId != null) 20.0 else 14.0
                        controller.setZoom(defaultZoom)
                        mapView = this

                        if (isDarkMode) {
                            // Clean Dark Mode: Greyscale then Invert
                            val matrix = ColorMatrix()
                            matrix.setSaturation(0f) // Remove all colors (eliminates brown)

                            val inverse = ColorMatrix(floatArrayOf(
                                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                            ))
                            matrix.postConcat(inverse)

                            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                            // Set background to match theme to hide "white squares" while loading
                            setBackgroundColor(android.graphics.Color.parseColor("#0A0D12"))
                        }
                        // Click events for selecting fences
                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                return false
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean {
                                return false
                            }
                        })
                        // Use overlayManager to ensure order
                        overlayManager.add(eventsOverlay)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mv ->
                    mv.setTileSource(TileSourceFactory.MAPNIK)
                    if (isDarkMode) {
                        val matrix = ColorMatrix()
                        matrix.setSaturation(0f)
                        val inverse = ColorMatrix(floatArrayOf(
                            -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                            0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                            0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                        ))
                        matrix.postConcat(inverse)

                        mv.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                        mv.setBackgroundColor(android.graphics.Color.parseColor("#0A0D12"))
                    } else {
                        mv.overlayManager.tilesOverlay.setColorFilter(null)
                        mv.setBackgroundColor(android.graphics.Color.WHITE)
                    }
                    
                    // Clear and Re-add Overlays in correct order
                    mv.overlays.clear()
                    
                    // 1. Events Overlay first (lowest priority for touches)
                    mv.overlays.add(eventsOverlay)

                    // 2. Fences
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

                    // 3. Device Markers
                    markers.values.forEach { marker ->
                        // The marker position is already updated in the LaunchedEffect
                        mv.overlays.add(marker)
                    }

                    // 4. Dragging Fence (Top priority)
                    draggingFence?.let { fence ->
                        val draggingPolygon = Polygon(mv)
                        draggingPolygon.points = Polygon.pointsAsCircle(GeoPoint(fence.centerLat, fence.centerLng), fence.radiusMeters)
                        draggingPolygon.fillPaint.color = 0x44FF5252.toInt()
                        draggingPolygon.outlinePaint.color = 0xFFFF5252.toInt()
                        draggingPolygon.outlinePaint.strokeWidth = 3f
                        mv.overlays.add(draggingPolygon)

                        val draggingMarker = Marker(mv)
                        draggingMarker.position = GeoPoint(fence.centerLat, fence.centerLng)
                        draggingMarker.relatedObject = "dragging"
                        draggingMarker.title = "Drag to Place"
                        draggingMarker.isDraggable = true
                        draggingMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        
                        draggingMarker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) {
                                draggingPolygon.points = Polygon.pointsAsCircle(marker.position, fence.radiusMeters)
                                mv.invalidate()
                            }
                            override fun onMarkerDragEnd(marker: Marker) {
                                draggingFence = draggingFence?.copy(
                                    centerLat = marker.position.latitude,
                                    centerLng = marker.position.longitude
                                )
                                draggingPolygon.points = Polygon.pointsAsCircle(marker.position, fence.radiusMeters)
                                mv.invalidate()
                            }
                            override fun onMarkerDragStart(marker: Marker) {
                                marker.closeInfoWindow()
                            }
                        })
                        mv.overlays.add(draggingMarker)
                    }

                    mv.invalidate()
                },
                onRelease = { mv ->
                    mv.onDetach()
                    mapView = null
                }
            )
            // Filter Chips Overlay
            if (initialDeviceId == null || selectedFilters.contains("Fences")) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                ) {
                    if (initialDeviceId == null) {
                        Row(
                            modifier = Modifier
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
                                    label = { Text(filter) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        // Device-specific view: just Fences
                        FilterChip(
                            selected = selectedFilters.contains("Fences"),
                            onClick = {
                                if (selectedFilters.contains("Fences")) selectedFilters.remove("Fences")
                                else selectedFilters.add("Fences")
                            },
                            label = { Text("Fences") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // Jump to Device Dropdown
                    Box {
                        Button(
                            onClick = { showDeviceMenu = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            elevation = ButtonDefaults.buttonElevation(4.dp),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Jump to Device", style = MaterialTheme.typography.labelLarge)
                        }
                        DropdownMenu(
                            expanded = showDeviceMenu,
                            onDismissRequest = { showDeviceMenu = false }
                        ) {
                            if (ownedDevices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No devices found") },
                                    onClick = { showDeviceMenu = false }
                                )
                            } else {
                                ownedDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier
                                                        .size(8.dp)
                                                        .background(
                                                            if (device.status == "online") Color(0xFF4CAF50) else Color.Gray,
                                                            androidx.compose.foundation.shape.CircleShape
                                                        )
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(device.deviceName)
                                            }
                                        },
                                        onClick = {
                                            showDeviceMenu = false
                                            selectedDeviceId = device.deviceId
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (draggingFence != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .padding(bottom = 32.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Positioning Fence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            draggingFence?.name ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Drag the red marker on the map to place it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { draggingFence = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        fenceRepository.saveFence(draggingFence!!)
                                        draggingFence = null
                                        fenceRepository.getAllFences().onSuccess {
                                            fences.clear()
                                            fences.addAll(it)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Confirm Placement")
                            }
                        }
                    }
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

    // Device Info Bottom Sheet
    if (showDeviceSheet != null) {
        DeviceSettingsSheet(
            device = showDeviceSheet!!,
            onDismiss = { showDeviceSheet = null },
            deviceRepository = deviceRepository
        )
    }

    // Geofence Editing Sheet
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
            onMove = { fenceToMove ->
                draggingFence = fenceToMove
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