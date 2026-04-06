package com.example.gpsspeedometer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val accuracyThresholdMeters: Float = 25f,
    val minSegmentMeters: Float = 3f,
    val emaAlpha: Float = 0.25f,
    val historyWindowSamples: Int = 600,
    val maxPlausibleMph: Float = 200f,
    val backgroundTrackingEnabled: Boolean = true,
    val sampleIntervalMs: Long = 1000L,
    val minDeltaSec: Float = 0.3f,
    val maxDeltaSec: Float = 10f,
    val useMph: Boolean = true,
    val speedAlertLimit: Float = 80f
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val ACCURACY_THRESHOLD = floatPreferencesKey("accuracy_threshold")
        val MIN_SEGMENT_METERS = floatPreferencesKey("min_segment_meters")
        val EMA_ALPHA = floatPreferencesKey("ema_alpha")
        val HISTORY_WINDOW = intPreferencesKey("history_window")
        val MAX_PLAUSIBLE_MPH = floatPreferencesKey("max_plausible_mph")
        val BACKGROUND_TRACKING = booleanPreferencesKey("background_tracking")
        val SAMPLE_INTERVAL = longPreferencesKey("sample_interval")
        val MIN_DELTA_SEC = floatPreferencesKey("min_delta_sec")
        val MAX_DELTA_SEC = floatPreferencesKey("max_delta_sec")
        val USE_MPH = booleanPreferencesKey("use_mph")
        val SPEED_ALERT_LIMIT = floatPreferencesKey("speed_alert_limit")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .map { preferences ->
            AppSettings(
                accuracyThresholdMeters = preferences[PreferencesKeys.ACCURACY_THRESHOLD] ?: 25f,
                minSegmentMeters = preferences[PreferencesKeys.MIN_SEGMENT_METERS] ?: 3f,
                emaAlpha = preferences[PreferencesKeys.EMA_ALPHA] ?: 0.25f,
                historyWindowSamples = preferences[PreferencesKeys.HISTORY_WINDOW] ?: 600,
                maxPlausibleMph = preferences[PreferencesKeys.MAX_PLAUSIBLE_MPH] ?: 200f,
                backgroundTrackingEnabled = preferences[PreferencesKeys.BACKGROUND_TRACKING] ?: true,
                sampleIntervalMs = preferences[PreferencesKeys.SAMPLE_INTERVAL] ?: 1000L,
                minDeltaSec = preferences[PreferencesKeys.MIN_DELTA_SEC] ?: 0.3f,
                maxDeltaSec = preferences[PreferencesKeys.MAX_DELTA_SEC] ?: 10f,
                useMph = preferences[PreferencesKeys.USE_MPH] ?: true,
                speedAlertLimit = preferences[PreferencesKeys.SPEED_ALERT_LIMIT] ?: 80f
            )
        }

    suspend fun updateAccuracyThreshold(value: Float) {
        dataStore.edit { it[PreferencesKeys.ACCURACY_THRESHOLD] = value }
    }

    suspend fun updateMinSegmentMeters(value: Float) {
        dataStore.edit { it[PreferencesKeys.MIN_SEGMENT_METERS] = value }
    }

    suspend fun updateEmaAlpha(value: Float) {
        dataStore.edit { it[PreferencesKeys.EMA_ALPHA] = value }
    }

    suspend fun updateHistoryWindow(value: Int) {
        dataStore.edit { it[PreferencesKeys.HISTORY_WINDOW] = value }
    }

    suspend fun updateMaxPlausibleMph(value: Float) {
        dataStore.edit { it[PreferencesKeys.MAX_PLAUSIBLE_MPH] = value }
    }

    suspend fun updateBackgroundTracking(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.BACKGROUND_TRACKING] = enabled }
    }

    suspend fun updateSampleInterval(intervalMs: Long) {
        dataStore.edit { it[PreferencesKeys.SAMPLE_INTERVAL] = intervalMs }
    }

    suspend fun updateMinDeltaSec(value: Float) {
        dataStore.edit { it[PreferencesKeys.MIN_DELTA_SEC] = value }
    }

    suspend fun updateMaxDeltaSec(value: Float) {
        dataStore.edit { it[PreferencesKeys.MAX_DELTA_SEC] = value }
    }

    suspend fun updateUseMph(value: Boolean) {
        dataStore.edit { it[PreferencesKeys.USE_MPH] = value }
    }

    suspend fun updateSpeedAlertLimit(value: Float) {
        dataStore.edit { it[PreferencesKeys.SPEED_ALERT_LIMIT] = value }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
