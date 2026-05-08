package com.beacon.admin.repository

import android.content.Context
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.models.Group
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class GroupRepository(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val adminId = getAdminId(context)

    suspend fun createGroup(group: Group): Result<String> {
        return try {
            val doc = firestore.collection(FirebaseCollections.GROUPS)
                .document()
            val groupWithId = group.copy(group_id = doc.id)
            doc.set(groupWithId.toMap()).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroup(group: Group): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.GROUPS)
                .document(group.group_id)
                .set(group.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(groupId: String): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.GROUPS)
                .document(groupId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroup(groupId: String): Result<Group> {
        return try {
            val doc = firestore.collection(FirebaseCollections.GROUPS)
                .document(groupId)
                .get()
                .await()
            val group = doc.toObject(Group::class.java)
            if (group != null) {
                Result.success(group)
            } else {
                Result.failure(Exception("Group not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllGroups(): Result<List<Group>> {
        return try {
            val groups = firestore.collection(FirebaseCollections.GROUPS)
                .get()
                .await()
                .toObjects(Group::class.java)
            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addDeviceToGroup(groupId: String, deviceId: String): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.GROUPS)
                .document(groupId)
                .update("device_ids", com.google.firebase.firestore.FieldValue.arrayUnion(deviceId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeDeviceFromGroup(groupId: String, deviceId: String): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.GROUPS)
                .document(groupId)
                .update("device_ids", com.google.firestore.FieldValue.arrayRemove(deviceId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getAdminId(context: Context): String {
        val encPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "admin_auth",
            androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return encPrefs.getString("admin_uid", "") ?: ""
    }
}
