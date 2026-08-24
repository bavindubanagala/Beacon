package com.beacon.shared.models

data class Device(
    val deviceId: String = "",
    val deviceName: String = "",
    val batteryLevel: Int = 0,
    val status: String = "offline",
    val groupId: String? = null,
    val is_paired: Boolean = false,
    val ownerId: String = "",
    val trackerAuthUid: String? = null,
    val trackingMode: String = "interval",
    val intervalSeconds: Int = 900, // Default 15 mins
    val autoRevertSeconds: Int = 1800, // Default 30 mins, 0 = off
    val isEmergencyMode: Boolean = false,
    val batterySavingEnabled: Boolean = true,
    val stationaryIntervalMinutes: Int = 45,
    val commandMode: String? = null,
    val commandDurationMinutes: Int? = null, // Deprecated, but keep for now
    val commandTimestamp: Long? = null,
    val alertThresholds: AlertThresholds = AlertThresholds(),
    val alertsEnabled: Boolean = true,
    val sosFallbackPhone: String = "",
    val customColor: Int? = null,
    val deviceModel: String = "",
    val pairingDate: Long? = null
)

data class AlertThresholds(
    val lowBatteryPercent: Int = 15,
    val offlineThresholdMinutes: Int = 10,
    val speedLimitKmH: Int = 0, // 0 = disabled
    val geofences: List<GeofenceZone> = emptyList()
)

data class GeofenceZone(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 100.0
)
