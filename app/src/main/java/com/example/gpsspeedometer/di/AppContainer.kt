package com.example.gpsspeedometer.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.gpsspeedometer.data.AppDatabase
import com.example.gpsspeedometer.data.SettingsRepository
import com.example.gpsspeedometer.location.LocationRepository
import com.example.gpsspeedometer.logic.TripManager
import com.example.gpsspeedometer.speedlimit.SpeedLimitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "gps_speedometer.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context.dataStore)
    }

    val locationRepository: LocationRepository by lazy {
        LocationRepository(context)
    }

    val speedLimitRepository: SpeedLimitRepository by lazy {
        SpeedLimitRepository()
    }

    // Application scope for TripManager
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val tripManager: TripManager by lazy {
        TripManager(
            settingsRepository = settingsRepository,
            tripDao = database.tripDao(),
            scope = appScope,
            speedLimitRepository = speedLimitRepository
        )
    }
}
