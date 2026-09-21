package com.beacon.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.beacon.tracker.sync.LocationSyncManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
            val transitionTypeStr = if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) "ENTER" else "EXIT"
            val timestamp = System.currentTimeMillis()
            val userId = "current_user_id" // Handled by Session/Auth Context

            val pendingResult = goAsync()
            receiverScope.launch {
                try {
                    for (geofence in triggeringGeofences) {
                        locationSyncManager.processGeofenceEvent(
                            userId = userId,
                            geofenceId = geofence.requestId,
                            transitionType = transitionTypeStr,
                            timestamp = timestamp
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
