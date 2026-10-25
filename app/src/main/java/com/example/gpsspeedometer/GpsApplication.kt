package com.example.gpsspeedometer

import android.app.Application
import android.util.Log
import com.example.gpsspeedometer.di.AppContainer

class GpsApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FATAL_APP_CRASH", "Fatal uncaught exception on thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        try {
            container = AppContainer(this)
        } catch (e: Exception) {
            Log.e("GpsApplication", "Failed to initialize AppContainer", e)
        }
    }
}
