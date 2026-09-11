package com.beacon.tracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beacon.shared.models.GeofenceEventType
import java.util.UUID

@Entity(tableName = "offline_geofence_events")
data class OfflineGeofenceEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val geofenceId: String,
    val deviceId: String,
    val geofenceName: String,
    val eventType: GeofenceEventType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
