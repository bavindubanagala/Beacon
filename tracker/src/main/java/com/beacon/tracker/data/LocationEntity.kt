package com.beacon.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "location_logs")
data class LocationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val userId: String = "", // Added for compatibility with new SyncManager
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 0f,
    val batteryLevel: Int = 0,
    val signalStrength: Int = 0,
    val deviceMotionStatus: String = "moving",
    val timestamp: Long,
    val isSynced: Boolean = false
)
