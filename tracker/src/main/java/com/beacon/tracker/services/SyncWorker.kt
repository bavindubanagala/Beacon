package com.beacon.tracker.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.beacon.tracker.database.LocationDatabase
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.beacon.tracker.auth.DeviceAuthManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.beacon.shared.models.Location as BeaconLocation
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val db = LocationDatabase.getDatabase(applicationContext)
        val authManager = DeviceAuthManager(applicationContext)
        val repository = FirebaseTrackerRepository(
            FirebaseFirestore.getInstance(),
            FirebaseDatabase.getInstance("https://gen-lang-client-0281237877-default-rtdb.asia-southeast1.firebasedatabase.app/"),
            authManager
        )

        val pending = db.locationDao().getAllPending()
        if (pending.isEmpty()) return Result.success()

        Log.d("SyncWorker", "Found ${pending.size} pending locations to sync")

        for (loc in pending) {
            val beaconLoc = BeaconLocation(
                deviceId = loc.deviceId,
                timestamp = loc.timestamp,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                provider = loc.provider,
                speed = loc.speed,
                heading = loc.heading,
                batteryLevel = loc.batteryLevel,
                signalStrength = loc.signalStrength,
                deviceMotionStatus = loc.deviceMotionStatus
            )

            try {
                val success = repository.uploadLocationToHistory(beaconLoc).isSuccess
                if (success) {
                    db.locationDao().delete(loc)
                } else {
                    return Result.retry()
                }
            } catch (e: Exception) {
                return Result.retry()
            }
        }

        return Result.success()
    }

    companion object {
        fun createSyncWorkRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
    }
}
