package com.beacon.admin.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceEventObserver @Inject constructor() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var geofenceListenerRegistration: ListenerRegistration? = null
    private var unregisterCleanup: (() -> Unit)? = null

    fun startObserving() {
        // 1. Authentication Guarding
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot start geofence observation: No active user authenticated.")
            return
        }

        Log.d(TAG, "Geofence events are handled by AdminSosService")
    }

    fun stopObserving() {
        geofenceListenerRegistration?.remove()
        geofenceListenerRegistration = null
        unregisterCleanup?.invoke()
        unregisterCleanup = null
        Log.d(TAG, "Geofence event observer listener safely detached.")
    }

    companion object {
        private const val TAG = "GeofenceEventObserver"
    }
}
