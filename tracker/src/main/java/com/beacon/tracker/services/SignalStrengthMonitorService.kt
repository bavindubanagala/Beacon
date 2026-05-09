package com.beacon.tracker.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Monitors cellular signal strength and broadcasts updates for batching into location updates.
 * NOT used for direct Firebase writes—all data is batched into location updates.
 * Samples signal strength every 10 seconds.
 */
class SignalStrengthMonitorService : Service() {
    private val tag = "SignalStrengthMonitorService"
    private val handler = Handler(Looper.getMainLooper())
    private var samplingRunnable: Runnable? = null
    private val samplingIntervalMs = 10000L // Sample every 10 seconds

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "SignalStrengthMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Starting signal strength monitoring")
        startSignalSampling()
        return START_STICKY
    }

    private fun startSignalSampling() {
        samplingRunnable = object : Runnable {
            override fun run() {
                val signalStrength = getSignalStrength()
                Log.d(tag, "Signal strength: $signalStrength%")

                // Broadcast to LocationTrackingService
                val broadcastIntent = Intent(SIGNAL_UPDATE_ACTION)
                broadcastIntent.putExtra("signal_strength", signalStrength)
                sendBroadcast(broadcastIntent)

                // Reschedule next sample
                handler.postDelayed(this, samplingIntervalMs)
            }
        }

        handler.post(samplingRunnable!!)
    }

    private fun getSignalStrength(): Int {
        return try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Get signal strength (API 30+) or fallback to deprecated API
            val signalStrength = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                telephonyManager.signalStrength
            } else {
                null
            }

            if (signalStrength != null) {
                // Convert to 0-100 scale
                val level = signalStrength.level // 0-4
                (level * 25).coerceIn(0, 100)
            } else {
                // Fallback: return random or last known
                50
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get signal strength", e)
            50
        }
    }

    override fun onDestroy() {
        Log.d(tag, "SignalStrengthMonitorService destroyed")
        samplingRunnable?.let {
            handler.removeCallbacks(it)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val SIGNAL_UPDATE_ACTION = "com.beacon.tracker.SIGNAL_UPDATE"
    }
}

