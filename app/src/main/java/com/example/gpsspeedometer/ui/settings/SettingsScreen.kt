package com.example.gpsspeedometer.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<GpsPreset?>(null) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset to Defaults?") },
            text = { Text("Are you sure you want to restore all speedometer, GPS filter, and display preferences to factory settings?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        selectedPreset = null
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. DISPLAY & UNITS CARD
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Display & Units",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        title = "Use MPH (Imperial)",
                        subtitle = if (settings.useMph) "Speeds in MPH, distances in miles" else "Speeds in KM/H, distances in kilometers",
                        checked = settings.useMph,
                        onCheckedChange = { viewModel.updateUseMph(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingSwitchRow(
                        title = "Keep Screen Awake",
                        subtitle = "Prevent device screen from turning off while speedometer or map is active",
                        checked = settings.keepScreenOn,
                        onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingSliderRow(
                        title = "Speed Alert Limit",
                        subtitle = "Visual warning sign appears when exceeding this limit",
                        value = settings.speedAlertLimit,
                        formattedValue = "${settings.speedAlertLimit.toInt()} ${if (settings.useMph) "MPH" else "KM/H"}",
                        range = 10f..200f,
                        onValueChangeFinished = { viewModel.updateSpeedAlertLimit(it) }
                    )
                }
            }

            // 2. TRACKING & BATTERY CARD
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tracking & Updates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        title = "Background Tracking",
                        subtitle = "Record trips and calculate stats when the app is minimized or the screen is locked",
                        checked = settings.backgroundTrackingEnabled,
                        onCheckedChange = { viewModel.updateBackgroundTracking(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    val intervalSec = settings.sampleIntervalMs / 1000f
                    val intervalLabel = if (settings.sampleIntervalMs < 1000L) {
                        "${settings.sampleIntervalMs} ms"
                    } else {
                        String.format(Locale.US, "%.1f s", intervalSec)
                    }

                    SettingSliderRow(
                        title = "GPS Update Frequency",
                        subtitle = "Time interval between GPS sensor polls (faster uses slightly more battery)",
                        value = settings.sampleIntervalMs.toFloat(),
                        formattedValue = intervalLabel,
                        range = 500f..5000f,
                        steps = 8,
                        onValueChangeFinished = { viewModel.updateSampleInterval(it.toLong()) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingSwitchRow(
                        title = "Offline Mode (Zero Network)",
                        subtitle = "Air-gap the app: blocks all outgoing internet requests (0 bytes). Uses local GPS hardware and cached map tiles only.",
                        checked = settings.offlineMode,
                        onCheckedChange = { viewModel.updateOfflineMode(it) }
                    )
                }
            }

            // 3. PRESETS CARD
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tuning Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Quickly configure GPS sampling and filtering parameters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedPreset == GpsPreset.BALANCED,
                            onClick = {
                                selectedPreset = GpsPreset.BALANCED
                                viewModel.applyPreset(GpsPreset.BALANCED)
                            },
                            label = { Text("Balanced") },
                            leadingIcon = if (selectedPreset == GpsPreset.BALANCED) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedPreset == GpsPreset.HIGH_PRECISION,
                            onClick = {
                                selectedPreset = GpsPreset.HIGH_PRECISION
                                viewModel.applyPreset(GpsPreset.HIGH_PRECISION)
                            },
                            label = { Text("High Precision") },
                            leadingIcon = if (selectedPreset == GpsPreset.HIGH_PRECISION) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedPreset == GpsPreset.BATTERY_SAVER,
                            onClick = {
                                selectedPreset = GpsPreset.BATTERY_SAVER
                                viewModel.applyPreset(GpsPreset.BATTERY_SAVER)
                            },
                            label = { Text("Battery Saver") },
                            leadingIcon = if (selectedPreset == GpsPreset.BATTERY_SAVER) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // 4. ADVANCED GPS TUNING (COLLAPSIBLE)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Advanced GPS Tuning",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (showAdvanced) "Tap to collapse advanced parameters" else "Tap to configure filtering, alpha, and thresholds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showAdvanced = !showAdvanced }) {
                            Icon(
                                if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showAdvanced) "Collapse" else "Expand"
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showAdvanced,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingSliderRow(
                                title = "Accuracy Threshold",
                                subtitle = "Ignore GPS samples with estimated radius error above this limit",
                                value = settings.accuracyThresholdMeters,
                                formattedValue = "${settings.accuracyThresholdMeters.toInt()} m",
                                range = 5f..100f,
                                onValueChangeFinished = {
                                    selectedPreset = null
                                    viewModel.updateAccuracyThreshold(it)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingSliderRow(
                                title = "Min Segment Distance",
                                subtitle = "Minimum displacement needed before adding distance to trip",
                                value = settings.minSegmentMeters,
                                formattedValue = "${settings.minSegmentMeters.toInt()} m",
                                range = 1f..50f,
                                onValueChangeFinished = {
                                    selectedPreset = null
                                    viewModel.updateMinSegmentMeters(it)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingSliderRow(
                                title = "EMA Smoothing Factor (Alpha)",
                                subtitle = "Higher reacts faster to acceleration; lower gives a smoother needle",
                                value = settings.emaAlpha,
                                formattedValue = String.format(Locale.US, "%.2f", settings.emaAlpha),
                                range = 0.05f..1.0f,
                                onValueChangeFinished = {
                                    selectedPreset = null
                                    viewModel.updateEmaAlpha(it)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingSliderRow(
                                title = "History Window Size",
                                subtitle = "Number of location samples kept in memory for statistics",
                                value = settings.historyWindowSamples.toFloat(),
                                formattedValue = "${settings.historyWindowSamples} samples",
                                range = 60f..3600f,
                                onValueChangeFinished = {
                                    selectedPreset = null
                                    viewModel.updateHistoryWindow(it.toInt())
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingSliderRow(
                                title = "Min Delta Time",
                                subtitle = "Minimum time difference between consecutive updates",
                                value = settings.minDeltaSec,
                                formattedValue = String.format(Locale.US, "%.1f s", settings.minDeltaSec),
                                range = 0.1f..2f,
                                onValueChangeFinished = {
                                    selectedPreset = null
                                    viewModel.updateMinDeltaSec(it)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingSliderRow(
                                title = "Max Delta Time",
                                subtitle = "Maximum time gap before resetting instantaneous speed",
                                value = settings.maxDeltaSec,
                                formattedValue = String.format(Locale.US, "%.1f s", settings.maxDeltaSec),
                                range = 2f..20f,
                                onValueChangeFinished = {
                                    selectedPreset = null
                                    viewModel.updateMaxDeltaSec(it)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingSliderRow(
                                title = "Max Plausible Speed",
                                subtitle = "Glitch rejection threshold for unrealistic speed spikes",
                                value = settings.maxPlausibleMph,
                                formattedValue = "${settings.maxPlausibleMph.toInt()} MPH",
                                range = 50f..350f,
                                onValueChangeFinished = {
                                    selectedPreset = null
                                    viewModel.updateMaxPlausibleMph(it)
                                }
                            )
                        }
                    }
                }
            }

            // 5. RESET TO DEFAULTS
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Defaults")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    subtitle: String,
    value: Float,
    formattedValue: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: (Float) -> Unit
) {
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = formattedValue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onValueChangeFinished(sliderPosition) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
