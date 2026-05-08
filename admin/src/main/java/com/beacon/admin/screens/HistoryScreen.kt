package com.beacon.admin.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.MaterialTheme
import com.beacon.shared.models.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val locations = remember { mutableStateOf<List<Location>>(emptyList()) }
    val isLoading = remember { mutableStateOf(false) }
    val selectedDate = remember { mutableStateOf("") }

    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Location History",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Date Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = selectedDate.value,
                    onValueChange = { selectedDate.value = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { /* Load history */ }
                ) {
                    Text("Load")
                }
            }

            // Loading State
            if (isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (locations.value.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Select a date to view location history",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            } else {
                // Location Timeline
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(locations.value) { location ->
                        LocationHistoryItem(location)
                    }
                }
            }
        }
    }
}

@Composable
fun LocationHistoryItem(location: Location) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        String.format("%.4f, %.4f", location.latitude, location.longitude),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(location.timestamp)),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Accuracy
                Box(
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "±${location.accuracy.toInt()}m",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
