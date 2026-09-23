package com.example.beaconadmin.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beaconadmin.data.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: FirebaseUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    object PasswordResetSent : AuthUiState()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentUser: FirebaseUser?
        get() = repository.currentUser

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signIn(email, pass)
            result.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { error -> _uiState.value = AuthUiState.Error(error.message ?: "Sign in failed.") }
            )
        }
    }

    fun signUp(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signUp(email, pass)
            result.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { error -> _uiState.value = AuthUiState.Error(error.message ?: "Sign up failed.") }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.sendPasswordReset(email)
            result.fold(
                onSuccess = { _uiState.value = AuthUiState.PasswordResetSent },
                onFailure = { error -> _uiState.value = AuthUiState.Error(error.message ?: "Password reset failed.") }
            )
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.value = AuthUiState.Idle
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
