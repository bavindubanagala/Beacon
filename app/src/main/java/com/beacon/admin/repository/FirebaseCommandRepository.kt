package com.beacon.admin.repository

import android.util.Log
import com.beacon.shared.models.RemoteCommand
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCommandRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("device_commands")
    private val auth = FirebaseAuth.getInstance()

    suspend fun sendCommand(command: RemoteCommand): Result<Unit> = runCatching {
        val docRef = collection.document()
        docRef.set(command.copy(id = docRef.id)).await()
    }.map { Unit }

    fun getLatestCommandStatus(deviceId: String): Flow<RemoteCommand?> {
        if (auth.currentUser == null) return flowOf(null)

        return callbackFlow {
            val subscription = collection
                .whereEqualTo("deviceId", deviceId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("CommandRepo", "getLatestCommandStatus error", error)
                        trySend(null)
                        return@addSnapshotListener
                    }
                    val command = snapshot?.documents?.firstOrNull()?.toObject(RemoteCommand::class.java)
                    trySend(command)
                }
            awaitClose { subscription.remove() }
        }
    }
}
