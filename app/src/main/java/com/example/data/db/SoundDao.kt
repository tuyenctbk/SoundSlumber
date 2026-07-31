package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDao {
    @Query("SELECT * FROM presets")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity)

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Query("SELECT * FROM sleep_logs ORDER BY timestamp DESC LIMIT 30")
    fun getRecentSleepLogs(): Flow<List<SleepLogEntity>>

    @Insert
    suspend fun insertSleepLog(log: SleepLogEntity)

    @Query("DELETE FROM sleep_logs")
    suspend fun clearSleepLogs()
}
