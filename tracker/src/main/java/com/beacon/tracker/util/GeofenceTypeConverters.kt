package com.beacon.tracker.util

import androidx.room.TypeConverter
import com.beacon.shared.models.GeofenceEventType

class GeofenceTypeConverters {
    @TypeConverter
    fun fromGeofenceEventType(value: GeofenceEventType): String {
        return value.name
    }

    @TypeConverter
    fun toGeofenceEventType(value: String): GeofenceEventType {
        return GeofenceEventType.valueOf(value)
    }
}
