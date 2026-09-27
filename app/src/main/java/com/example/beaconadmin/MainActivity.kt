package com.example.beaconadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.beaconadmin.ui.auth.AuthScreen
import com.example.beaconadmin.ui.detail.DeviceDetailScreen
import com.example.beaconadmin.ui.pairing.PairingScreen
import com.example.beaconadmin.ui.theme.BeaconTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeaconTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeaconAppNavigation()
                }
            }
        }
    }
}

@Composable
fun BeaconAppNavigation() {
    val auth = FirebaseAuth.getInstance()
    val navController = rememberNavController()

    val startDestination = if (auth.currentUser != null) {
        "dashboard"
    } else {
        "auth"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            MainDashboardScreen(
                onDeviceClick = { deviceId ->
                    navController.navigate("deviceDetail/$deviceId")
                },
                onPairDeviceClick = {
                    navController.navigate("pairing")
                },
                onSignOut = {
                    auth.signOut()
                    navController.navigate("auth") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "deviceDetail/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            DeviceDetailScreen(
                deviceId = deviceId,
                onUnpairSuccess = {
                    navController.popBackStack()
                }
            )
        }

        composable("pairing") {
            val currentUid = auth.currentUser?.uid ?: ""
            PairingScreen(
                adminUid = currentUid,
                onPairingSuccess = {
                    navController.popBackStack()
                }
            )
        }
    }
}
