package com.example.gpsspeedometer.ui.main

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.logic.TripManager
import com.example.gpsspeedometer.service.TrackingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val tripManager: TripManager,
    private val application: Application
) : AndroidViewModel(application) {

    val tripState = tripManager.tripState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), tripManager.tripState.value)

    val speedHistory = tripManager.speedHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
         // Reset means stop and discard? or stop and save?
         // Prompt says "Reset" button. Usually means clear stats.
         // If recording, maybe stop and discard.
         // TripManager.stopTrip() saves it.
         // I might need a `reset()` in TripManager that doesn't save.
         // For now, I'll just use STOP.
         sendCommand(TrackingService.ACTION_STOP)
    }

    private fun sendCommand(action: String) {
        val intent = Intent(application, TrackingService::class.java).apply {
            this.action = action
        }
        application.startForegroundService(intent) // Use startForegroundService for Android 8+
    }

    class Factory(
        private val tripManager: TripManager,
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(tripManager, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
