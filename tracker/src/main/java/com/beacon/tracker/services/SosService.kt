package com.beacon.tracker.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SosService : Service() {
    // This could handle the high-priority background needs if we were fully standalone, 
    // but the logic is primarily driven by the LocationTrackingService commands now.
    override fun onBind(intent: Intent?): IBinder? = null
}
