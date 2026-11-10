package com.example.gpsspeedometer.ui.trips

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.data.AppSettings
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.data.TripDao
import com.example.gpsspeedometer.data.TripEntity
import com.example.gpsspeedometer.data.TripPointEntity
import com.example.gpsspeedometer.speedlimit.SpeedLimitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ExportType { SUMMARY, POINTS, GPX, DIAGNOSTIC }

class TripsViewModel(
    private val tripDao: TripDao,
    private val settingsRepository: SettingsRepository,
    private val speedLimitRepository: SpeedLimitRepository? = null
) : ViewModel() {

    val trips = tripDao.getAllTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _exportEvent = MutableSharedFlow<String>()
    val exportEvent: SharedFlow<String> = _exportEvent.asSharedFlow()

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            tripDao.deleteTrip(trip)
        }
    }

    suspend fun getTrip(id: Long): TripEntity? {
        return tripDao.getTripById(id)
    }

    fun exportDiagnosticLog(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    speedLimitRepository?.exportDiagnosticCsv(outputStream)
                }
                _exportEvent.emit("Speed diagnostic log exported successfully")
            } catch (e: Exception) {
                _exportEvent.emit("Export failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun exportTrip(context: Context, tripId: Long, uri: Uri, type: ExportType) {
        viewModelScope.launch(Dispatchers.IO) {
            if (type == ExportType.DIAGNOSTIC) {
                exportDiagnosticLog(context, uri)
                return@launch
            }

            val trip = tripDao.getTripById(tripId)
            if (trip == null) {
                _exportEvent.emit("Export failed: Trip not found")
                return@launch
            }

            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                        when (type) {
                            ExportType.SUMMARY -> {
                                writer.write("name,start_time,end_time,elapsed_s,distance_miles,distance_km,max_mph,avg_mph\n")
                                val elapsedSec = trip.elapsedMs / 1000f
                                val distMiles = trip.distanceMeters * 0.000621371f
                                val distKm = trip.distanceMeters / 1000f
                                val escapedName = escapeCsv(trip.name)

                                writer.write("$escapedName,${trip.startTime},${trip.endTime},$elapsedSec,$distMiles,$distKm,${trip.maxSpeedMph},${trip.avgSpeedMph}\n")
                            }
                            ExportType.POINTS -> {
                                writer.write("timestamp,lat,lng,accuracy_m,speed_mps,raw_mph,smoothed_mph\n")
                                val points = tripDao.getTripPoints(tripId)
                                points.forEach { point ->
                                    writer.write("${point.timestamp},${point.lat},${point.lng},${point.accuracyMeters},${point.speedMps},${point.rawMph},${point.smoothedMph}\n")
                                }
                            }
                            ExportType.GPX -> {
                                val points = tripDao.getTripPoints(tripId)
                                writer.write(buildGpx(trip, points))
                            }
                            ExportType.DIAGNOSTIC -> { /* handled above */ }
                        }
                    }
                }
                _exportEvent.emit("Export successful")
            } catch (e: Exception) {
                _exportEvent.emit("Export failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    companion object {
        fun escapeCsv(value: String): String {
            return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
        }

        fun escapeXml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun buildGpx(trip: TripEntity, points: List<TripPointEntity>): String {
            val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
            sb.append("""<gpx version="1.1" creator="GPS Speedometer" xmlns="http://www.topografix.com/GPX/1/1">""").append("\n")
            sb.append("  <metadata>\n")
            sb.append("    <name>").append(escapeXml(trip.name)).append("</name>\n")
            sb.append("    <time>").append(isoDateFormat.format(Date(trip.startTime))).append("</time>\n")
            sb.append("  </metadata>\n")
            sb.append("  <trk>\n")
            sb.append("    <name>").append(escapeXml(trip.name)).append("</name>\n")
            sb.append("    <trkseg>\n")
            for (pt in points) {
                sb.append("      <trkpt lat=\"").append(pt.lat).append("\" lon=\"").append(pt.lng).append("\">\n")
                sb.append("        <time>").append(isoDateFormat.format(Date(pt.timestamp))).append("</time>\n")
                sb.append("        <speed>").append(pt.speedMps).append("</speed>\n")
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
            sb.append("</gpx>\n")
            return sb.toString()
        }
    }

    class Factory(
        private val tripDao: TripDao,
        private val settingsRepository: SettingsRepository,
        private val speedLimitRepository: SpeedLimitRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripsViewModel::class.java)) {
                return TripsViewModel(tripDao, settingsRepository, speedLimitRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
