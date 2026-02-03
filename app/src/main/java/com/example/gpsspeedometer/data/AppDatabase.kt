package com.example.gpsspeedometer.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class, TripPointEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}
