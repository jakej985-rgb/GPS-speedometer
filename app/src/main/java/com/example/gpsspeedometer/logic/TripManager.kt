package com.example.gpsspeedometer.logic

import android.location.Location
import android.util.Log
import com.example.gpsspeedometer.data.AppSettings
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.data.TripDao
import com.example.gpsspeedometer.data.TripEntity
import com.example.gpsspeedometer.data.TripPointEntity
import com.example.gpsspeedometer.domain.LocationSample
import com.example.gpsspeedometer.domain.TripStats
import com.example.gpsspeedometer.speedlimit.SpeedLimitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

class TripManager(
    private val settingsRepository: SettingsRepository,
    private val tripDao: TripDao,
    private val scope: CoroutineScope,
    private val speedLimitRepository: SpeedLimitRepository? = null
) {

    private val _tripState = MutableStateFlow(TripStats())
    val tripState: StateFlow<TripStats> = _tripState.asStateFlow()

    private val _speedHistory = MutableStateFlow<List<Pair<Long, Float>>>(emptyList())
    val speedHistory: StateFlow<List<Pair<Long, Float>>> = _speedHistory.asStateFlow()

    @Volatile
    private var currentTripId: Long? = null
    private var lastValidLocation: LocationSample? = null
    private var lastSmoothedSpeedMph: Float = 0f

    // Wall-clock & location time tracking for accurate elapsed time accounting (including pauses)
    private var tripStartTime: Long = 0L
    private var firstLocationTimestamp: Long = 0L
    private var totalPausedDurationMs: Long = 0L
    private var lastPauseTimestamp: Long = 0L

    private val historyLock = Any()

    // Settings
    private var settings: AppSettings = AppSettings()

    init {
        settingsRepository.settingsFlow
            .onEach { settings = it }
            .launchIn(scope)

        // Observe speed limit changes and reflect in trip state
        speedLimitRepository?.speedLimitFlow
            ?.onEach { limitInfo ->
                _tripState.update {
                    it.copy(
                        speedLimitMph = limitInfo.speedLimitMph,
                        roadName = limitInfo.roadName
                    )
                }
            }
            ?.launchIn(scope)
    }

    fun startTrip() {
        if (_tripState.value.isRecording) return

        val now = System.currentTimeMillis()
        tripStartTime = now
        firstLocationTimestamp = 0L
        totalPausedDurationMs = 0L
        lastPauseTimestamp = 0L
        lastValidLocation = null
        lastSmoothedSpeedMph = 0f
        synchronized(historyLock) {
            _speedHistory.value = emptyList()
        }

        scope.launch {
            try {
                val trip = TripEntity(
                    name = "Trip $now",
                    startTime = now,
                    endTime = 0,
                    elapsedMs = 0,
                    distanceMeters = 0f,
                    maxSpeedMph = 0f,
                    avgSpeedMph = 0f
                )
                val id = tripDao.insertTrip(trip)
                currentTripId = id
                _tripState.update {
                    it.copy(
                        isRecording = true,
                        isPaused = false,
                        elapsedMs = 0,
                        distanceMeters = 0f,
                        maxSpeedMph = 0f,
                        avgSpeedMph = 0f,
                        avgSpeedKmh = 0f
                    )
                }
            } catch (e: Exception) {
                Log.e("TripManager", "Error inserting trip to database", e)
            }
        }
    }

    fun pauseTrip() {
        if (!_tripState.value.isRecording || _tripState.value.isPaused) return
        lastPauseTimestamp = System.currentTimeMillis()
        _tripState.update { it.copy(isPaused = true) }
    }

    fun resumeTrip() {
        if (!_tripState.value.isRecording || !_tripState.value.isPaused) return
        if (lastPauseTimestamp > 0L) {
            totalPausedDurationMs += (System.currentTimeMillis() - lastPauseTimestamp)
            lastPauseTimestamp = 0L
        }
        _tripState.update { it.copy(isPaused = false) }
        lastValidLocation = null // Reset last location to avoid huge jumps/segments after pause
    }

    fun updateCurrentTripName(name: String) {
        val tripId = currentTripId ?: return
        scope.launch {
            try {
                val trip = tripDao.getTripById(tripId)
                if (trip != null) {
                    tripDao.updateTrip(trip.copy(name = name))
                }
            } catch (e: Exception) {
                Log.e("TripManager", "Error updating trip name", e)
            }
        }
    }

    fun stopTrip() {
        if (!_tripState.value.isRecording) return

        val now = System.currentTimeMillis()
        if (_tripState.value.isPaused && lastPauseTimestamp > 0L) {
            totalPausedDurationMs += (now - lastPauseTimestamp)
            lastPauseTimestamp = 0L
        }
        val finalElapsedMs = if (tripStartTime > 0L) {
            max(0L, now - tripStartTime - totalPausedDurationMs)
        } else {
            _tripState.value.elapsedMs
        }

        val finalState = _tripState.value.copy(elapsedMs = finalElapsedMs)
        val tripId = currentTripId

        // Calculate proper average speed: distance in miles / elapsed hours
        val elapsedHours = finalElapsedMs / 3600000f
        val distanceMiles = finalState.distanceMeters * 0.000621371f
        val calculatedAvgMph = if (elapsedHours > 0.0001f) (distanceMiles / elapsedHours) else 0f
        val calculatedAvgKmh = calculatedAvgMph * 1.609344f
        _tripState.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                elapsedMs = finalElapsedMs,
                avgSpeedMph = calculatedAvgMph,
                avgSpeedKmh = calculatedAvgKmh
            )
        }
        currentTripId = null
        tripStartTime = 0L
        firstLocationTimestamp = 0L
        totalPausedDurationMs = 0L

        scope.launch {
            try {
                if (tripId != null) {
                    val trip = tripDao.getTripById(tripId)
                    if (trip != null) {
                        val updatedTrip = trip.copy(
                            endTime = now,
                            elapsedMs = finalElapsedMs,
                            distanceMeters = finalState.distanceMeters,
                            maxSpeedMph = finalState.maxSpeedMph,
                            avgSpeedMph = calculatedAvgMph
                        )
                        tripDao.updateTrip(updatedTrip)
                    }
                }
            } catch (e: Exception) {
                Log.e("TripManager", "Error updating trip on stop", e)
            }
        }
    }

    fun processLocation(location: LocationSample) {
        // Always update current speed/accuracy for display (Live Speed)
        val rawMph = location.speedMps * 2.23694f
        val smoothedMph = calculateSmoothedSpeed(rawMph)

        // Update history (Ring buffer)
        updateHistory(location.timestamp, smoothedMph)

        // Asynchronously query road speed limit on valid coordinates
        if (location.lat != 0.0 && location.lng != 0.0) {
            scope.launch {
                try {
                    speedLimitRepository?.queryIfNeeded(
                        lat = location.lat,
                        lng = location.lng,
                        useMph = settings.useMph,
                        offlineMode = settings.offlineMode,
                        currentSpeedMph = tripState.value.currentSpeedMph
                    )
                } catch (e: Exception) {
                    Log.e("TripManager", "Error querying speed limit in processLocation", e)
                }
            }
        }

        val currentSpeedLimit = speedLimitRepository?.speedLimitFlow?.value
        val isRecording = _tripState.value.isRecording
        val isPaused = _tripState.value.isPaused

        if (!isRecording || isPaused) {
            // Live view stats only
            _tripState.update {
                it.copy(
                    currentSpeedMph = smoothedMph,
                    currentSpeedKmh = location.speedMps * 3.6f,
                    accuracyMeters = location.accuracyMeters,
                    lastLocationTimestamp = location.timestamp,
                    currentLat = location.lat,
                    currentLng = location.lng,
                    bearing = location.bearing,
                    speedLimitMph = currentSpeedLimit?.speedLimitMph ?: it.speedLimitMph,
                    roadName = currentSpeedLimit?.roadName ?: it.roadName
                )
            }
            return
        }

        // Always track max speed on accurate GPS samples
        val isAccurate = location.accuracyMeters <= settings.accuracyThresholdMeters
        val newMaxSpeed = if (isAccurate) max(_tripState.value.maxSpeedMph, smoothedMph) else _tripState.value.maxSpeedMph

        // Calculate elapsed time (supporting wall-clock and location timestamps)
        if (firstLocationTimestamp == 0L && location.timestamp > 0L) {
            firstLocationTimestamp = location.timestamp
        }
        val wallClockElapsed = if (tripStartTime > 0L) {
            max(0L, System.currentTimeMillis() - tripStartTime - totalPausedDurationMs)
        } else {
            _tripState.value.elapsedMs
        }
        val locationElapsed = if (firstLocationTimestamp > 0L && location.timestamp >= firstLocationTimestamp) {
            max(0L, location.timestamp - firstLocationTimestamp)
        } else {
            0L
        }
        val currentElapsed = max(wallClockElapsed, locationElapsed)

        var newDistance = _tripState.value.distanceMeters
        var shouldPersistPoint = false

        if (isAccurate) {
            if (lastValidLocation == null) {
                lastValidLocation = location
                shouldPersistPoint = true
            } else {
                val deltaSec = (location.timestamp - lastValidLocation!!.timestamp) / 1000f

                if (deltaSec >= settings.minDeltaSec) {
                    if (deltaSec > settings.maxDeltaSec) {
                        // Large time gap (e.g. stopped for a while, GPS temporarily lost, or service restart):
                        // Advance lastValidLocation to resume tracking without adding an artificial leap distance
                        lastValidLocation = location
                        shouldPersistPoint = true
                    } else {
                        // Normal sequential update
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            lastValidLocation!!.lat, lastValidLocation!!.lng,
                            location.lat, location.lng,
                            results
                        )
                        val dist = results[0]

                        // Spike plausibility check
                        val impliedMph = if (deltaSec > 0) (dist / deltaSec) * 2.23694f else 0f
                        if (impliedMph <= settings.maxPlausibleMph) {
                            if (dist >= settings.minSegmentMeters) {
                                newDistance += dist
                                shouldPersistPoint = true
                            }
                            // Keep lastValidLocation fresh (even if stationary < minSegmentMeters)
                            // to prevent deltaSec from building up beyond maxDeltaSec and deadlocking
                            lastValidLocation = location
                        }
                    }
                }
            }
        }

        val elapsedHours = currentElapsed / 3600000f
        val distanceMiles = newDistance * 0.000621371f
        val liveAvgMph = if (elapsedHours > 0.0001f) (distanceMiles / elapsedHours) else 0f
        val liveAvgKmh = liveAvgMph * 1.609344f

        _tripState.update {
            it.copy(
                distanceMeters = newDistance,
                maxSpeedMph = newMaxSpeed,
                avgSpeedMph = liveAvgMph,
                avgSpeedKmh = liveAvgKmh,
                currentSpeedMph = smoothedMph,
                currentSpeedKmh = location.speedMps * 3.6f,
                accuracyMeters = location.accuracyMeters,
                elapsedMs = currentElapsed,
                lastLocationTimestamp = location.timestamp,
                currentLat = location.lat,
                currentLng = location.lng,
                bearing = location.bearing,
                speedLimitMph = currentSpeedLimit?.speedLimitMph ?: it.speedLimitMph,
                roadName = currentSpeedLimit?.roadName ?: it.roadName
            )
        }

        if (shouldPersistPoint) {
            persistPoint(location, rawMph, smoothedMph, currentSpeedLimit?.speedLimitMph)
        }
    }

    private fun calculateSmoothedSpeed(rawMph: Float): Float {
        val alpha = settings.emaAlpha
        lastSmoothedSpeedMph = alpha * rawMph + (1 - alpha) * lastSmoothedSpeedMph
        // If speed is very low, snap to 0 to avoid drift
        if (lastSmoothedSpeedMph < 0.5f) lastSmoothedSpeedMph = 0f
        return lastSmoothedSpeedMph
    }

    private fun updateHistory(timestamp: Long, mph: Float) {
        synchronized(historyLock) {
            val currentList = _speedHistory.value.toMutableList()
            currentList.add(timestamp to mph)
            while (currentList.size > settings.historyWindowSamples && currentList.isNotEmpty()) {
                currentList.removeAt(0)
            }
            _speedHistory.value = currentList
        }
    }

    private fun persistPoint(
        location: LocationSample,
        rawMph: Float,
        smoothedMph: Float,
        speedLimitMph: Int?
    ) {
        val tripId = currentTripId ?: return
        scope.launch {
            try {
                tripDao.insertTripPoint(
                    TripPointEntity(
                        tripId = tripId,
                        timestamp = location.timestamp,
                        lat = location.lat,
                        lng = location.lng,
                        accuracyMeters = location.accuracyMeters,
                        speedMps = location.speedMps,
                        rawMph = rawMph,
                        smoothedMph = smoothedMph,
                        speedLimitMph = speedLimitMph
                    )
                )
            } catch (e: Exception) {
                Log.e("TripManager", "Error inserting trip point", e)
            }
        }
    }
}
