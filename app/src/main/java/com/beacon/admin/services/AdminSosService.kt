package com.beacon.admin.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.beacon.admin.data.auth.AuthSessionCleanupRegistry

class AdminSosService : Service() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var sosListenerRegistration: ListenerRegistration? = null
    private var unregisterCleanup: (() -> Unit)? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        // 1. Authentication Guarding
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot start SOS monitoring: No active user authenticated.")
            stopSelf()
            return
        }

        // Avoid duplicate listeners
        if (sosListenerRegistration != null) {
            Log.d(TAG, "SOS monitoring is already active.")
            return
        }

        Log.d(TAG, "Starting SOS monitoring for authenticated user: ${currentUser.uid}")

        // 2. Defensive Error Handling & Listener Attachment
        sosListenerRegistration = db.collection("sos_alerts")
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore error during SOS monitoring", error)
                    
                    // Zombie Stream Prevention on terminal errors (e.g., PERMISSION_DENIED)
                    if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                        error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                    ) {
                        Log.e(TAG, "Terminal security exception encountered. Removing listener.")
                        stopMonitoring()
                    }
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    Log.i(TAG, "Active SOS alerts detected: ${snapshots.size()}")
                    // Handle incoming SOS events
                }
            }
        unregisterCleanup = AuthSessionCleanupRegistry.register { stopMonitoring() }
    }

    private fun stopMonitoring() {
        sosListenerRegistration?.remove()
        sosListenerRegistration = null
        unregisterCleanup?.invoke()
        unregisterCleanup = null
        Log.d(TAG, "SOS monitoring listener removed.")
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AdminSosService"
    }
}
