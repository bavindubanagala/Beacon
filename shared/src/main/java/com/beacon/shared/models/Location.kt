package com.beacon.shared.models

data class Location(
    val deviceId: String = "",
    val timestamp: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val provider: String = "gps",
    val speed: Float = 0f,
    val heading: Float = 0f,
    val batteryLevel: Int = 0,
    val signalStrength: Int = 0,
    val deviceMotionStatus: String = "idle"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "device_id" to deviceId,
        "timestamp" to timestamp,
        "latitude" to latitude,
        "longitude" to longitude,
        "accuracy" to accuracy,
        "provider" to provider,
        "speed" to speed,
        "heading" to heading,
        "battery_level" to batteryLevel,
        "signal_strength" to signalStrength,
        "device_motion_status" to deviceMotionStatus
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Location {
            return Location(
                deviceId = map["device_id"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f,
                provider = map["provider"] as? String ?: "gps",
                speed = (map["speed"] as? Number)?.toFloat() ?: 0f,
                heading = (map["heading"] as? Number)?.toFloat() ?: 0f,
                batteryLevel = (map["battery_level"] as? Number)?.toInt() ?: 0,
                signalStrength = (map["signal_strength"] as? Number)?.toInt() ?: 0,
                deviceMotionStatus = map["device_motion_status"] as? String ?: "idle"
            )
        }
    }
}
