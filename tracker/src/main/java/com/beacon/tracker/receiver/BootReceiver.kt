package com.beacon.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.service.LocationTrackingService
import com.beacon.tracker.worker.ServiceWatchdogWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    @Inject
    lateinit var deviceAuthManager: DeviceAuthManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == null) {
            Log.w(tag, "Ignoring broadcast without an action")
            return
        }
        Log.d(tag, "System event detected: $action")

        if (action in listOf(
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
        } else {
            Log.w(tag, "Ignoring unsupported broadcast action: $action")
        }
    }
}
