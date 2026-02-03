package com.example.gpsspeedometer.domain

data class LocationSample(
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val speedMps: Float,
    val accuracyMeters: Float,
    val bearing: Float
)

data class TripStats(
    val elapsedMs: Long = 0,
    val distanceMeters: Float = 0f,
    val maxSpeedMph: Float = 0f,
    val currentSpeedMph: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val accuracyMeters: Float = 0f,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val lastLocationTimestamp: Long = 0
)
