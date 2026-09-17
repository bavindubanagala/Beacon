package com.beacon.admin.ui.components

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.MotionEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.beacon.admin.ui.theme.BeaconCyan
import com.beacon.admin.ui.theme.GlassSurface
import com.beacon.admin.ui.theme.GlassSurfaceBorder
import com.beacon.admin.ui.theme.TextPrimary
import com.beacon.admin.ui.utils.getStatusUiConfig
import com.beacon.shared.models.DeviceStatus
import com.beacon.shared.models.GeofenceType
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

data class MapMarkerState(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val status: DeviceStatus,
    val accuracy: Float = 0f
)

data class GeofenceZoneState(
    val id: String,
    val name: String,
    val type: GeofenceType,
    val centerLat: Double? = null,
    val centerLng: Double? = null,
    val radiusMeters: Double? = null,
    val pointALat: Double? = null,
    val pointALng: Double? = null,
    val pointBLat: Double? = null,
    val pointBLng: Double? = null
)

class PulseOverlay(
    private val center: GeoPoint,
    private val color: androidx.compose.ui.graphics.Color,
    private val progress: Float
) : Overlay() {
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val projection = mapView.projection
        val screenPoint = Point()
        projection.toPixels(center, screenPoint)

        val maxRadius = 100f // Pixels
        val currentRadius = maxRadius * progress
        val alpha = (255 * (1f - progress)).toInt()

        paint.color = color.toArgb()
        paint.alpha = alpha
        paint.strokeWidth = 4f

        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), currentRadius, paint)
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun BeaconMapComponent(
    modifier: Modifier = Modifier,
    initialLat: Double = 0.0,
    initialLng: Double = 0.0,
    initialZoom: Double = 12.0,
    mapStyle: String = "Standard",
    markers: List<MapMarkerState> = emptyList(),
    geofences: List<GeofenceZoneState> = emptyList(),
    historicalPath: List<GeoPoint> = emptyList(),
    playbackMarker: GeoPoint? = null,
    isCreationMode: Boolean = false,
    draftGeofenceType: GeofenceType? = null,
    draftCenter: GeoPoint? = null,
    draftRadiusMeters: Double = 100.0,
    draftPointA: GeoPoint? = null,
    draftPointB: GeoPoint? = null,
    onDraftCenterMoved: (GeoPoint) -> Unit = {},
    onDraftPointAMoved: (GeoPoint) -> Unit = {},
    onDraftPointBMoved: (GeoPoint) -> Unit = {},
    onRadiusChanged: (Double) -> Unit = {},
    onMarkerClick: (String) -> Unit = {},
    centerOn: GeoPoint? = null,
    targetZoom: Double? = null,
    onRecenter: (() -> Unit)? = null
) {
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "MapPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseProgress"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clipToBounds()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().userAgentValue = context.packageName
                val mapView = MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(initialZoom)
                    controller.setCenter(GeoPoint(initialLat, initialLng))
                    
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                }
                mapViewRef = mapView
                mapView
            },
            update = { mapView ->
                // Ensure zoom is synchronized if initialZoom changes
                if (mapView.zoomLevelDouble != initialZoom && centerOn == null) {
                    mapView.controller.setZoom(initialZoom)
                }

                // Update tile source based on mapStyle
                val tileSource = when (mapStyle) {
                    "Satellite" -> TileSourceFactory.USGS_SAT
                    "Topographic" -> TileSourceFactory.USGS_TOPO
                    else -> TileSourceFactory.MAPNIK
                }
                if (mapView.tileProvider.tileSource != tileSource) {
                    mapView.setTileSource(tileSource)
                }

                mapView.overlays.clear()

                // 1. Add Historical Path Polyline
                if (historicalPath.isNotEmpty()) {
                    val pathLine = Polyline(mapView)
                    pathLine.setPoints(historicalPath)
                    pathLine.outlinePaint.color = 0xAA00BCD4.toInt() // Cyan semi-transparent
                    pathLine.outlinePaint.strokeWidth = 5f
                    mapView.overlays.add(pathLine)
                }

                // 2. Add Existing Geofence Overlays
                geofences.forEach { fence ->
                    when (fence.type) {
                        GeofenceType.RADIAL -> {
                            val lat = fence.centerLat ?: return@forEach
                            val lng = fence.centerLng ?: return@forEach
                            val radius = fence.radiusMeters ?: return@forEach

                            val circle = Polygon(mapView)
                            circle.points = Polygon.pointsAsCircle(GeoPoint(lat, lng), radius)
                            circle.fillPaint.color = 0x3300BCD4 // Light Cyan semi-transparent
                            circle.outlinePaint.color = 0xFF00BCD4.toInt()
                            circle.outlinePaint.strokeWidth = 2f
                            circle.title = fence.name
                            mapView.overlays.add(circle)
                        }
                        GeofenceType.TRIPWIRE -> {
                            val aLat = fence.pointALat ?: return@forEach
                            val aLng = fence.pointALng ?: return@forEach
                            val bLat = fence.pointBLat ?: return@forEach
                            val bLng = fence.pointBLng ?: return@forEach

                            val line = Polyline(mapView)
                            line.addPoint(GeoPoint(aLat, aLng))
                            line.addPoint(GeoPoint(bLat, bLng))
                            line.outlinePaint.color = 0xFFFF5722.toInt() // Deep Orange
                            line.outlinePaint.strokeWidth = 6f
                            line.title = fence.name
                            mapView.overlays.add(line)
                        }
                    }
                }

                // 2. Add Creation Mode Overlays
                if (isCreationMode && draftGeofenceType != null) {
                    when (draftGeofenceType) {
                        GeofenceType.RADIAL -> {
                            draftCenter?.let { center ->
                                val cyanArgb = BeaconCyan.toArgb()
                                // Circle Overlay
                                val circle = Polygon(mapView)
                                circle.points = Polygon.pointsAsCircle(center, draftRadiusMeters)
                                circle.fillPaint.color = (0x44 shl 24) or (cyanArgb and 0x00FFFFFF)
                                circle.outlinePaint.color = cyanArgb
                                circle.outlinePaint.strokeWidth = 3f
                                mapView.overlays.add(circle)

                                // Draggable Center Marker
                                val marker = Marker(mapView).apply {
                                    position = center
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    isDraggable = true
                                    title = "Drag to Place Center"
                                    
                                    val shape = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        setColor(cyanArgb)
                                        setStroke(4, android.graphics.Color.WHITE)
                                        setSize(64, 64)
                                    }
                                    icon = shape
                                    
                                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                        override fun onMarkerDrag(m: Marker) {
                                            circle.points = Polygon.pointsAsCircle(m.position, draftRadiusMeters)
                                            mapView.invalidate()
                                        }
                                        override fun onMarkerDragEnd(m: Marker) {
                                            onDraftCenterMoved(m.position)
                                        }
                                        override fun onMarkerDragStart(m: Marker) {}
                                    })
                                }
                                mapView.overlays.add(marker)
                            }
                        }
                        GeofenceType.TRIPWIRE -> {
                            if (draftPointA != null && draftPointB != null) {
                                // Polyline Overlay
                                val line = Polyline(mapView)
                                line.setPoints(listOf(draftPointA, draftPointB))
                                line.outlinePaint.color = 0xFFFF5722.toInt()
                                line.outlinePaint.strokeWidth = 8f
                                mapView.overlays.add(line)

                                var markerA: Marker? = null
                                var markerB: Marker? = null

                                // Endpoint A
                                markerA = Marker(mapView).apply {
                                    position = draftPointA
                                    isDraggable = true
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = "Endpoint A"
                                    
                                    val shape = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        setColor(0xFFFF5722.toInt())
                                        setStroke(4, android.graphics.Color.WHITE)
                                        setSize(50, 50)
                                    }
                                    icon = shape

                                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                        override fun onMarkerDrag(m: Marker) {
                                            markerB?.let { b ->
                                                line.setPoints(listOf(m.position, b.position))
                                            }
                                            mapView.invalidate()
                                        }
                                        override fun onMarkerDragEnd(m: Marker) {
                                            onDraftPointAMoved(m.position)
                                        }
                                        override fun onMarkerDragStart(m: Marker) {}
                                    })
                                }

                                // Endpoint B
                                markerB = Marker(mapView).apply {
                                    position = draftPointB
                                    isDraggable = true
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = "Endpoint B"
                                    
                                    val shape = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        setColor(0xFFFF5722.toInt())
                                        setStroke(4, android.graphics.Color.WHITE)
                                        setSize(50, 50)
                                    }
                                    icon = shape

                                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                        override fun onMarkerDrag(m: Marker) {
                                            markerA?.let { a ->
                                                line.setPoints(listOf(a.position, m.position))
                                            }
                                            mapView.invalidate()
                                        }
                                        override fun onMarkerDragEnd(m: Marker) {
                                            onDraftPointBMoved(m.position)
                                        }
                                        override fun onMarkerDragStart(m: Marker) {}
                                    })
                                }
                                mapView.overlays.add(line)
                                mapView.overlays.add(markerA)
                                mapView.overlays.add(markerB)
                            }
                        }
                    }
                }

                // 3. Add Pulse Overlays (so they are under markers)
                markers.filterNot { it.latitude == 0.0 && it.longitude == 0.0 }.forEach { markerData ->
                    if (markerData.status == DeviceStatus.GREEN_LIVE || markerData.status == DeviceStatus.BLUE_INTERVAL) {
                        val (statusColor, _) = getStatusUiConfig(markerData.status)
                        val pulseOverlay = PulseOverlay(
                            GeoPoint(markerData.latitude, markerData.longitude),
                            statusColor,
                            pulseProgress
                        )
                        mapView.overlays.add(pulseOverlay)
                    }
                }

                // 5. Add Device Markers & Accuracy Circles
                markers.filterNot { it.latitude == 0.0 && it.longitude == 0.0 }.forEach { markerData ->
                    // Draw Accuracy Circle
                    if (markerData.accuracy > 0) {
                        val circle = Polygon(mapView)
                        circle.points = Polygon.pointsAsCircle(GeoPoint(markerData.latitude, markerData.longitude), markerData.accuracy.toDouble())
                        val (color, _) = getStatusUiConfig(markerData.status)
                        circle.fillPaint.color = (0x33 shl 24) or (color.toArgb() and 0x00FFFFFF)
                        circle.outlinePaint.color = (0x88 shl 24) or (color.toArgb() and 0x00FFFFFF)
                        circle.outlinePaint.strokeWidth = 2f
                        mapView.overlays.add(circle)
                    }

                    val osmMarker = Marker(mapView).apply {
                        position = GeoPoint(markerData.latitude, markerData.longitude)
                        title = markerData.title
                        val (color, label) = getStatusUiConfig(markerData.status)
                        snippet = label
                        
                        val dotRadius = 24
                        val shape = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(color.toArgb())
                            setStroke(4, android.graphics.Color.WHITE)
                            setSize(dotRadius * 2, dotRadius * 2)
                        }
                        icon = shape
                        
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { m, _ ->
                            m.showInfoWindow()
                            onMarkerClick(markerData.id)
                            true
                        }

                        if (markerData.status == DeviceStatus.RED_OFFLINE || markerData.status == DeviceStatus.GRAY_UNPAIRED) {
                            alpha = 0.6f
                        }
                    }
                    mapView.overlays.add(osmMarker)
                }

                // 6. Add Playback Marker
                playbackMarker?.let { pos ->
                    val marker = Marker(mapView).apply {
                        position = pos
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Playback Position"
                        val shape = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(android.graphics.Color.YELLOW)
                            setStroke(6, android.graphics.Color.BLACK)
                            setSize(48, 48)
                        }
                        icon = shape
                    }
                    mapView.overlays.add(marker)
                }

                centerOn?.let {
                    if (targetZoom != null) {
                        mapView.controller.animateTo(it, targetZoom, 1000L)
                    } else {
                        mapView.controller.animateTo(it)
                    }
                }

                mapView.invalidate()
            }
        )

        if (onRecenter != null && !isCreationMode) {
            Surface(
                onClick = {
                    centerOn?.let {
                        mapViewRef?.controller?.animateTo(it, 18.0, 1000L)
                    }
                    onRecenter()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = GlassSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder)
            ) {
                Box(modifier = Modifier.padding(10.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.GpsFixed,
                        contentDescription = "Recenter",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
