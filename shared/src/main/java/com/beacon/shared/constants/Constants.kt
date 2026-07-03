package com.beacon.shared.constants

object FirebaseCollections {
    const val ADMIN = "admin"
    const val ADMIN_ACCOUNT = "account"
    const val DEVICES = "devices"
    const val DEVICES_GROUPS = "devices_groups"
    const val LOCATION_HISTORY = "location_history"
    const val ALERTS = "alerts"
    const val FCM_TOKENS = "fcm_tokens"
}

object RealtimeDBPaths {
    const val LIVE_LOCATIONS = "live_locations"
}

object SharedPrefsKeys {
    const val PREFS_NAME = "beacon_prefs"
    const val DEVICE_ID = "device_id"
    const val DEVICE_SECRET = "device_secret"
    const val ADMIN_EMAIL = "admin_email"
    const val AUTH_TOKEN = "auth_token"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val LAST_LOCATION_SYNC = "last_location_sync"
    const val TRACKING_ENABLED = "tracking_enabled"
}

object TrackingDefaults {
    const val MIN_INTERVAL_SECONDS = 30
    const val MAX_INTERVAL_SECONDS = 300
    const val DEFAULT_INTERVAL_SECONDS = 60
    const val LOW_BATTERY_THRESHOLD = 15
    const val WEAK_SIGNAL_THRESHOLD = 20
    const val OFFLINE_THRESHOLD_MINUTES = 10
    const val HISTORY_RETENTION_DAYS = 30
    const val MAP_REFRESH_RATE_SECONDS = 5
    const val LOCATION_UPDATE_TIMEOUT_SECONDS = 60
}

object DeviceIcons {
    const val CAR = "car"
    const val PHONE = "phone"
    const val PERSON = "person"
    const val BIKE = "bike"
    const val PACKAGE = "package"
    const val BRIEFCASE = "briefcase"
    const val TRUCK = "truck"
    const val SCOOTER = "scooter"

    fun getAll(): List<String> = listOf(CAR, PHONE, PERSON, BIKE, PACKAGE, BRIEFCASE, TRUCK, SCOOTER)
}

object NotificationDefaults {
    const val NOTIFICATION_CHANNEL_ID = "beacon_alerts"
    const val NOTIFICATION_CHANNEL_NAME = "Beacon Alerts"
    const val NOTIFICATION_CHANNEL_IMPORTANCE = 4 // NotificationManager.IMPORTANCE_HIGH
    const val TRACKING_NOTIFICATION_ID = 1001
    const val TRACKING_NOTIFICATION_CHANNEL_ID = "beacon_tracking"
    const val TRACKING_NOTIFICATION_CHANNEL_NAME = "Beacon Tracking"
}

object ErrorMessages {
    const val FIREBASE_AUTH_ERROR = "Authentication failed. Please try again."
    const val DEVICE_NOT_FOUND = "Device not found. Please check the device ID."
    const val NETWORK_ERROR = "Network error. Please check your connection."
    const val LOCATION_PERMISSION_DENIED = "Location permission is required for tracking."
    const val LOCATION_DISABLED = "Location services are disabled. Please enable them."
    const val DEVICE_NOT_AUTHORIZED = "Device is not authorized. Please reinstall and re-pair."
}
