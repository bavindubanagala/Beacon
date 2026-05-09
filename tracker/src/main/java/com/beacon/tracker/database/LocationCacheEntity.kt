package com.beacon.tracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "location_cache")
data class LocationCacheEntity(
    @PrimaryKey
    val timestamp: Long,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String,
    val speed: Float,
    val heading: Float,
    val batteryLevel: Int,
    val signalStrength: Int,
    val deviceMotionStatus: String,
    val syncedToFirebase: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
