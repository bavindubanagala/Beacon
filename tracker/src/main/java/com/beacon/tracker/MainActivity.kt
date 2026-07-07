package com.beacon.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beacon.tracker.services.LocationTrackingService
import com.beacon.tracker.ui.TrackerViewModel
import com.beacon.tracker.ui.theme.BeaconTrackerTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure service is started
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            val viewModel: TrackerViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode

            BeaconTrackerTheme(darkTheme = isDarkMode) {
                MainContent(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: TrackerViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val isPaired by viewModel.isPaired
        val isDarkMode by viewModel.isDarkMode
        
        // Listen for status updates from service
        StatusUpdateReceiver(viewModel)
        
        // Request permissions if not granted
        PermissionRequest()
        
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Beacon Tracker", style = MaterialTheme.typography.titleMedium) },
                    actions = {
                        IconButton(onClick = { viewModel.toggleDarkMode(!isDarkMode) }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime,
                                contentDescription = "Toggle Theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                if (isPaired) {
                    StatusScreen(viewModel)
                } else {
                    PairingScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun StatusUpdateReceiver(viewModel: TrackerViewModel) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val message = intent?.getStringExtra(LocationTrackingService.EXTRA_STATUS_MESSAGE)
                if (message != null) {
                    viewModel.updateStatus(message)
                }
            }
        }
        val filter = IntentFilter(LocationTrackingService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

@Composable
fun PermissionRequest() {
    val context = LocalContext.current
    val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            // Permission granted, restart service to be sure
            val serviceIntent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            launcher.launch(permissions)
        }
    }
}

@Composable
fun PairingScreen(viewModel: TrackerViewModel) {
    val pairingCode by viewModel.pairingCode
    val expiresAt by viewModel.pairingExpiresAt
    val context = LocalContext.current
    
    var timeLeft by remember { mutableStateOf(0L) }
    
    LaunchedEffect(expiresAt) {
        while (expiresAt > System.currentTimeMillis()) {
            timeLeft = (expiresAt - System.currentTimeMillis()) / 1000
            kotlinx.coroutines.delay(1000)
        }
        timeLeft = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Beacon",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "THIS DEVICE IS NOT PAIRED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (pairingCode == null || timeLeft <= 0) {
            Button(
                onClick = { viewModel.generatePairingCode() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Generate Pairing Code")
            }
        } else {
            Text(
                "Your Pairing Code:", 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = pairingCode!!,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Countdown Timer
            val minutes = timeLeft / 60
            val seconds = timeLeft % 60
            Text(
                text = String.format("Expires in %02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.labelMedium,
                color = if (timeLeft < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Pairing Code", pairingCode)
                        clipboard.setPrimaryClip(clip)
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Text("Copy")
                }

                OutlinedButton(
                    onClick = { viewModel.generatePairingCode() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Regenerate")
                }
            }
        }
    }
}

@Composable
fun StatusScreen(viewModel: TrackerViewModel) {
    val deviceId by viewModel.deviceId
    val isUpdating by viewModel.isUpdating
    val statusMessage by viewModel.statusMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tracking Active",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "BEACON TRACKER SERVICE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "STATUS: ${statusMessage.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "ID: $deviceId",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = { viewModel.forceUpdate() },
            enabled = !isUpdating,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text("Syncing...")
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Force Update Now")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel.generatePairingCode() }) {
            Text("Need to re-pair? Generate new code", style = MaterialTheme.typography.bodySmall)
        }
    }
}
