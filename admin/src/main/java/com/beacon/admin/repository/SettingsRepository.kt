package com.beacon.admin.repository

import android.content.Context
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.models.AdminSettings
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SettingsRepository(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val adminId: String? = getAdminId(context)

    suspend fun getAdminSettings(): Result<AdminSettings> {
        return try {
            if (adminId == null) return Result.failure(Exception("Admin ID not found"))

            val doc = firestore.collection(FirebaseCollections.ADMIN_SETTINGS)
                .document(adminId)
                .get()
                .await()

            val settings = doc.toObject(AdminSettings::class.java)
            if (settings != null) {
                Result.success(settings)
            } else {
                // Return default settings if none exist
                val defaultSettings = AdminSettings()
                Result.success(defaultSettings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAdminSettings(settings: AdminSettings): Result<Unit> {
        return try {
            if (adminId == null) return Result.failure(Exception("Admin ID not found"))

            firestore.collection(FirebaseCollections.ADMIN_SETTINGS)
                .document(adminId)
                .set(settings.toMap())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDefaultTrackingInterval(intervalSeconds: Long): Result<Unit> {
        return try {
            if (adminId == null) return Result.failure(Exception("Admin ID not found"))

            firestore.collection(FirebaseCollections.ADMIN_SETTINGS)
                .document(adminId)
                .update("default_tracking_interval", intervalSeconds)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAlertThresholds(
        lowBatteryPercent: Int,
        offlineTimeoutSeconds: Long,
        weakSignalThreshold: Int
    ): Result<Unit> {
        return try {
            if (adminId == null) return Result.failure(Exception("Admin ID not found"))

            firestore.collection(FirebaseCollections.ADMIN_SETTINGS)
                .document(adminId)
                .update(
                    "alert_thresholds.low_battery_percent", lowBatteryPercent,
                    "alert_thresholds.offline_timeout_seconds", offlineTimeoutSeconds,
                    "alert_thresholds.weak_signal_threshold", weakSignalThreshold
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getAdminId(context: Context): String? {
        return try {
            val encPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "admin_auth",
                androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            encPrefs.getString("admin_uid", null)
        } catch (e: Exception) {
            null
        }
    }
}
