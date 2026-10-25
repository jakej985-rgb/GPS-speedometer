package com.example.gpsspeedometer.ui.main

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.data.AppSettings
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.domain.SpeedLimitInfo
import com.example.gpsspeedometer.location.LocationRepository
import com.example.gpsspeedometer.logic.TripManager
import com.example.gpsspeedometer.service.TrackingService
import com.example.gpsspeedometer.speedlimit.SpeedLimitRepository
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val tripManager: TripManager,
    private val locationRepository: LocationRepository,
    private val speedLimitRepository: SpeedLimitRepository,
    private val settingsRepository: SettingsRepository,
    private val application: Application
) : AndroidViewModel(application) {

    val tripState = tripManager.tripState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), tripManager.tripState.value)

    val speedHistory = tripManager.speedHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val speedLimitInfo: StateFlow<SpeedLimitInfo> = speedLimitRepository.speedLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpeedLimitInfo())

    private var foregroundLocationJob: Job? = null

    init {
        // Collect live location for speedometer when not recording in background service
        viewModelScope.launch {
            tripState
                .map { it.isRecording }
                .distinctUntilChanged()
                .collect { isRecording ->
                    if (!isRecording) {
                        startForegroundLocationUpdates()
                    } else {
                        stopForegroundLocationUpdates()
                    }
                }
        }

        // Query road speed limit whenever coordinates change significantly
        viewModelScope.launch {
            tripState.collect { state ->
                if (state.currentLat != 0.0 && state.currentLng != 0.0) {
                    speedLimitRepository.queryIfNeeded(
                        lat = state.currentLat,
                        lng = state.currentLng,
                        useMph = settings.value.useMph
                    )
                }
            }
        }
    }

    private fun startForegroundLocationUpdates() {
        if (foregroundLocationJob?.isActive == true) return
        foregroundLocationJob = viewModelScope.launch {
            try {
                val sampleRate = settings.value.sampleIntervalMs
                locationRepository.getLocationUpdates(sampleRate)
                    .catch { e -> Log.e("MainViewModel", "Error in location updates flow", e) }
                    .collect { location ->
                        tripManager.processLocation(location)
                    }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to start location collection", e)
            }
        }
    }

    private fun stopForegroundLocationUpdates() {
        foregroundLocationJob?.cancel()
        foregroundLocationJob = null
    }

    fun startTrip() {
        sendCommand(TrackingService.ACTION_START)
    }

    fun pauseTrip() {
        sendCommand(TrackingService.ACTION_PAUSE)
    }

    fun resumeTrip() {
        sendCommand(TrackingService.ACTION_RESUME)
    }

    fun stopAndSaveTrip(name: String) {
        if (name.isNotBlank()) {
            tripManager.updateCurrentTripName(name)
        }
        sendCommand(TrackingService.ACTION_STOP)
    }

    fun resetTrip() {
        sendCommand(TrackingService.ACTION_STOP)
    }

    private fun sendCommand(action: String) {
        try {
            val intent = Intent(application, TrackingService::class.java).apply {
                this.action = action
            }
            if (action == TrackingService.ACTION_START) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to send command $action to TrackingService", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopForegroundLocationUpdates()
    }

    class Factory(
        private val tripManager: TripManager,
        private val locationRepository: LocationRepository,
        private val speedLimitRepository: SpeedLimitRepository,
        private val settingsRepository: SettingsRepository,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(
                    tripManager,
                    locationRepository,
                    speedLimitRepository,
                    settingsRepository,
                    application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
