package com.beacon.tracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_location")
data class CachedLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val deviceId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String,
    val speed: Float,
    val heading: Float,
    val batteryLevel: Int,
    val signalStrength: Int,
    val deviceMotionStatus: String = "idle",
    val synced: Int = 0  // 0 = not synced, 1 = synced
)
