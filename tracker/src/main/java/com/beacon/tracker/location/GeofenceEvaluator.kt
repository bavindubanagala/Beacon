package com.beacon.tracker.location

import android.location.Location
import android.util.Log
import com.beacon.shared.models.Directionality
import com.beacon.shared.models.GeofenceEvent
import com.beacon.shared.models.GeofenceEventType
import com.beacon.shared.models.GeofenceType
import com.beacon.shared.models.GeofenceZone
import com.beacon.shared.repository.FirebaseGeofenceRepository
import com.beacon.tracker.db.OfflineBufferDao
import com.beacon.tracker.db.OfflineGeofenceEventEntity
import com.beacon.tracker.sync.OfflineSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceEvaluator @Inject constructor(
    private val repository: FirebaseGeofenceRepository,
    private val bufferDao: OfflineBufferDao,
    private val syncManager: OfflineSyncManager,
    private val scope: CoroutineScope
) {
    private var activeFences = listOf<GeofenceZone>()
    private var lastLocation: Location? = null
    private val radialInsideState = mutableMapOf<String, Boolean>()

    private val TAG = "GeofenceEvaluator"

    fun setFences(fences: List<GeofenceZone>) {
        activeFences = fences
        Log.d(TAG, "Active fences updated: ${fences.size} fences")
    }

    suspend fun processLocationUpdate(newLocation: Location, deviceId: String): Boolean {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        var isNearAnyFence = false

        for (fence in activeFences) {
            // 1. Temporal Check
            val activeFrom = fence.activeFrom
            if (activeFrom != null && now < activeFrom) continue
            
            val activeUntil = fence.activeUntil
            if (activeUntil != null && now > activeUntil) continue
            
            val activeDaysOfWeek = fence.activeDaysOfWeek
            if (activeDaysOfWeek != null && !activeDaysOfWeek.contains(dayOfWeek)) continue

            // 2. Proximity Check (for Adaptive Policy)
            if (!isNearAnyFence) {
                when (fence.type) {
                    GeofenceType.RADIAL -> {
                        val centerLat = fence.centerLat
                        val centerLng = fence.centerLng
                        val radius = fence.radiusMeters
                        if (centerLat != null && centerLng != null && radius != null) {
                            val results = FloatArray(1)
                            Location.distanceBetween(newLocation.latitude, newLocation.longitude, centerLat, centerLng, results)
                            if (results[0] <= radius + 200.0) {
                                isNearAnyFence = true
                            }
                        }
                    }
                    GeofenceType.TRIPWIRE -> {
                        val aLat = fence.pointALat
                        val aLng = fence.pointALng
                        val bLat = fence.pointBLat
                        val bLng = fence.pointBLng
                        if (aLat != null && aLng != null && bLat != null && bLng != null) {
                            val resA = FloatArray(1)
                            val resB = FloatArray(1)
                            Location.distanceBetween(newLocation.latitude, newLocation.longitude, aLat, aLng, resA)
                            Location.distanceBetween(newLocation.latitude, newLocation.longitude, bLat, bLng, resB)
                            if (resA[0] <= 200.0 || resB[0] <= 200.0) {
                                isNearAnyFence = true
                            }
                        }
                    }
                }
            }

            when (fence.type) {
                GeofenceType.RADIAL -> {
                    val centerLat = fence.centerLat ?: continue
                    val centerLng = fence.centerLng ?: continue
                    val radius = fence.radiusMeters?.toFloat() ?: continue

                    val isInside = GeofenceMathEngine.isInsideRadial(
                        newLocation.latitude, newLocation.longitude,
                        centerLat, centerLng, radius
                    )

                    val prevState = radialInsideState[fence.id] ?: false
                    if (isInside != prevState) {
                        radialInsideState[fence.id] = isInside
                        val eventType = if (isInside) GeofenceEventType.ENTER else GeofenceEventType.EXIT
                        
                        // Check if we should alert based on settings
                        val shouldAlert = (eventType == GeofenceEventType.ENTER && fence.alertOnEnter) ||
                                          (eventType == GeofenceEventType.EXIT && fence.alertOnExit)
                        
                        if (shouldAlert) {
                            logEvent(fence, deviceId, eventType, newLocation)
                        }
                    }
                }
                GeofenceType.TRIPWIRE -> {
                    val lastLoc = lastLocation ?: continue
                    val pALat = fence.pointALat ?: continue
                    val pALng = fence.pointALng ?: continue
                    val pBLat = fence.pointBLat ?: continue
                    val pBLng = fence.pointBLng ?: continue

                    val crossingType = GeofenceMathEngine.checkTripwireCrossing(
                        lastLoc.latitude, lastLoc.longitude,
                        newLocation.latitude, newLocation.longitude,
                        pALat, pALng, pBLat, pBLng
                    )

                    if (crossingType != null) {
                        val matchesDirection = when (fence.directionality) {
                            Directionality.A_TO_B -> crossingType == GeofenceEventType.CROSS_A_TO_B
                            Directionality.B_TO_A -> crossingType == GeofenceEventType.CROSS_B_TO_A
                            Directionality.BOTH -> true
                            null -> true
                        }

                        if (matchesDirection) {
                            logEvent(fence, deviceId, crossingType, newLocation)
                        }
                    }
                }
            }
        }
        lastLocation = newLocation
        return isNearAnyFence
    }

    private fun logEvent(
        fence: GeofenceZone,
        deviceId: String,
        type: GeofenceEventType,
        location: Location
    ) {
        val event = GeofenceEvent(
            geofenceId = fence.id,
            geofenceName = fence.name,
            deviceId = deviceId,
            eventType = type,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis()
        )

        scope.launch {
            if (syncManager.isOnline.value) {
                val result = repository.logGeofenceEvent(event)
                if (result.isFailure) {
                    bufferEventLocally(event)
                } else {
                    Log.d(TAG, "Geofence event logged: ${fence.name} - $type")
                }
            } else {
                bufferEventLocally(event)
            }
        }
    }

    private suspend fun bufferEventLocally(event: GeofenceEvent) {
        val entity = OfflineGeofenceEventEntity(
            geofenceId = event.geofenceId,
            deviceId = event.deviceId,
            geofenceName = event.geofenceName,
            eventType = event.eventType,
            latitude = event.latitude,
            longitude = event.longitude,
            timestamp = event.timestamp
        )
        bufferDao.insertGeofenceEvent(entity)
        Log.d(TAG, "Geofence event buffered locally (Offline)")
    }
}
