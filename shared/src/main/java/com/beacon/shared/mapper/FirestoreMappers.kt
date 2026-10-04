package com.beacon.shared.mapper

import com.beacon.shared.models.Alert
import com.beacon.shared.models.Device
import com.beacon.shared.models.DeviceGroup
import com.google.firebase.firestore.DocumentSnapshot

// ALERT
fun DocumentSnapshot.toAlert(): Alert {
    return Alert(
        id = id,
        alert_type = getString("alert_type") ?: getString("alertType") ?: "",
        device_id = getString("device_id") ?: getString("deviceId") ?: "",
        device_name = getString("device_name") ?: getString("deviceName") ?: "Unknown",
        alert_severity = getString("alert_severity") ?: getString("alertSeverity") ?: "INFO",
        message = getString("message") ?: "",
        created_at = ((get("created_at") ?: get("createdAt")) as? Number)?.toLong() ?: System.currentTimeMillis(),
        is_read = getBoolean("is_read") ?: getBoolean("isRead") ?: false
    )
}

// DEVICE
fun DocumentSnapshot.toDevice(): Device {
    val lastLocMap = (get("last_location") as? Map<*, *>) ?: (get("location") as? Map<*, *>)

    val lat = getDouble("latitude")
        ?: getDouble("lat")
        ?: (lastLocMap?.get("latitude") as? Number)?.toDouble()
        ?: (lastLocMap?.get("lat") as? Number)?.toDouble()
        ?: 0.0

    val lng = getDouble("longitude")
        ?: getDouble("lng")
        ?: (lastLocMap?.get("longitude") as? Number)?.toDouble()
        ?: (lastLocMap?.get("lng") as? Number)?.toDouble()
        ?: 0.0

    val accuracyVal = getDouble("accuracy")?.toFloat()
        ?: (lastLocMap?.get("accuracy") as? Number)?.toFloat()
        ?: 0f

    val thresholdsMap = get("alertThresholds") as? Map<*, *>
    val alertThresholds = com.beacon.shared.models.AlertThresholds(
        lowBatteryPercent = (thresholdsMap?.get("lowBatteryPercent") as? Number)?.toInt() ?: 15,
        offlineThresholdMinutes = (thresholdsMap?.get("offlineThresholdMinutes") as? Number)?.toInt() ?: 10,
        speedLimitKmH = (thresholdsMap?.get("speedLimitKmH") as? Number)?.toInt() ?: 0,
        isCrashDetectionEnabled = thresholdsMap?.get("isCrashDetectionEnabled") as? Boolean ?: true,
        isShockAlertEnabled = thresholdsMap?.get("isShockAlertEnabled") as? Boolean ?: false
    )

    return Device(
        deviceId = getString("deviceId") ?: getString("device_id") ?: id,
        deviceName = getString("deviceName") ?: getString("device_name") ?: "New Device",
        batteryLevel = getLong("batteryLevel")?.toInt()
            ?: getLong("battery_level")?.toInt()
            ?: getLong("battery")?.toInt()
            ?: 0,
        status = getString("status") ?: "paired",
        latitude = lat,
        longitude = lng,
        speed = getDouble("speed")?.toFloat()
            ?: getDouble("last_speed")?.toFloat()
            ?: getDouble("speedKmh")?.toFloat()
            ?: 0f,
        signalStrength = getLong("signal_strength")?.toInt()
            ?: getLong("signalStrength")?.toInt()
            ?: getLong("signal")?.toInt()
            ?: 0,
        ownerId = getString("ownerId") ?: getString("owner_id") ?: "",
        trackerAuthUid = getString("trackerAuthUid") ?: "",
        groupId = (getString("groupId") ?: getString("group_id"))?.takeIf { it.isNotBlank() },
        is_paired = getBoolean("is_paired") ?: getBoolean("isPaired") ?: false,
        trackingMode = getString("trackingMode") ?: getString("tracking_mode") ?: "interval",
        scheduledIntervalMillis = getLong("scheduledIntervalMillis") ?: 900_000L,
        liveIntervalMillis = getLong("liveIntervalMillis") ?: 10_000L,
        liveRevertAfterMillis = getLong("liveRevertAfterMillis") ?: 1_800_000L,
        revertToMode = getString("revertToMode") ?: "",
        trackingChangedAt = getLong("trackingChangedAt") ?: 0L,
        intervalSeconds = getLong("intervalSeconds")?.toInt() ?: getLong("interval_seconds")?.toInt() ?: 900,
        autoRevertSeconds = getLong("autoRevertSeconds")?.toInt() ?: getLong("auto_revert_seconds")?.toInt() ?: 1800,
        isEmergencyMode = getBoolean("isEmergencyMode") ?: getBoolean("is_emergency_mode") ?: false,
        batterySavingEnabled = getBoolean("batterySavingEnabled") ?: getBoolean("battery_saving_enabled") ?: true,
        stationaryIntervalMinutes = getLong("stationaryIntervalMinutes")?.toInt() ?: getLong("stationary_interval_minutes")?.toInt() ?: 45,
        commandMode = getString("commandMode") ?: getString("command_mode") ?: "",
        commandTimestamp = getLong("commandTimestamp") ?: getLong("command_timestamp") ?: 0L,
        accuracy = accuracyVal,
        alertThresholds = alertThresholds,
        lastSeenTimestamp = getLong("lastSeenTimestamp") ?: getLong("last_seen") ?: getLong("lastSeen") ?: getLong("updatedAt") ?: 0L
    )
}

fun DocumentSnapshot.toGroup(): DeviceGroup {
    return DeviceGroup(
        id = id,
        name = getString("name") ?: "",
        deviceIds = (get("device_ids") as? List<*>)?.mapNotNull { it as? String }
            ?: (get("deviceIds") as? List<*>)?.mapNotNull { it as? String }
            ?: emptyList()
    )
}
