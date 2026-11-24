package com.example.gpsspeedometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One persisted speed limit lookup. Unlike the in-memory session buffer this table
 * survives process death, so the diagnostic CSV can be exported after a restart.
 */
@Entity(tableName = "speed_limit_diagnostics")
data class SpeedLimitDiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val currentSpeedMph: Float,
    val speedLimitMph: Int?,
    val speedLimitKmh: Int?,
    val roadName: String?,
    val status: String,
    val provider: String,
    val details: String
)
