package com.beacon.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.geofence.TrackerGeofenceManager
import com.beacon.tracker.geofence.TrackerGeofenceManager.StoredGeofence
import com.beacon.tracker.sync.LocationSyncManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var locationSyncManager: LocationSyncManager

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GeofenceReceiver", "Geofencing error: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        val isEnter = (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)
        val triggerLoc = geofencingEvent.triggeringLocation

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                withTimeoutOrNull(8000L) {
                    val appContext = context.applicationContext
                    val deviceId = DeviceAuthManager(appContext).getDeviceId()
                    if (deviceId.isBlank()) return@withTimeoutOrNull

                    val storedZones = TrackerGeofenceManager(appContext).loadStored()

                    for (geofence in triggeringGeofences) {
                        val zone = storedZones.find { it.id == geofence.requestId } ?: continue

                        // Active check
                        val calendar = Calendar.getInstance()
                        val todayNumber = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                        if (zone.activeUntil > 0L && System.currentTimeMillis() > zone.activeUntil) continue
                        if (zone.activeDaysOfWeek.isNotEmpty() && todayNumber !in zone.activeDaysOfWeek) continue

                        val lat = triggerLoc?.latitude ?: zone.lat
                        val lng = triggerLoc?.longitude ?: zone.lng

                        // Event reporting
                        val nowMs = System.currentTimeMillis()
                        if (((isEnter && zone.alertOnEnter) || (!isEnter && zone.alertOnExit)) &&
                            TrackerGeofenceManager(appContext).shouldSendEvent(zone.id, zone.alertFrequency, nowMs)
                        ) {
                            try {
                                locationSyncManager.processGeofenceEvent(
                                    deviceId = deviceId,
                                    geofenceId = zone.id,
                                    geofenceName = zone.name,
                                    eventType = if (isEnter) "ENTER" else "EXIT",
                                    latitude = lat,
                                    longitude = lng,
                                    timestamp = nowMs
                                )
                            } catch (e: Exception) {
                                Log.e("GeofenceReceiver", "Failed to process geofence event", e)
                            }
                        }

                        // Arrival behavior
                        try {
                            if (isEnter && zone.arrivalLiveEnabled) {
                                switchToLive(appContext, deviceId, zone)
                            } else if (!isEnter && zone.arrivalLiveEnabled && zone.revertOnExit) {
                                revertAfterExit(appContext, deviceId, zone)
                            }
                        } catch (e: Exception) {
                            Log.e("GeofenceReceiver", "Failed to handle arrival/exit mode change for zone ${zone.id}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GeofenceReceiver", "Error in geofence broadcast receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun switchToLive(context: Context, deviceId: String, zone: StoredGeofence) {
        val firestore = FirebaseFirestore.getInstance()
        val deviceRef = firestore.collection("devices").document(deviceId)
        val snapshot = deviceRef.get().await()

        val rawMode = snapshot.getString("trackingMode")?.trim()?.uppercase() ?: ""
        val currentMode = when (rawMode) {
            "INTERVAL", "SCHEDULED" -> "SCHEDULED"
            "ONLINE" -> "ONLINE"
            "LIVE" -> "LIVE"
            else -> "SCHEDULED"
        }

        if (currentMode == "LIVE") {
            Log.d("GeofenceReceiver", "Device is already LIVE, ignoring geofence arrival switch")
            return
        }

        val now = System.currentTimeMillis()
        val batch = firestore.batch()
        batch.update(
            deviceRef,
            mapOf(
                "trackingMode" to "LIVE",
                "liveIntervalMillis" to zone.arrivalLiveIntervalMillis,
                "liveRevertAfterMillis" to 0L,
                "revertToMode" to currentMode,
                "trackingChangedAt" to now
            )
        )
        batch.set(
            deviceRef.collection("mode_history").document(),
            mapOf(
                "mode" to "LIVE",
                "intervalMillis" to zone.arrivalLiveIntervalMillis,
                "changedBy" to "Geofence",
                "timestamp" to now
            )
        )
        batch.commit().await()

        val prefs = context.getSharedPreferences(TrackerGeofenceManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("live_by_geofence_id", zone.id)
            .putLong("live_by_geofence_changed_at", now)
            .apply()
    }

    private suspend fun revertAfterExit(context: Context, deviceId: String, zone: StoredGeofence) {
        val prefs = context.getSharedPreferences(TrackerGeofenceManager.PREFS_NAME, Context.MODE_PRIVATE)
        val storedId = prefs.getString("live_by_geofence_id", null)
        val storedChangedAt = prefs.getLong("live_by_geofence_changed_at", 0L)

        if (storedId != zone.id) return

        val firestore = FirebaseFirestore.getInstance()
        val deviceRef = firestore.collection("devices").document(deviceId)
        val snapshot = deviceRef.get().await()

        val currentMode = snapshot.getString("trackingMode")?.trim()?.uppercase() ?: ""
        val changedAt = snapshot.getLong("trackingChangedAt") ?: 0L

        if (currentMode != "LIVE" || changedAt != storedChangedAt) {
            prefs.edit()
                .remove("live_by_geofence_id")
                .remove("live_by_geofence_changed_at")
                .apply()
            return
        }

        val revertTo = if (snapshot.getString("revertToMode")?.uppercase() == "ONLINE") "ONLINE" else "SCHEDULED"
        val now = System.currentTimeMillis()
        val intervalMillis = if (revertTo == "SCHEDULED") {
            snapshot.getLong("scheduledIntervalMillis") ?: 900_000L
        } else {
            0L
        }

        val batch = firestore.batch()
        batch.update(
            deviceRef,
            mapOf(
                "trackingMode" to revertTo,
                "trackingChangedAt" to now,
                "revertToMode" to ""
            )
        )
        batch.set(
            deviceRef.collection("mode_history").document(),
            mapOf(
                "mode" to revertTo,
                "intervalMillis" to intervalMillis,
                "changedBy" to "Geofence",
                "timestamp" to now
            )
        )
        batch.commit().await()

    prefs.edit()
            .remove("live_by_geofence_id")
            .remove("live_by_geofence_changed_at")
            .apply()
    }
}
