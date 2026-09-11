package com.beacon.admin.ui.devices

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PairScreen(
    viewModel: DevicesViewModel = hiltViewModel(),
    onPairSuccess: () -> Unit = {}
) {
    var codeText by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState.pairingSuccess) {
            Toast.makeText(context, "Device paired successfully!", Toast.LENGTH_SHORT).show()
            viewModel.resetPairingState()
            onPairSuccess()
        } else if (uiState.error != null) {
            Toast.makeText(context, uiState.error, Toast.LENGTH_LONG).show()
            viewModel.resetPairingState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = codeText,
            onValueChange = { codeText = it },
            label = { Text("Enter 6-Digit Pairing Code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                Log.d("PairDebug", "Pair button tapped with code: $codeText")
                if (codeText.isNotBlank()) {
                    viewModel.pairDevice(codeText.trim())
                } else {
                    Toast.makeText(context, "Please enter a valid pairing code", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !uiState.isPairing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isPairing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Pair Device")
            }
        }
    }
}
