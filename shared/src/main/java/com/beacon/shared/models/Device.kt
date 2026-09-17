package com.beacon.shared.models



data class Device(
    val deviceId: String = "",
    val deviceName: String = "",
    val batteryLevel: Int = 0,
    val status: String = "offline",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Float = 0f,
    val signalStrength: Int = 0,
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
    val pairingDate: Long? = null,
    val accuracy: Float = 0f,
    val lastSeenTimestamp: Long = 0L
) {
    val statusLight: DeviceStatus
        get() {
            if (!is_paired || status.equals("unpaired", ignoreCase = true)) {
                return DeviceStatus.GRAY_UNPAIRED
            }

            val now = System.currentTimeMillis()
            val fifteenMinutesMs = 15 * 60 * 1000L
            if (now - lastSeenTimestamp > fifteenMinutesMs) {
                return DeviceStatus.RED_OFFLINE
            }

            return when (trackingMode.uppercase()) {
                "LIVE", "REALTIME" -> DeviceStatus.GREEN_LIVE
                "INTERVAL" -> DeviceStatus.BLUE_INTERVAL
                "OFF", "STANDBY" -> DeviceStatus.YELLOW_IDLE
                else -> DeviceStatus.YELLOW_IDLE
            }
        }
}

data class AlertThresholds(
    val lowBatteryPercent: Int = 15,
    val offlineThresholdMinutes: Int = 10,
    val speedLimitKmH: Int = 0, // 0 = disabled
    val geofences: List<GeofenceZone> = emptyList(),
    val isCrashDetectionEnabled: Boolean = true,
    val isShockAlertEnabled: Boolean = false
)
