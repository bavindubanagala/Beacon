package com.beacon.admin.ui.utils

fun normalizeTrackingMode(raw: String?): String {
    val trimmed = raw?.trim()?.uppercase() ?: return "SCHEDULED"
    return when (trimmed) {
        "LIVE" -> "LIVE"
        "ONLINE" -> "ONLINE"
        else -> "SCHEDULED"
    }
}

fun modeDisplayName(mode: String): String {
    return when (mode.uppercase()) {
        "LIVE" -> "Live"
        "ONLINE" -> "Online"
        else -> "Scheduled"
    }
}

fun formatDelay(millis: Long): String {
    return when {
        millis > 0 && millis % 86_400_000L == 0L -> {
            val days = millis / 86_400_000L
            if (days == 1L) "1 day" else "$days days"
        }
        millis > 0 && millis % 3_600_000L == 0L -> {
            val hours = millis / 3_600_000L
            if (hours == 1L) "1 hour" else "$hours hours"
        }
        millis > 0 && millis % 60_000L == 0L -> {
            val mins = millis / 60_000L
            if (mins == 1L) "1 min" else "$mins min"
        }
        else -> {
            val seconds = if (millis > 0) millis / 1000L else 0L
            "${seconds}s"
        }
    }
}

fun buildModeLabel(mode: String, scheduledIntervalMillis: Long, liveIntervalMillis: Long): String {
    return when (mode.uppercase()) {
        "LIVE" -> {
            val interval = if (liveIntervalMillis > 0) liveIntervalMillis else 10_000L
            "Live - ${formatDelay(interval)}"
        }
        "SCHEDULED" -> {
            val interval = if (scheduledIntervalMillis > 0) scheduledIntervalMillis else 900_000L
            "Scheduled - ${formatDelay(interval)}"
        }
        "ONLINE" -> "Online - 5 min"
        else -> {
            val interval = if (scheduledIntervalMillis > 0) scheduledIntervalMillis else 900_000L
            "Scheduled - ${formatDelay(interval)}"
        }
    }
}
