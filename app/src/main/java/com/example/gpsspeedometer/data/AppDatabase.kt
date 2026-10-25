package com.example.gpsspeedometer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        TripPointEntity::class,
        OfflineRoadSegmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun offlineSpeedDao(): OfflineSpeedDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_points ADD COLUMN speedLimitMph INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `offline_road_segments` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `name` TEXT,
                        `highwayType` TEXT NOT NULL,
                        `speedLimitMph` INTEGER NOT NULL,
                        `speedLimitKmh` INTEGER NOT NULL,
                        `minLat` REAL NOT NULL,
                        `maxLat` REAL NOT NULL,
                        `minLng` REAL NOT NULL,
                        `maxLng` REAL NOT NULL,
                        `geometrySerialized` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_road_segments_minLat_maxLat_minLng_maxLng` ON `offline_road_segments` (`minLat`, `maxLat`, `minLng`, `maxLng`)")
            }
        }
    }
}
