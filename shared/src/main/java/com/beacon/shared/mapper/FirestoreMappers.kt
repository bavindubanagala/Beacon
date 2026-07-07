package com.beacon.shared.mapper

import com.beacon.shared.models.Alert
import com.beacon.shared.models.Device
import com.beacon.shared.models.Group
import com.google.firebase.firestore.DocumentSnapshot

// ALERT
fun DocumentSnapshot.toAlert(): Alert {
    return Alert(
        id = id,
        alert_type = getString("alert_type") ?: "",
        device_id = getString("device_id") ?: "",
        device_name = getString("device_name") ?: getString("deviceName") ?: "Unknown",
        alert_severity = getString("alert_severity") ?: "INFO",
        message = getString("message") ?: "",
        created_at = getLong("created_at") ?: System.currentTimeMillis(),
        is_read = getBoolean("is_read") ?: false
    )
}

// DEVICE
fun DocumentSnapshot.toDevice(): Device {
    return Device(
        deviceId = getString("deviceId") ?: getString("device_id") ?: "",
        deviceName = getString("deviceName") ?: getString("device_name") ?: "",
        batteryLevel = (getLong("batteryLevel") ?: getLong("battery_level") ?: 0L).toInt(),
        status = getString("status") ?: "offline",
        ownerId = getString("ownerId") ?: getString("owner_id") ?: "",
        groupId = getString("groupId") ?: getString("group_id"),
        is_paired = getBoolean("is_paired") ?: false,
        trackingMode = getString("tracking_mode") ?: getString("trackingMode") ?: "interval",
        intervalSeconds = (getLong("interval_seconds") ?: getLong("intervalSeconds") ?: 900L).toInt(),
        autoRevertSeconds = (getLong("auto_revert_seconds") ?: getLong("autoRevertSeconds") ?: 1800L).toInt(),
        isEmergencyMode = getBoolean("is_emergency_mode") ?: getBoolean("isEmergencyMode") ?: false,
        commandMode = getString("command_mode") ?: getString("commandMode"),
        commandDurationMinutes = (getLong("command_duration_minutes") ?: getLong("commandDurationMinutes"))?.toInt(),
        commandTimestamp = getLong("command_timestamp") ?: getLong("commandTimestamp")
    )
}

fun DocumentSnapshot.toGroup(): Group {
    return Group(
        groupId = id,
        name = getString("name") ?: "",
        deviceIds = (get("device_ids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    )
}
