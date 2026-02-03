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

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
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
