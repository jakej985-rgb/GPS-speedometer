package com.example.gpsspeedometer.ui.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gpsspeedometer.data.TripEntity
import com.example.gpsspeedometer.ui.components.InfoChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    viewModel: TripsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var trip by remember { mutableStateOf<TripEntity?>(null) }

    LaunchedEffect(tripId) {
        trip = viewModel.getTrip(tripId)
    }

    val exportSummaryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { viewModel.exportTrip(context, tripId, it, ExportType.SUMMARY) }
        }
    )

    val exportPointsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { viewModel.exportTrip(context, tripId, it, ExportType.POINTS) }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "Trip Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        trip?.let {
                            viewModel.deleteTrip(it)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            trip?.let { t ->
                Text("Stats", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                // Using standard Row/Column if FlowRow is issue, but trying FlowRow logic manually
                // Since I don't want to rely on Accompanist which I didn't add to gradle.
                // Wait, I didn't add accompanist. I used ExperimentalLayoutApi FlowRow in Main.
                // I will use Column for simplicity or the same FlowRow from foundation if available.
                // I'll stick to simple Column with Rows.

                Row {
                   if (settings.useMph) {
                       InfoChip(label = "Distance", value = String.format("%.2f mi", t.distanceMeters * 0.000621371f))
                       InfoChip(label = "Max Speed", value = String.format("%.1f MPH", t.maxSpeedMph))
                   } else {
                       InfoChip(label = "Distance", value = String.format("%.2f km", t.distanceMeters / 1000f))
                       InfoChip(label = "Max Speed", value = String.format("%.1f KM/H", t.maxSpeedMph * 1.60934f))
                   }
                }
                Row {
                    val elapsedSec = t.elapsedMs / 1000
                    val h = elapsedSec / 3600
                    val m = (elapsedSec % 3600) / 60
                    val s = elapsedSec % 60
                    InfoChip(label = "Duration", value = String.format("%02d:%02d:%02d", h, m, s))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Export", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { exportSummaryLauncher.launch("${t.name}_summary.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Summary CSV")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { exportPointsLauncher.launch("${t.name}_points.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Points CSV")
                }
            }
        }
    }
}
