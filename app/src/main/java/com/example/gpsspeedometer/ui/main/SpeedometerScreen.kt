package com.example.gpsspeedometer.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpsspeedometer.ui.components.InfoChip
import com.example.gpsspeedometer.ui.components.SpeedChart
import com.example.gpsspeedometer.ui.components.SpeedDisplay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedometerScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrips: () -> Unit
) {
    val tripState by viewModel.tripState.collectAsState()
    val speedHistory by viewModel.speedHistory.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }

    // Status Logic
    val status = remember(tripState) {
        val age = System.currentTimeMillis() - tripState.lastLocationTimestamp
        when {
            !tripState.isRecording -> "Ready"
            age > 10000 -> "Searching..."
            tripState.accuracyMeters > 25 -> "Low Accuracy (${tripState.accuracyMeters.toInt()}m)"
            else -> "Good (${tripState.accuracyMeters.toInt()}m)"
        }
    }

    val statusColor = when {
        status == "Ready" -> Color.Gray
        status == "Good" || status.startsWith("Good") -> Color.Green
        else -> Color.Red
    }

    if (showSaveDialog) {
        SaveTripDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.stopAndSaveTrip(name)
                showSaveDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
             // We can have a small top bar or just use the layout
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Chip
            SuggestionChip(
                onClick = { },
                label = { Text(status) },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Speed Display
            SpeedDisplay(
                mph = tripState.currentSpeedMph,
                kmh = tripState.currentSpeedKmh,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Chart
            SpeedChart(
                history = speedHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(vertical = 16.dp)
            )

            // Stats Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                maxItemsInEachRow = 3
            ) {
                val distMiles = tripState.distanceMeters * 0.000621371f
                InfoChip(label = "Distance", value = String.format("%.2f mi", distMiles))

                val elapsedSec = tripState.elapsedMs / 1000
                val h = elapsedSec / 3600
                val m = (elapsedSec % 3600) / 60
                val s = elapsedSec % 60
                InfoChip(label = "Time", value = String.format("%02d:%02d:%02d", h, m, s))

                InfoChip(label = "Max Speed", value = String.format("%.1f", tripState.maxSpeedMph))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                if (!tripState.isRecording) {
                    Button(
                        onClick = { viewModel.startTrip() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Trip")
                    }
                } else {
                    if (tripState.isPaused) {
                        Button(
                            onClick = { viewModel.resumeTrip() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Resume")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.pauseTrip() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)), // Orange
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pause")
                        }
                    }

                    Button(
                        onClick = { showSaveDialog = true }, // Stop prompts for save
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
            }

            // Reset button if paused?
            if (tripState.isPaused) {
                FilledTonalButton(onClick = { viewModel.resetTrip() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
            }

            // Navigation to other screens
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalButton(onClick = onNavigateToTrips) {
                    Text("History")
                }
                FilledTonalButton(onClick = onNavigateToSettings) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
fun SaveTripDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Trip") },
        text = {
            Column {
                Text("Enter a name for this trip:")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("My Trip") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = { onSave("") }) { // Empty name = default
                Text("Skip Name")
            }
        }
    )
}
