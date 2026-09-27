package com.example.beaconadmin.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairingScreen(
    adminUid: String = "",
    viewModel: PairingViewModel = viewModel(),
    onPairingSuccess: () -> Unit = {}
) {
    val pairingState by viewModel.pairingState.collectAsState()

    LaunchedEffect(pairingState) {
        if (pairingState is PairingState.Paired) {
            onPairingSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pair New Device",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = pairingState) {
            is PairingState.Idle -> {
                Button(
                    onClick = { viewModel.generatePairingCode(adminUid) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate pairing code")
                }
            }
            is PairingState.Generated -> {
                Text(
                    text = "Enter this code on the Tracker device:",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.pairingCode,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Waiting for device to connect...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is PairingState.Paired -> {
                Text(
                    text = "Device paired successfully",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            is PairingState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.generatePairingCode(adminUid) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try again")
                }
            }
        }
    }
}
