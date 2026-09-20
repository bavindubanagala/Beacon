package com.beacon.admin

import android.app.Application
import io.sentry.android.core.SentryAndroid
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BeaconApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Safely initialize Sentry or guard against missing DSN configuration
        try {
            SentryAndroid.init(this) { options ->
                // Optional: set fallback or programmatically configure DSN if needed
            }
        } catch (e: Exception) {
            // Log initialization failure gracefully without crashing the app startup
            e.printStackTrace()
        }
    }
}