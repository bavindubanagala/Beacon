package com.beacon.tracker.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.services.LocationTrackingService
import com.beacon.tracker.worker.ServiceWatchdogWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    @Inject
    lateinit var deviceAuthManager: DeviceAuthManager

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(tag, "System event detected: ${intent?.action}")

        if (intent?.action in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "com.htc.intent.action.QUICKBOOT_POWERON"
            )
        ) {
            // 1. Check if device is paired and was tracking
            if (deviceAuthManager.isPaired()) {
                Log.d(tag, "Device is paired, ensuring tracking service is running")
                val startIntent = Intent(context, LocationTrackingService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
            }

            // 2. Reconcile state with a one-time watchdog run
            val oneTimeRequest = OneTimeWorkRequestBuilder<ServiceWatchdogWorker>().build()
            WorkManager.getInstance(context).enqueue(oneTimeRequest)

            // 3. Schedule long-term periodic watchdog
            ServiceWatchdogWorker.schedule(context)
            
            Log.d(tag, "Service Watchdog recovery tasks orchestrated")
        }
    }
}
