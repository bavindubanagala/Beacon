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
import com.google.firebase.FirebaseNetworkException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val db = LocationDatabase.getDatabase(applicationContext)
        val authManager = DeviceAuthManager(applicationContext)
        if (authManager.getDeviceId().isBlank() || authManager.getDeviceSecret().isBlank()) {
            Log.e("SyncWorker", "Cannot sync locations without device authentication credentials")
            return Result.failure()
        }

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
                val uploadResult = repository.uploadLocationToHistory(beaconLoc)
                if (uploadResult.isSuccess) {
                    db.locationDao().delete(loc)
                } else if (uploadResult.exceptionOrNull()?.isTransientSyncFailure() == true) {
                    return Result.retry()
                } else {
                    Log.e("SyncWorker", "Unrecoverable location upload failure", uploadResult.exceptionOrNull())
                    return Result.failure()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return if (e.isTransientSyncFailure()) {
                    Result.retry()
                } else {
                    Log.e("SyncWorker", "Unrecoverable sync failure", e)
                    Result.failure()
                }
            }
        }

        return Result.success()
    }

    private fun Throwable.isTransientSyncFailure(): Boolean {
        return this is IOException ||
            this is TimeoutCancellationException ||
            this is FirebaseNetworkException
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
