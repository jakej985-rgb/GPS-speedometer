package com.example.gpsspeedometer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
        ) {
            SettingSlider(
                label = "Accuracy Threshold (Meters)",
                value = settings.accuracyThresholdMeters,
                range = 5f..100f,
                onValueChange = { viewModel.updateAccuracyThreshold(it) }
            )

            SettingSlider(
                label = "Min Segment Distance (Meters)",
                value = settings.minSegmentMeters,
                range = 1f..50f,
                onValueChange = { viewModel.updateMinSegmentMeters(it) }
            )

            SettingSlider(
                label = "Smoothing Factor (Alpha)",
                value = settings.emaAlpha,
                range = 0.01f..1.0f,
                onValueChange = { viewModel.updateEmaAlpha(it) }
            )

            SettingSlider(
                label = "History Window (Samples)",
                value = settings.historyWindowSamples.toFloat(),
                range = 60f..3600f,
                steps = 0,
                onValueChange = { viewModel.updateHistoryWindow(it.toInt()) }
            )

            SettingSlider(
                label = "Max Plausible Speed (MPH)",
                value = settings.maxPlausibleMph,
                range = 50f..500f,
                onValueChange = { viewModel.updateMaxPlausibleMph(it) }
            )

            SettingSlider(
                label = "Sample Interval (ms)",
                value = settings.sampleIntervalMs.toFloat(),
                range = 500f..10000f,
                steps = 19, // 500ms steps
                onValueChange = { viewModel.updateSampleInterval(it.toLong()) }
            )

            SettingSlider(
                label = "Min Delta Time (sec)",
                value = settings.minDeltaSec,
                range = 0.1f..2f,
                onValueChange = { viewModel.updateMinDeltaSec(it) }
            )

            SettingSlider(
                label = "Max Delta Time (sec)",
                value = settings.maxDeltaSec,
                range = 2f..20f,
                onValueChange = { viewModel.updateMaxDeltaSec(it) }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Background Tracking", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.backgroundTrackingEnabled,
                    onCheckedChange = { viewModel.updateBackgroundTracking(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Defaults")
            }
        }
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = "$label: ${String.format("%.2f", value)}")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
