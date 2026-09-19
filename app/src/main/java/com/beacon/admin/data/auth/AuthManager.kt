package com.beacon.admin.data.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beacon.shared.constants.SharedPrefsKeys
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private companion object {
        const val CACHED_USER_ID = "cached_admin_user_id"
    }
    sealed interface AuthState {
        data class Authenticated(val userId: String, val isOffline: Boolean = false) : AuthState
        data object Unauthenticated : AuthState
        data object SessionExpired : AuthState
    }

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

    val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    val authStateFlow: Flow<AuthState> = callbackFlow {
        val cachedUserId = encryptedPrefs.getString(CACHED_USER_ID, null)
        firebaseAuth.currentUser?.let { trySend(AuthState.Authenticated(it.uid)) }
            ?: cachedUserId?.let { trySend(AuthState.Authenticated(it, isOffline = true)) }
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                encryptedPrefs.edit().putString(CACHED_USER_ID, user.uid).apply()
                trySend(AuthState.Authenticated(user.uid))
            } else {
                val cachedId = encryptedPrefs.getString(CACHED_USER_ID, null)
                if (cachedId != null) {
                    trySend(AuthState.Authenticated(cachedId, isOffline = true))
                } else {
                    trySend(AuthState.Unauthenticated)
                }
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    suspend fun ensureAuthenticated(): String? {
        val existingId = currentUserId
        if (!existingId.isNullOrEmpty()) {
            return existingId
        }

        return try {
            val authResult = firebaseAuth.signInAnonymously().await()
            val newUid = authResult.user?.uid
            Log.d("PairDebug", "AuthManager: Authenticated anonymously with UID: $newUid")
            newUid
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.w("PairDebug", "Auth session expired", e)
            null
        } catch (e: Exception) {
            Log.e("PairDebug", "AuthManager: Anonymous authentication failed", e)
            null
        }
    }

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let {
                encryptedPrefs.edit()
                    .putString(SharedPrefsKeys.ADMIN_EMAIL, email)
                    .putString(CACHED_USER_ID, it.uid)
                    .apply()
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
                encryptedPrefs.edit()
                    .putString(SharedPrefsKeys.ADMIN_EMAIL, email)
                    .putString(CACHED_USER_ID, it.uid)
                    .apply()
                Result.success(it)
            } ?: Result.failure(Exception("Failed to sign in"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        AuthSessionCleanupRegistry.clear()
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
