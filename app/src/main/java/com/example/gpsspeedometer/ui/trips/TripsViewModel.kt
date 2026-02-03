package com.example.gpsspeedometer.ui.trips

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.data.TripDao
import com.example.gpsspeedometer.data.TripEntity
import com.example.gpsspeedometer.data.TripPointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter

enum class ExportType { SUMMARY, POINTS }

class TripsViewModel(private val tripDao: TripDao) : ViewModel() {

    val trips = tripDao.getAllTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            tripDao.deleteTrip(trip)
        }
    }

    suspend fun getTrip(id: Long): TripEntity? {
        return tripDao.getTripById(id)
    }

    fun exportTrip(context: Context, tripId: Long, uri: Uri, type: ExportType) {
        viewModelScope.launch(Dispatchers.IO) {
            val trip = tripDao.getTripById(tripId) ?: return@launch

            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                        when (type) {
                            ExportType.SUMMARY -> {
                                writer.write("name,start_time,end_time,elapsed_s,distance_miles,distance_km,max_mph,avg_mph\n")
                                val elapsedSec = trip.elapsedMs / 1000f
                                val distMiles = trip.distanceMeters * 0.000621371f
                                val distKm = trip.distanceMeters / 1000f

                                writer.write("${trip.name},${trip.startTime},${trip.endTime},${elapsedSec},${distMiles},${distKm},${trip.maxSpeedMph},${trip.avgSpeedMph}\n")
                            }
                            ExportType.POINTS -> {
                                writer.write("timestamp,lat,lng,accuracy_m,speed_mps,raw_mph,smoothed_mph\n")
                                val points = tripDao.getTripPoints(tripId)
                                points.forEach { point ->
                                    writer.write("${point.timestamp},${point.lat},${point.lng},${point.accuracyMeters},${point.speedMps},${point.rawMph},${point.smoothedMph}\n")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class Factory(private val tripDao: TripDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripsViewModel::class.java)) {
                return TripsViewModel(tripDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
