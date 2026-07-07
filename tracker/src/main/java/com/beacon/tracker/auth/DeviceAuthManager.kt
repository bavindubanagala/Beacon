package com.beacon.tracker.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beacon.shared.constants.SharedPrefsKeys
import java.util.UUID

class DeviceAuthManager(private val context: Context) {
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
        // getString returns String? — coerce to non-null with ?: ""
        var deviceId = encryptedPrefs.getString(SharedPrefsKeys.DEVICE_ID, "") ?: ""
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(SharedPrefsKeys.DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun getDeviceSecret(): String {
        // getString returns String? — coerce to non-null with ?: ""
        var secret = encryptedPrefs.getString(SharedPrefsKeys.DEVICE_SECRET, "") ?: ""
        if (secret.isEmpty()) {
            secret = generateSecureSecret()
            encryptedPrefs.edit().putString(SharedPrefsKeys.DEVICE_SECRET, secret).apply()
        }
        return secret
    }

    private fun generateSecureSecret(): String {
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

    fun isPaired(): Boolean {
        return encryptedPrefs.getBoolean("is_paired", false)
    }

    fun setPaired(paired: Boolean) {
        encryptedPrefs.edit().putBoolean("is_paired", paired).apply()
    }

    fun isDarkMode(): Boolean {
        return encryptedPrefs.getBoolean("dark_mode", false)
    }

    fun setDarkMode(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("dark_mode", enabled).apply()
    }
}

