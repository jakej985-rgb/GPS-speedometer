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
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.gpsspeedometer.ui.components.InfoChip
import com.example.gpsspeedometer.ui.components.SpeedChart
import com.example.gpsspeedometer.ui.components.SpeedDisplay
import com.example.gpsspeedometer.ui.components.SpeedLimitSign

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedometerScreen(
    viewModel: MainViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrips: () -> Unit
) {
    val tripState by viewModel.tripState.collectAsState()
    val speedHistory by viewModel.speedHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val speedLimit by viewModel.speedLimitInfo.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var isHudMode by remember { mutableStateOf(false) }

    // Status Logic
    val status = remember(tripState) {
        val age = System.currentTimeMillis() - tripState.lastLocationTimestamp
        when {
            tripState.lastLocationTimestamp == 0L -> "Searching GPS..."
            age > 10000 -> "Searching..."
            tripState.accuracyMeters > 25 -> "Low Accuracy (${tripState.accuracyMeters.toInt()}m)"
            !tripState.isRecording -> "Ready (${tripState.accuracyMeters.toInt()}m)"
            tripState.isPaused -> "Paused"
            else -> "Tracking (${tripState.accuracyMeters.toInt()}m)"
        }
    }

    val statusColor = when {
        status.startsWith("Ready") -> MaterialTheme.colorScheme.secondary
        status.startsWith("Tracking") -> Color(0xFF4CAF50)
        status == "Paused" -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.error
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text(status, color = statusColor) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            labelColor = statusColor
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (settings.offlineMode) {
                        Spacer(modifier = Modifier.width(6.dp))
                        SuggestionChip(
                            onClick = { },
                            label = { Text("Offline", color = MaterialTheme.colorScheme.tertiary) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = "Open Live Map",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = { isHudMode = !isHudMode }) {
                        Icon(
                            Icons.Default.Flip,
                            contentDescription = "Toggle HUD Mode",
                            tint = if (isHudMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Speed Limit Comparison
            val currentSpeedToCompare = if (settings.useMph) tripState.currentSpeedMph else tripState.currentSpeedKmh
            val speedLimitToCompare = if (settings.useMph) speedLimit.speedLimitMph else speedLimit.speedLimitKmh
            val isSpeedLimitExceeded = speedLimitToCompare != null && currentSpeedToCompare > speedLimitToCompare
            val isUserAlertExceeded = currentSpeedToCompare > settings.speedAlertLimit
            val isAlert = isSpeedLimitExceeded || isUserAlertExceeded

            // Speed Display & Road Speed Limit Sign
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedDisplay(
                    mph = tripState.currentSpeedMph,
                    kmh = tripState.currentSpeedKmh,
                    useMph = settings.useMph,
                    isAlert = isAlert,
                    modifier = Modifier.graphicsLayer { scaleX = if (isHudMode) -1f else 1f }
                )

                if (!isHudMode) {
                    Spacer(modifier = Modifier.width(16.dp))
                    SpeedLimitSign(
                        speedLimitMph = speedLimit.speedLimitMph,
                        speedLimitKmh = speedLimit.speedLimitKmh,
                        useMph = settings.useMph,
                        isExceeded = isSpeedLimitExceeded,
                        roadName = speedLimit.roadName,
                        isLoading = speedLimit.isLoading
                    )
                }
            }

            // Chart
            SpeedChart(
                history = speedHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(vertical = 8.dp)
            )

            // Stats Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                maxItemsInEachRow = 4
            ) {
                if (settings.useMph) {
                    val distMiles = tripState.distanceMeters * 0.000621371f
                    InfoChip(label = "Distance", value = String.format("%.2f mi", distMiles))
                } else {
                    val distKm = tripState.distanceMeters / 1000f
                    InfoChip(label = "Distance", value = String.format("%.2f km", distKm))
                }

                val elapsedSec = tripState.elapsedMs / 1000
                val h = elapsedSec / 3600
                val m = (elapsedSec % 3600) / 60
                val s = elapsedSec % 60
                InfoChip(label = "Time", value = String.format("%02d:%02d:%02d", h, m, s))

                if (settings.useMph) {
                    InfoChip(label = "Avg Speed", value = String.format("%.1f", tripState.avgSpeedMph))
                    InfoChip(label = "Max Speed", value = String.format("%.1f", tripState.maxSpeedMph))
                } else {
                    InfoChip(label = "Avg Speed", value = String.format("%.1f", tripState.avgSpeedKmh))
                    InfoChip(label = "Max Speed", value = String.format("%.1f", tripState.maxSpeedMph * 1.60934f))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 12.dp)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pause")
                        }
                    }

                    Button(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
            }

            // Reset button if paused
            if (tripState.isPaused) {
                FilledTonalButton(
                    onClick = { viewModel.resetTrip() },
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset Trip")
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
                FilledTonalButton(onClick = onNavigateToMap) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Map")
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
            Button(onClick = { onSave("") }) {
                Text("Skip Name")
            }
        }
    )
}
