package com.example.gpsspeedometer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SpeedLimitDiagnosticDao {

    @Insert
    suspend fun insert(event: SpeedLimitDiagnosticEntity): Long

    @Query("SELECT * FROM speed_limit_diagnostics ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<SpeedLimitDiagnosticEntity>

    @Query("SELECT COUNT(*) FROM speed_limit_diagnostics")
    suspend fun getEventCount(): Int

    @Query(
        "DELETE FROM speed_limit_diagnostics WHERE id NOT IN " +
            "(SELECT id FROM speed_limit_diagnostics ORDER BY id DESC LIMIT :keepCount)"
    )
    suspend fun pruneToLatest(keepCount: Int)

    @Query("DELETE FROM speed_limit_diagnostics")
    suspend fun clearAll()
}
