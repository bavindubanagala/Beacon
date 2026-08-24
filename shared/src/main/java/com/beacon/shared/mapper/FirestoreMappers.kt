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
        created_at = (get("created_at") as? Number)?.toLong() ?: System.currentTimeMillis(),
        is_read = getBoolean("is_read") ?: false
    )
}

// DEVICE
fun DocumentSnapshot.toDevice(): Device {
    return Device(
        deviceId = getString("deviceId") ?: getString("device_id") ?: "",
        deviceName = getString("deviceName") ?: getString("device_name") ?: "",
        batteryLevel = ((get("batteryLevel") ?: get("battery_level")) as? Number)?.toInt() ?: 0,
        status = getString("status") ?: "offline",
        ownerId = getString("ownerId") ?: getString("owner_id") ?: "",
        trackerAuthUid = getString("trackerAuthUid"),
        groupId = getString("groupId") ?: getString("group_id"),
        is_paired = getBoolean("is_paired") ?: false,
        trackingMode = getString("tracking_mode") ?: getString("trackingMode") ?: "interval",
        intervalSeconds = ((get("interval_seconds") ?: get("intervalSeconds") ?: 900L) as? Number)?.toInt() ?: 900,
        autoRevertSeconds = ((get("auto_revert_seconds") ?: get("autoRevertSeconds") ?: 1800L) as? Number)?.toInt() ?: 1800,
        isEmergencyMode = getBoolean("is_emergency_mode") ?: getBoolean("isEmergencyMode") ?: false,
        batterySavingEnabled = getBoolean("battery_saving_enabled") ?: getBoolean("batterySavingEnabled") ?: true,
        stationaryIntervalMinutes = ((get("stationary_interval_minutes") ?: get("stationaryIntervalMinutes") ?: 45L) as? Number)?.toInt() ?: 45,
        commandMode = getString("command_mode") ?: getString("commandMode"),
        commandDurationMinutes = ((get("command_duration_minutes") ?: get("commandDurationMinutes")) as? Number)?.toInt(),
        commandTimestamp = ((get("command_timestamp") ?: get("commandTimestamp")) as? Number)?.toLong()
    )
}

fun DocumentSnapshot.toGroup(): Group {
    return Group(
        groupId = id,
        name = getString("name") ?: "",
        deviceIds = (get("device_ids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    )
}
