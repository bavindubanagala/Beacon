package com.beacon.shared.models

enum class GeofenceType {
    RADIAL,
    TRIPWIRE
}

enum class Directionality {
    A_TO_B,
    B_TO_A,
    BOTH
}

enum class AlertFrequency {
    EVERY_TIME,
    ONCE_EVER,
    ONCE_PER_DAY
}

data class GeofenceZone(
    val id: String = "",
    val name: String = "",
    val type: GeofenceType = GeofenceType.RADIAL,
    
    // Radial fields
    val centerLat: Double? = null,
    val centerLng: Double? = null,
    val radiusMeters: Double? = null,
    
    // Tripwire fields
    val pointALat: Double? = null,
    val pointALng: Double? = null,
    val pointBLat: Double? = null,
    val pointBLng: Double? = null,
    val directionality: Directionality? = null,
    
    // Assignments
    val assignedDeviceIds: List<String> = emptyList(),
    val assignedGroupIds: List<String> = emptyList(),
    
    // Alert settings
    val alertOnEnter: Boolean = true,
    val alertOnExit: Boolean = true,
    val alertFrequency: AlertFrequency = AlertFrequency.EVERY_TIME,
    
    // Time bounds
    val activeFrom: Long? = null,
    val activeUntil: Long? = null,
    val activeDaysOfWeek: List<Int>? = null,
    
    val createdAt: Long = System.currentTimeMillis()
)
