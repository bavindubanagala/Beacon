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
    val scheduledIntervalMillis: Long = 900_000L,
    val liveIntervalMillis: Long = 10_000L,
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
    // These limits mirror TrackingConfig in the Tracker app
    fun offlineAfterMillis(): Long {
        val normalizedMode = trackingMode.trim().uppercase()
        val base = when (normalizedMode) {
            "LIVE" -> maxOf(120_000L, 3 * liveIntervalMillis.coerceIn(5_000L, 60_000L))
            "ONLINE" -> 900_000L
            else -> 2 * scheduledIntervalMillis.coerceIn(15_000L, 2_592_000_000L) + 300_000L
        }
        // With battery saving on, the Tracker may pause to a 5 minute heartbeat
        return if (batterySavingEnabled) maxOf(base, 900_000L) else base
    }

    fun isOnlineAt(nowMillis: Long): Boolean {
        return lastSeenTimestamp > 0 && nowMillis - lastSeenTimestamp <= offlineAfterMillis()
    }

    val statusLight: DeviceStatus
        get() {
            if (!is_paired || status.equals("unpaired", ignoreCase = true)) {
                return DeviceStatus.GRAY_UNPAIRED
            }

            if (!isOnlineAt(System.currentTimeMillis())) {
                return DeviceStatus.RED_OFFLINE
            }

            return when (trackingMode.trim().uppercase()) {
                "LIVE", "REALTIME" -> DeviceStatus.GREEN_LIVE
                "SCHEDULED", "INTERVAL" -> DeviceStatus.BLUE_INTERVAL
                "ONLINE", "OFF", "STANDBY" -> DeviceStatus.YELLOW_IDLE
                else -> DeviceStatus.BLUE_INTERVAL
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
