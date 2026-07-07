package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.shared.models.Location
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(deviceId: String, repository: com.beacon.admin.repository.LocationRepository) {

    val locations = remember { mutableStateOf<List<Location>>(emptyList()) }
    val loading = remember { mutableStateOf(false) }
    val selectedDate = remember { mutableStateOf(Calendar.getInstance()) }
    val scope = rememberCoroutineScope()

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dateText = remember(selectedDate.value) { dateFormat.format(selectedDate.value.time) }

    Surface(color = MaterialTheme.colorScheme.background) {

        Column(Modifier.fillMaxSize().padding(16.dp)) {

            Text(
                "Location History", 
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                Text(
                    dateText, 
                    modifier = Modifier.weight(1f).padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                Button(
                    onClick = {
                        scope.launch {
                            loading.value = true
                            val cal = selectedDate.value
                            val start = cal.clone() as Calendar
                            start.set(Calendar.HOUR_OF_DAY, 0)
                            start.set(Calendar.MINUTE, 0)
                            start.set(Calendar.SECOND, 0)
                            
                            val end = cal.clone() as Calendar
                            end.set(Calendar.HOUR_OF_DAY, 23)
                            end.set(Calendar.MINUTE, 59)
                            end.set(Calendar.SECOND, 59)

                            val res = repository.getLocationsInTimeRange(deviceId, start.timeInMillis, end.timeInMillis)
                            locations.value = res.getOrDefault(emptyList())
                            loading.value = false
                        }
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Load History")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (loading.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (locations.value.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No history for this date", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(locations.value) { loc ->
                    LocationItem(loc)
                }
            }
        }
    }
}

@Composable
fun LocationItem(location: Location) {
    val time = location.timestamp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "${location.latitude}, ${location.longitude}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                Text(
                    text = "ACCURACY: ±${location.accuracy}M",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
