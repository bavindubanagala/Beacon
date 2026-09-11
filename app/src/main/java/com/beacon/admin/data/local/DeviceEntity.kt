package com.beacon.admin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trackingStatus: String,
    val batteryPercentage: Int,
    val isPowerSaveMode: Boolean,
    val lastPing: String,
    val activePreset: String
)
