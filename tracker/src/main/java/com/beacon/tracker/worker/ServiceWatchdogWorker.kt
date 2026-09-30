package com.beacon.tracker.worker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.service.LocationTrackingService
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val firestore: FirebaseFirestore,
    private val deviceAuthManager: DeviceAuthManager,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WatchdogWorker"
        const val WORK_NAME = "service_watchdog_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                4, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting watchdog check")

        val deviceId = deviceAuthManager.getDeviceId()
        if (deviceId.isEmpty()) return Result.failure()

        return try {
            val snapshot = firestore.collection("devices").document(deviceId).get().await()
            if (!snapshot.exists()) return Result.failure()

            val isPaired = snapshot.getBoolean("is_paired") ?: false
            if (!isPaired) {
                Log.d(TAG, "Device not paired, skipping recovery")
                return Result.success()
            }

            val intent = Intent(context, LocationTrackingService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ can refuse to start a foreground service from the background.
                Log.w(TAG, "Could not start tracking service from background", e)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog check failed", e)
            Result.retry()
        }
    }
}
