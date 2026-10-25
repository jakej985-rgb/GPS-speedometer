package com.example.gpsspeedometer

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.gpsspeedometer.di.AppContainer

class GpsApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                val crashReport = "Thread: ${thread.name}\n" +
                        "Exception: ${throwable.javaClass.name}\n" +
                        "Message: ${throwable.message}\n\n" +
                        "Stack Trace:\n$stackTrace"
                Log.e("FATAL_APP_CRASH", crashReport)
                val prefs = getSharedPreferences("crash_reporting", Context.MODE_PRIVATE)
                prefs.edit().putString("last_crash_trace", crashReport).commit()
            } catch (e: Exception) {
                Log.e("FATAL_APP_CRASH", "Error persisting crash report", e)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        try {
            com.example.gpsspeedometer.ui.map.AppTileSources.setupOsmdroid(this)
        } catch (e: Exception) {
            Log.e("GpsApplication", "Failed to initialize osmdroid", e)
        }

        try {
            container = AppContainer(this)
        } catch (e: Exception) {
            Log.e("GpsApplication", "Failed to initialize AppContainer", e)
        }
    }
}
