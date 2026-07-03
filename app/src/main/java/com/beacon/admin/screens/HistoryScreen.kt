package com.beacon.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beacon.shared.models.Location
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen() {

    val locations = remember { mutableStateOf<List<Location>>(emptyList()) }
    val loading = remember { mutableStateOf(false) }
    val date = remember { mutableStateOf("") }

    Surface(color = MaterialTheme.colors.background) {

        Column(Modifier.fillMaxSize().padding(16.dp)) {

            Text("Location History", fontSize = 24.sp)

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                OutlinedTextField(
                    value = date.value,
                    onValueChange = { date.value = it },
                    label = { Text("YYYY-MM-DD") },
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                Button(onClick = { /* future repo call */ }) {
                    Text("Load")
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
                    Text("No history", color = Color.Gray)
                }
                return@Column
            }

            LazyColumn {
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

    Card(Modifier.fillMaxWidth().padding(4.dp)) {

        Column(Modifier.padding(12.dp)) {

            Text("${location.latitude}, ${location.longitude}")

            Spacer(Modifier.height(4.dp))

            Text(
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(Date(time)),
                color = Color.Gray,
                fontSize = 11.sp
            )

            Text("±${location.accuracy}m", fontSize = 11.sp, color = Color.Gray)
        }
    }
}
