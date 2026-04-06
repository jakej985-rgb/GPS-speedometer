package com.example.gpsspeedometer.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    onTripClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val trips by viewModel.trips.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(trips) { trip ->
                TripItem(
                    trip = trip,
                    useMph = settings.useMph,
                    onClick = { onTripClick(trip.id) }
                )
            }
        }
    }
}

@Composable
fun TripItem(
    trip: com.example.gpsspeedometer.data.TripEntity,
    useMph: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(trip.startTime))
    val distStr = if (useMph) {
        String.format("%.2f mi", trip.distanceMeters * 0.000621371f)
    } else {
        String.format("%.2f km", trip.distanceMeters / 1000f)
    }
    val maxSpeedStr = if (useMph) {
        String.format("Max: %.1f MPH", trip.maxSpeedMph)
    } else {
        String.format("Max: %.1f KM/H", trip.maxSpeedMph * 1.60934f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = trip.name, style = MaterialTheme.typography.titleMedium)
            Text(text = dateStr, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.padding(4.dp))
            Row {
                Text(text = distStr, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = maxSpeedStr, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
