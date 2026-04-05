package com.example.gpsspeedometer.logic

import android.location.Location
import com.example.gpsspeedometer.data.AppSettings
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.data.TripDao
import com.example.gpsspeedometer.data.TripEntity
import com.example.gpsspeedometer.data.TripPointEntity
import com.example.gpsspeedometer.domain.LocationSample
import com.example.gpsspeedometer.domain.TripStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val scope: CoroutineScope
) {

    private val _tripState = MutableStateFlow(TripStats())
    val tripState: StateFlow<TripStats> = _tripState.asStateFlow()

    private val _speedHistory = MutableStateFlow<List<Pair<Long, Float>>>(emptyList())
    val speedHistory: StateFlow<List<Pair<Long, Float>>> = _speedHistory.asStateFlow()

    private var currentTripId: Long? = null
    private var lastValidLocation: LocationSample? = null
    private var lastSmoothedSpeedMph: Float = 0f

    // Settings
    private var settings: AppSettings = AppSettings()

    init {
        settingsRepository.settingsFlow
            .onEach { settings = it }
            .launchIn(scope)
    }

    fun startTrip() {
        if (_tripState.value.isRecording) return

        scope.launch {
            val trip = TripEntity(
                name = "Trip ${System.currentTimeMillis()}", // Placeholder name
                startTime = System.currentTimeMillis(),
                endTime = 0,
                elapsedMs = 0,
                distanceMeters = 0f,
                maxSpeedMph = 0f,
                avgSpeedMph = 0f
            )
            currentTripId = tripDao.insertTrip(trip)
            _tripState.update { it.copy(isRecording = true, isPaused = false, elapsedMs = 0, distanceMeters = 0f, maxSpeedMph = 0f) }

            // Reset state
            lastValidLocation = null
            lastSmoothedSpeedMph = 0f
            _speedHistory.value = emptyList()
        }
    }

    fun pauseTrip() {
        _tripState.update { it.copy(isPaused = true) }
    }

    fun resumeTrip() {
        _tripState.update { it.copy(isPaused = false) }
        lastValidLocation = null // Reset last location to avoid huge jumps/segments after pause
    }

    fun updateCurrentTripName(name: String) {
        val tripId = currentTripId ?: return
        scope.launch {
            val trip = tripDao.getTripById(tripId)
            if (trip != null) {
                tripDao.updateTrip(trip.copy(name = name))
            }
        }
    }

    fun stopTrip() {
        if (!_tripState.value.isRecording) return

        val finalState = _tripState.value
        val tripId = currentTripId

        scope.launch {
            if (tripId != null) {
                val trip = tripDao.getTripById(tripId)
                if (trip != null) {
                    val avgSpeedMph = if (finalState.elapsedMs > 0) {
                        (finalState.distanceMeters / (finalState.elapsedMs / 1000f)) * 2.23694f
                    } else {
                        0f
                    }
                    val updatedTrip = trip.copy(
                        endTime = System.currentTimeMillis(),
                        elapsedMs = finalState.elapsedMs,
                        distanceMeters = finalState.distanceMeters,
                        maxSpeedMph = finalState.maxSpeedMph,
                        avgSpeedMph = avgSpeedMph
                    )
                    tripDao.updateTrip(updatedTrip)
                }
            }
        }

        _tripState.update { it.copy(isRecording = false, isPaused = false) }
        currentTripId = null
    }

    fun processLocation(location: LocationSample) {
        // Always update current speed/accuracy for display (Live Speed)
        // Convert speed from m/s to mph
        val rawMph = location.speedMps * 2.23694f
        val smoothedMph = calculateSmoothedSpeed(rawMph)

        // Update history (Ring buffer)
        updateHistory(location.timestamp, smoothedMph)

        if (!_tripState.value.isRecording || _tripState.value.isPaused) {
            // just update live view stats
            _tripState.update {
                it.copy(
                    currentSpeedMph = smoothedMph,
                    currentSpeedKmh = location.speedMps * 3.6f,
                    accuracyMeters = location.accuracyMeters,
                    lastLocationTimestamp = location.timestamp
                )
            }
            return
        }

        // Filtering logic for distance/accumulation
        if (isValidForAccumulation(location)) {
            val dist = if (lastValidLocation != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    lastValidLocation!!.lat, lastValidLocation!!.lng,
                    location.lat, location.lng,
                    results
                )
                results[0]
            } else {
                0f
            }

            // Additional sanity check on distance/speed
            // If distance implies speed > maxPlausible, ignore
            val timeDeltaSec = if (lastValidLocation != null) (location.timestamp - lastValidLocation!!.timestamp) / 1000f else 0f
            val impliedSpeedMps = if (timeDeltaSec > 0) dist / timeDeltaSec else 0f
            val impliedMph = impliedSpeedMps * 2.23694f

            if (timeDeltaSec > 0 && impliedMph > settings.maxPlausibleMph && location.accuracyMeters > 10) {
                // Ignore as outlier unless accuracy is very high
            } else {
                // Accumulate
                val newDistance = _tripState.value.distanceMeters + dist
                val newMaxSpeed = max(_tripState.value.maxSpeedMph, smoothedMph)

                // Update elapsed time properly
                // Simple approach: current time - start time - pause duration.
                // Or accumulate elapsed time. Here we just take delta from start if not complex.
                // But for robust implementation with pauses, we should accumulate time.
                // Since I don't track pause duration perfectly here, I'll rely on delta.
                // For now, let's assume elapsed is updated by a timer or simple delta accumulation?
                // Actually, `elapsedMs` in state is usually strictly "time moving" or "total duration"?
                // Let's stick to "Duration since start" minus "Pause duration".
                // I'll leave elapsed calculation to the UI or a separate ticker, OR accumulate deltas here.
                // Let's accumulate deltas between locations for "Moving Time"?
                // The prompt says "elapsed time" while running.
                // I will update elapsed time relative to start time in `TripStats` update via a separate ticker or just delta.
                // Let's rely on simple (now - start) - pauseOffsets.
                // But simpler: just add timeDelta if valid.

                val currentElapsed = _tripState.value.elapsedMs + (timeDeltaSec * 1000).toLong()

                _tripState.update {
                    it.copy(
                        distanceMeters = newDistance,
                        maxSpeedMph = newMaxSpeed,
                        currentSpeedMph = smoothedMph,
                        currentSpeedKmh = location.speedMps * 3.6f,
                        accuracyMeters = location.accuracyMeters,
                        elapsedMs = currentElapsed,
                        lastLocationTimestamp = location.timestamp
                    )
                }

                // Persist Point
                persistPoint(location, rawMph, smoothedMph)

                lastValidLocation = location
            }
        } else {
             // Just update speed display
             _tripState.update {
                it.copy(
                    currentSpeedMph = smoothedMph,
                    currentSpeedKmh = location.speedMps * 3.6f,
                    accuracyMeters = location.accuracyMeters,
                    lastLocationTimestamp = location.timestamp
                )
             }
        }
    }

    private fun calculateSmoothedSpeed(rawMph: Float): Float {
        // EMA
        val alpha = settings.emaAlpha
        lastSmoothedSpeedMph = alpha * rawMph + (1 - alpha) * lastSmoothedSpeedMph
        // If speed is very low, snap to 0 to avoid drift
        if (lastSmoothedSpeedMph < 0.5f) lastSmoothedSpeedMph = 0f
        return lastSmoothedSpeedMph
    }

    private fun isValidForAccumulation(location: LocationSample): Boolean {
        // Accuracy check
        if (location.accuracyMeters > settings.accuracyThresholdMeters) return false

        // Time Delta check
        if (lastValidLocation != null) {
            val deltaSec = (location.timestamp - lastValidLocation!!.timestamp) / 1000f
            if (deltaSec < settings.minDeltaSec || deltaSec > settings.maxDeltaSec) return false
        }

        // Min Segment check
        if (lastValidLocation != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastValidLocation!!.lat, lastValidLocation!!.lng,
                location.lat, location.lng,
                results
            )
            if (results[0] < settings.minSegmentMeters) return false
        }

        return true
    }

    private fun updateHistory(timestamp: Long, mph: Float) {
        val currentList = _speedHistory.value.toMutableList()
        currentList.add(timestamp to mph)
        // Keep window size
        if (currentList.size > settings.historyWindowSamples) {
            currentList.removeAt(0)
        }
        _speedHistory.value = currentList
    }

    private fun persistPoint(location: LocationSample, rawMph: Float, smoothedMph: Float) {
        val tripId = currentTripId ?: return
        scope.launch {
            tripDao.insertTripPoint(
                TripPointEntity(
                    tripId = tripId,
                    timestamp = location.timestamp,
                    lat = location.lat,
                    lng = location.lng,
                    accuracyMeters = location.accuracyMeters,
                    speedMps = location.speedMps,
                    rawMph = rawMph,
                    smoothedMph = smoothedMph
                )
            )
        }
    }
}
