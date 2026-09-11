package com.beacon.admin.ui.utils

import androidx.compose.ui.graphics.Color
import com.beacon.shared.models.DeviceStatusLight
import java.util.concurrent.TimeUnit

fun getStatusUiConfig(status: DeviceStatusLight): Pair<Color, String> {
    return when (status) {
        DeviceStatusLight.GREEN_LIVE -> Color(0xFF00E676) to "Real-Time Live"
        DeviceStatusLight.BLUE_INTERVAL -> Color(0xFF00B0FF) to "Interval Tracking"
        DeviceStatusLight.YELLOW_IDLE -> Color(0xFFFFD600) to "Idle & Reachable"
        DeviceStatusLight.RED_OFFLINE -> Color(0xFFFF5252) to "Offline / Unreachable"
        DeviceStatusLight.GRAY_UNPAIRED -> Color(0xFF8E8E93) to "Unpaired"
    }
}

fun formatRelativeSyncTime(timestamp: Long): String {
    if (timestamp == 0L) return "No recent sync"
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    if (diffMs < 0) return "Updated just now"
    
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)

    return when {
        seconds < 60 -> "Updated ${seconds}s ago"
        minutes < 60 -> "Updated ${minutes}m ago"
        hours < 24 -> "Updated ${hours}h ago"
        else -> "Updated ${days}d ago"
    }
}
