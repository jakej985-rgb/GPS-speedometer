package com.example.gpsspeedometer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineSpeedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<OfflineRoadSegmentEntity>)

    @Query("""
        SELECT * FROM offline_road_segments
        WHERE minLat <= :north AND maxLat >= :south
          AND minLng <= :east AND maxLng >= :west
    """)
    suspend fun getSegmentsInBoundingBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): List<OfflineRoadSegmentEntity>

    @Query("""
        SELECT * FROM offline_road_segments
        WHERE minLat <= :lat + :radius AND maxLat >= :lat - :radius
          AND minLng <= :lng + :radius AND maxLng >= :lng - :radius
        LIMIT 1
    """)
    suspend fun getSegmentNear(
        lat: Double,
        lng: Double,
        radius: Double = 0.001 // ~110m
    ): OfflineRoadSegmentEntity?

    @Query("SELECT COUNT(*) FROM offline_road_segments")
    suspend fun getSegmentCount(): Int

    @Query("DELETE FROM offline_road_segments")
    suspend fun clearAll()
}
