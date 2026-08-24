package com.beacon.admin.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beacon.admin.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class AdminSosService : Service() {
    private var listener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Dedicated scope for background operations, independent of UI lifecycle
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val notificationId = 888
    private val channelId = "admin_sos_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, createNotification("Monitoring for SOS alerts..."))
        startMonitoring()
    }

    private fun startMonitoring() {
        val user = auth.currentUser ?: return
        
        // Listener attached to Firestore runs in background and is scoped to service
        listener = firestore.collection("devices")
            .whereEqualTo("ownerId", user.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("AdminSosService", "Listen failed", e)
                    return@addSnapshotListener
                }
                
                val anyEmergency = snapshot?.documents?.any { 
                    it.getBoolean("isEmergencyMode") == true || it.getBoolean("is_emergency_mode") == true
                } ?: false
                
                if (anyEmergency) {
                    triggerEmergencyNotification()
                    
                    // Decoupled notification of MainActivity
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                }
            }
    }

    private fun triggerEmergencyNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS EMERGENCY")
            .setContentText("A device is in distress! Tap to open.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .apply {
                if (canShowFullScreenIntent()) {
                    setFullScreenIntent(pendingIntent, true)
                }
            }
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(android.graphics.Color.RED)
            .build()

        notificationManager.notify(notificationId + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Admin SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors for incoming SOS signals"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Beacon Hub SOS Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        listener?.remove()
        serviceScope.cancel() // Ensure all background tasks are cancelled
        super.onDestroy()
    }

    private fun canShowFullScreenIntent(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.canUseFullScreenIntent()
        } else true
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
