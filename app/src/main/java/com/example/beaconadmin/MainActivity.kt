package com.example.beaconadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.beaconadmin.ui.auth.AuthScreen
import com.example.beaconadmin.ui.auth.AuthViewModel
import com.example.beaconadmin.ui.theme.BeaconTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeaconTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isAuthenticated by remember {
                        mutableStateOf(authViewModel.currentUser != null)
                    }

                    if (isAuthenticated) {
                        MainDashboardScreen(
                            onSignOut = {
                                authViewModel.signOut()
                                isAuthenticated = false
                            }
                        )
                    } else {
                        AuthScreen(
                            viewModel = authViewModel,
                            onAuthSuccess = {
                                isAuthenticated = true
                            }
                        )
                    }
                }
            }
        }
    }
}
