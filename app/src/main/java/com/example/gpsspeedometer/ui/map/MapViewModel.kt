package com.example.gpsspeedometer.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.data.AppSettings
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.data.TripDao
import com.example.gpsspeedometer.data.TripEntity
import com.example.gpsspeedometer.data.TripPointEntity
import com.example.gpsspeedometer.domain.SpeedLimitInfo
import com.example.gpsspeedometer.domain.TripStats
import com.example.gpsspeedometer.logic.TripManager
import com.example.gpsspeedometer.speedlimit.RoadSpeedSegment
import com.example.gpsspeedometer.speedlimit.SpeedLimitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(
    private val tripManager: TripManager,
    private val speedLimitRepository: SpeedLimitRepository,
    private val settingsRepository: SettingsRepository,
    private val tripDao: TripDao,
    private val pastTripId: Long? = null
) : ViewModel() {

    val tripState: StateFlow<TripStats> = tripManager.tripState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), tripManager.tripState.value)

    val speedLimitInfo: StateFlow<SpeedLimitInfo> = speedLimitRepository.speedLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpeedLimitInfo())

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _pastTripPoints = MutableStateFlow<List<TripPointEntity>>(emptyList())
    val pastTripPoints: StateFlow<List<TripPointEntity>> = _pastTripPoints.asStateFlow()

    private val _pastTrip = MutableStateFlow<TripEntity?>(null)
    val pastTrip: StateFlow<TripEntity?> = _pastTrip.asStateFlow()

    private val _showRoadSpeedLimits = MutableStateFlow(true)
    val showRoadSpeedLimits: StateFlow<Boolean> = _showRoadSpeedLimits.asStateFlow()

    private val _roadSegments = MutableStateFlow<List<RoadSpeedSegment>>(emptyList())
    val roadSegments: StateFlow<List<RoadSpeedSegment>> = _roadSegments.asStateFlow()

    private var viewportJob: Job? = null

    init {
        if (pastTripId != null && pastTripId > 0L) {
            viewModelScope.launch {
                _pastTrip.value = tripDao.getTripById(pastTripId)
                _pastTripPoints.value = tripDao.getTripPoints(pastTripId)
            }
        }
    }

    fun toggleRoadSpeedLimits(enabled: Boolean) {
        _showRoadSpeedLimits.value = enabled
        if (!enabled) {
            _roadSegments.value = emptyList()
        }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateOfflineMode(enabled)
        }
    }

    fun onViewportChanged(south: Double, west: Double, north: Double, east: Double, zoomLevel: Double) {
        if (!_showRoadSpeedLimits.value) return
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(700L) // Debounce panning/zooming
            val segments = speedLimitRepository.fetchRoadSpeedSegments(
                south = south,
                west = west,
                north = north,
                east = east,
                zoomLevel = zoomLevel,
                useMph = settings.value.useMph,
                offlineMode = settings.value.offlineMode
            )
            _roadSegments.value = segments
        }
    }

    class Factory(
        private val tripManager: TripManager,
        private val speedLimitRepository: SpeedLimitRepository,
        private val settingsRepository: SettingsRepository,
        private val tripDao: TripDao,
        private val pastTripId: Long? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
                return MapViewModel(
                    tripManager,
                    speedLimitRepository,
                    settingsRepository,
                    tripDao,
                    pastTripId
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
