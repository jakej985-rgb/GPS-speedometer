package com.example.gpsspeedometer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Long): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripPoint(point: TripPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripPoints(points: List<TripPointEntity>)

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getTripPoints(tripId: Long): List<TripPointEntity>
}
