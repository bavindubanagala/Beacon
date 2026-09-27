package com.example.beaconadmin.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PairingState {
    object Idle : PairingState()
    data class Generated(val pairingCode: String) : PairingState()
    object Paired : PairingState()
    data class Error(val message: String) : PairingState()
}

class PairingViewModel(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private var pairingListener: ListenerRegistration? = null

    fun generatePairingCode(adminUid: String) {
        stopObserving()
        viewModelScope.launch {
            val code = (100000..999999).random().toString()
            val pairingData = mapOf(
                "code" to code,
                "adminUid" to adminUid,
                "createdAt" to System.currentTimeMillis(),
                "used" to false
            )
            firestore.collection("pairings")
                .document(code)
                .set(pairingData)
                .addOnSuccessListener {
                    _pairingState.value = PairingState.Generated(code)
                    observePairingCompletion(code)
                }
                .addOnFailureListener { e ->
                    _pairingState.value = PairingState.Error(e.message ?: "Failed to generate pairing code.")
                }
        }
    }

    private fun observePairingCompletion(code: String) {
        stopObserving()
        pairingListener = firestore.collection("pairings")
            .document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _pairingState.value = PairingState.Error(error.message ?: "Pairing listener error.")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val isUsed = snapshot.getBoolean("used") ?: false
                    if (isUsed) {
                        _pairingState.value = PairingState.Paired
                    }
                }
            }
    }

    fun resetState() {
        stopObserving()
        _pairingState.value = PairingState.Idle
    }

    private fun stopObserving() {
        pairingListener?.remove()
        pairingListener = null
    }

    override fun onCleared() {
        super.onCleared()
        stopObserving()
    }
}
