package com.beacon.tracker.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

@HiltWorker
class LocationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val repository: FirebaseTrackerRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("LocationWorker", "Background interval location update started")

        // 1. Verify Permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationWorker", "Location permission missing")
            return Result.failure()
        }

        return try {
            // 2. Obtain single-shot high-accuracy fix
            val cts = CancellationTokenSource()
            val location: Location? = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).await()

            if (location != null) {
                val batteryLevel = getBatteryLevel()
                
                // 3. Push telemetry to Firestore via repository
                val updateResult = repository.updateDeviceStatus(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    batteryLevel = batteryLevel,
                    signalStrength = 0,
                    deviceMotionStatus = "interval"
                )

                if (updateResult.isSuccess) {
                    Log.d("LocationWorker", "Interval location update synced successfully")
                    Result.success()
                } else {
                    Log.e("LocationWorker", "Failed to sync interval location")
                    Result.retry()
                }
            } else {
                Log.w("LocationWorker", "No location fix available")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("LocationWorker", "Error during interval tracking job", e)
            Result.retry()
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
