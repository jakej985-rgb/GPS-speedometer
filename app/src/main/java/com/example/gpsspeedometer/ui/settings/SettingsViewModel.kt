package com.example.gpsspeedometer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.data.AppSettings
import com.example.gpsspeedometer.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun updateAccuracyThreshold(value: Float) {
        viewModelScope.launch { repository.updateAccuracyThreshold(value) }
    }

    fun updateMinSegmentMeters(value: Float) {
        viewModelScope.launch { repository.updateMinSegmentMeters(value) }
    }

    fun updateEmaAlpha(value: Float) {
        viewModelScope.launch { repository.updateEmaAlpha(value) }
    }

    fun updateHistoryWindow(value: Int) {
        viewModelScope.launch { repository.updateHistoryWindow(value) }
    }

    fun updateMaxPlausibleMph(value: Float) {
        viewModelScope.launch { repository.updateMaxPlausibleMph(value) }
    }

    fun updateBackgroundTracking(enabled: Boolean) {
        viewModelScope.launch { repository.updateBackgroundTracking(enabled) }
    }

    fun updateSampleInterval(value: Long) {
        viewModelScope.launch { repository.updateSampleInterval(value) }
    }

    fun updateMinDeltaSec(value: Float) {
        viewModelScope.launch { repository.updateMinDeltaSec(value) }
    }

    fun updateMaxDeltaSec(value: Float) {
        viewModelScope.launch { repository.updateMaxDeltaSec(value) }
    }

    fun updateUseMph(value: Boolean) {
        viewModelScope.launch { repository.updateUseMph(value) }
    }

    fun updateSpeedAlertLimit(value: Float) {
        viewModelScope.launch { repository.updateSpeedAlertLimit(value) }
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { repository.updateKeepScreenOn(enabled) }
    }

    fun updateOfflineMode(enabled: Boolean) {
        viewModelScope.launch { repository.updateOfflineMode(enabled) }
    }

    fun updateOrientationLock(value: String) {
        viewModelScope.launch { repository.updateOrientationLock(value) }
    }

    fun updateDarkMode(value: String) {
        viewModelScope.launch { repository.updateDarkMode(value) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }

    fun applyPreset(preset: GpsPreset) {
        viewModelScope.launch {
            when (preset) {
                GpsPreset.BALANCED -> {
                    repository.updateSampleInterval(1000L)
                    repository.updateEmaAlpha(0.25f)
                    repository.updateAccuracyThreshold(25f)
                    repository.updateMinSegmentMeters(3f)
                    repository.updateMinDeltaSec(0.3f)
                    repository.updateMaxDeltaSec(10f)
                }
                GpsPreset.HIGH_PRECISION -> {
                    repository.updateSampleInterval(500L)
                    repository.updateEmaAlpha(0.40f)
                    repository.updateAccuracyThreshold(15f)
                    repository.updateMinSegmentMeters(1.5f)
                    repository.updateMinDeltaSec(0.2f)
                    repository.updateMaxDeltaSec(5f)
                }
                GpsPreset.BATTERY_SAVER -> {
                    repository.updateSampleInterval(2000L)
                    repository.updateEmaAlpha(0.20f)
                    repository.updateAccuracyThreshold(35f)
                    repository.updateMinSegmentMeters(5f)
                    repository.updateMinDeltaSec(0.5f)
                    repository.updateMaxDeltaSec(15f)
                }
            }
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

enum class GpsPreset {
    BALANCED,
    HIGH_PRECISION,
    BATTERY_SAVER
}
