package com.example.gpsspeedometer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_road_segments",
    indices = [Index(value = ["minLat", "maxLat", "minLng", "maxLng"])]
)
data class OfflineRoadSegmentEntity(
    @PrimaryKey val id: Long,
    val name: String?,
    val highwayType: String,
    val speedLimitMph: Int,
    val speedLimitKmh: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val geometrySerialized: String, // format: "lat,lng;lat,lng;..."
    val updatedAt: Long = System.currentTimeMillis()
)
