package com.example.gpsspeedometer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.gpsspeedometer.GpsApplication
import com.example.gpsspeedometer.MainActivity
import com.example.gpsspeedometer.R
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.location.LocationRepository
import com.example.gpsspeedometer.logic.TripManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class TrackingService : LifecycleService() {

    private lateinit var tripManager: TripManager
    private lateinit var locationRepository: LocationRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationManager: NotificationManager

    private var locationJob: Job? = null
    private var statsJob: Job? = null
    private var currentSampleRate: Long = 1000L

    override fun onCreate() {
        super.onCreate()
        val container = (application as GpsApplication).container
        tripManager = container.tripManager
        locationRepository = container.locationRepository
        settingsRepository = container.settingsRepository
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()

        // Observe settings for sample rate changes
        settingsRepository.settingsFlow
            .map { it.sampleIntervalMs }
            .distinctUntilChanged()
            .onEach { rate ->
                currentSampleRate = rate
                if (locationJob?.isActive == true) {
                    stopLocationUpdates()
                    startLocationUpdates()
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(0f, 0f, 0L))
                startTracking()
            }
            ACTION_PAUSE -> {
                tripManager.pauseTrip()
                stopLocationUpdates()
            }
            ACTION_RESUME -> {
                tripManager.resumeTrip()
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startTracking() {
        tripManager.startTrip()
        startLocationUpdates()
        observeTripStats()
    }

    private fun stopTracking() {
        tripManager.stopTrip()
        stopLocationUpdates()
        statsJob?.cancel()
    }

    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return

        locationJob = locationRepository.getLocationUpdates(currentSampleRate)
            .onEach { location ->
                tripManager.processLocation(location)
            }
            .launchIn(lifecycleScope)
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun observeTripStats() {
        statsJob?.cancel()
        statsJob = tripManager.tripState
            .onEach { stats ->
                updateNotification(stats.currentSpeedMph, stats.distanceMeters, stats.elapsedMs)
            }
            .launchIn(lifecycleScope)
    }

    private fun updateNotification(mph: Float, distanceMeters: Float, elapsedMs: Long) {
        val notification = buildNotification(mph, distanceMeters, elapsedMs)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(mph: Float, distanceMeters: Float, elapsedMs: Long): Notification {
        val distanceMiles = distanceMeters * 0.000621371
        val elapsedSec = elapsedMs / 1000
        val h = elapsedSec / 3600
        val m = (elapsedSec % 3600) / 60
        val s = elapsedSec % 60
        val timeStr = String.format("%02d:%02d:%02d", h, m, s)
        val contentText = String.format("Speed: %.1f MPH | Dist: %.2f mi | Time: %s", mph, distanceMiles, timeStr)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trip in Progress")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (Build.VERSION.SDK_INT >= 34) {
             builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"

        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "trip_tracking_channel"
    }
}
