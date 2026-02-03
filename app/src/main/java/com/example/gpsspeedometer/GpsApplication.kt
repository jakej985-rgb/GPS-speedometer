package com.example.gpsspeedometer

import android.app.Application
import com.example.gpsspeedometer.di.AppContainer

class GpsApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
