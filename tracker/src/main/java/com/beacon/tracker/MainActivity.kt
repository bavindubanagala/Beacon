package com.beacon.tracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.shared.constants.SharedPrefsKeys
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.services.LocationTrackingService
import com.beacon.tracker.ui.theme.BeaconTrackerTheme

class MainActivity : AppCompatActivity() {

    private val tag = "TrackerMainActivity"
    private lateinit var deviceAuthManager: DeviceAuthManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(tag, "All permissions granted")
            startLocationTracking()
        } else {
            Log.w(tag, "Some permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceAuthManager = DeviceAuthManager(this)

        val prefs = getSharedPreferences(SharedPrefsKeys.PREFS_NAME, MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean(SharedPrefsKeys.ONBOARDING_COMPLETED, false)

        setContent {
            BeaconTrackerTheme {
                Surface(color = MaterialTheme.colors.background) {
                    if (!onboardingDone) {
                        OnboardingScreen(deviceAuthManager) {
                            prefs.edit()
                                .putBoolean(SharedPrefsKeys.ONBOARDING_COMPLETED, true)
                                .apply()
                            requestTrackerPermissions()
                        }
                    } else {
                        StatusScreen(deviceAuthManager)
                    }
                }
            }
        }
    }

    private fun requestTrackerPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // ACCESS_BACKGROUND_LOCATION must be requested separately after fine/coarse
        // are granted; requesting it here alongside them causes denial on API 30+.
        // Add only for API 29 where it can be bundled.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startLocationTracking() {
        Log.d(tag, "Starting location tracking service")
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @Composable
    private fun OnboardingScreen(
        authManager: DeviceAuthManager,
        onReadyClicked: () -> Unit
    ) {
        val context = LocalContext.current
        val deviceId = remember { authManager.getDeviceId() }
        var copied by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Welcome to Beacon Tracker",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "This app sends your location to the admin in real-time.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Your Device ID:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = deviceId,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Device ID", deviceId)
                        )
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (copied) "Copied!" else "Copy Device ID")
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "⚠️ Warning: If you uninstall this app, location tracking will stop.",
                    fontSize = 11.sp,
                    color = Color(0xFFE57373)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onReadyClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "I'm Ready - Start Tracking", fontSize = 16.sp)
                }
            }
        }
    }

    @Composable
    private fun StatusScreen(authManager: DeviceAuthManager) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "✓ Tracking Active",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Your location is being sent to the admin.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Device ID: ${authManager.getDeviceId().take(8)}...",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
