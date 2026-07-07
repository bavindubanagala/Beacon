package com.beacon.admin.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beacon.shared.constants.SharedPrefsKeys
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class AuthManager(context: Context) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "admin_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let {
                encryptedPrefs.edit().putString(SharedPrefsKeys.ADMIN_EMAIL, email).apply()
                Result.success(it)
            } ?: Result.failure(Exception("Failed to create user account"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            result.user?.let {
                encryptedPrefs.edit().putString(SharedPrefsKeys.ADMIN_EMAIL, email).apply()
                Result.success(it)
            } ?: Result.failure(Exception("Failed to sign in"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
        encryptedPrefs.edit().clear().apply()
    }

    fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }

    fun isAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    fun getAdminEmail(): String {
        return encryptedPrefs.getString(SharedPrefsKeys.ADMIN_EMAIL, "") ?: ""
    }

    fun isDarkMode(): Boolean {
        return encryptedPrefs.getBoolean("dark_mode", false)
    }

    fun setDarkMode(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.updatePassword(newPassword)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
