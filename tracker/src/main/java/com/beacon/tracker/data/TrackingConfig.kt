package com.beacon.tracker.data

enum class TrackingMode {
    SCHEDULED,
    LIVE,
    ONLINE;

    companion object {
        fun fromString(value: String?): TrackingMode {
            return when (value?.uppercase()) {
                "LIVE" -> LIVE
                "ONLINE" -> ONLINE
                "SCHEDULED" -> SCHEDULED
                else -> SCHEDULED
            }
        }
    }
}

data class TrackingConfig(
    val mode: TrackingMode = TrackingMode.SCHEDULED,
    val scheduledIntervalMillis: Long = 900_000L,
    val liveIntervalMillis: Long = 10_000L,
    val liveRevertAfterMillis: Long = 1_800_000L,
    val revertToMode: TrackingMode = TrackingMode.SCHEDULED,
    val trackingChangedAt: Long = 0L,
    val onlineHeartbeatIntervalMillis: Long = DEFAULT_ONLINE_HEARTBEAT_MILLIS
) {
    companion object {
        const val DEFAULT_ONLINE_HEARTBEAT_MILLIS: Long = 300_000L
        const val MIN_SCHEDULED_INTERVAL_MILLIS: Long = 15_000L
        const val MAX_SCHEDULED_INTERVAL_MILLIS: Long = 2_592_000_000L // 30 days
        const val MIN_LIVE_INTERVAL_MILLIS: Long = 5_000L
        const val MAX_LIVE_INTERVAL_MILLIS: Long = 60_000L
    }
}
