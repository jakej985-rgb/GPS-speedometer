package com.example.gpsspeedometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val elapsedMs: Long,
    val distanceMeters: Float,
    val maxSpeedMph: Float,
    val avgSpeedMph: Float,
    val createdAt: Long = System.currentTimeMillis()
)
