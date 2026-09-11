package com.beacon.admin.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceEventObserver @Inject constructor() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var geofenceListenerRegistration: ListenerRegistration? = null

    fun startObserving() {
        // 1. Authentication Guarding
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot start geofence observation: No active user authenticated.")
            return
        }

        // Avoid duplicate listeners
        if (geofenceListenerRegistration != null) {
            Log.d(TAG, "Geofence event observation is already active.")
            return
        }

        Log.d(TAG, "Starting geofence event observation for user: ${currentUser.uid}")

        // 2. Defensive Error Handling & Listener Attachment
        geofenceListenerRegistration = db.collection("geofence_events")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore error during geofence event observation", error)

                    // Zombie Stream Prevention on terminal errors (e.g., PERMISSION_DENIED)
                    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                        error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                    ) {
                        Log.e(TAG, "Terminal security exception encountered. Removing geofence listener.")
                        stopObserving()
                    }
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    Log.i(TAG, "Geofence events updated: ${snapshots.size()} documents received.")
                    // Process incoming geofence event snapshots
                }
            }
    }

    fun stopObserving() {
        geofenceListenerRegistration?.remove()
        geofenceListenerRegistration = null
        Log.d(TAG, "Geofence event observer listener safely detached.")
    }

    companion object {
        private const val TAG = "GeofenceEventObserver"
    }
}
