package com.beacon.admin.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.beacon.admin.MainActivity
import com.beacon.shared.models.GeofenceEvent
import com.beacon.shared.models.GeofenceEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "geofence_alerts"
        private const val CHANNEL_NAME = "Geofence Alerts"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for geofence entry, exit and crossings"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun sendGeofenceAlert(event: GeofenceEvent, deviceName: String) {
        val title = when (event.eventType) {
            GeofenceEventType.ENTER -> "ENTERED Zone"
            GeofenceEventType.EXIT -> "EXITED Zone"
            GeofenceEventType.CROSS_A_TO_B -> "CROSSED Tripwire"
            GeofenceEventType.CROSS_B_TO_A -> "CROSSED Tripwire"
        }

        val content = "$deviceName triggered ${event.geofenceName}"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("deviceId", event.deviceId)
            putExtra("geofenceId", event.geofenceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission missing for POST_NOTIFICATIONS on Android 13+
        }
    }
}
