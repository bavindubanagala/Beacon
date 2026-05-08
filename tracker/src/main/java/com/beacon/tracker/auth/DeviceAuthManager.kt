package com.beacon.tracker.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beacon.shared.constants.SharedPrefsKeys
import java.util.UUID

class DeviceAuthManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "device_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getDeviceId(): String {
        var deviceId = encryptedPrefs.getString(SharedPrefsKeys.DEVICE_ID, "")
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(SharedPrefsKeys.DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun getDeviceSecret(): String {
        var secret = encryptedPrefs.getString(SharedPrefsKeys.DEVICE_SECRET, "")
        if (secret.isEmpty()) {
            secret = generateSecureSecret()
            encryptedPrefs.edit().putString(SharedPrefsKeys.DEVICE_SECRET, secret).apply()
        }
        return secret
    }

    private fun generateSecureSecret(): String {
        // Generate a 32-character secure random secret
        val charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..32)
            .map { charPool.random() }
            .joinToString("")
    }

    fun isAuthenticated(): Boolean {
        return getDeviceId().isNotEmpty() && getDeviceSecret().isNotEmpty()
    }

    fun clearAuth() {
        encryptedPrefs.edit().clear().apply()
    }
}
