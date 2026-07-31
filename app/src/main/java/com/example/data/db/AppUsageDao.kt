package com.example.data.db

import androidx.room.*

@Dao
interface AppUsageDao {
    @Query("SELECT value FROM app_usage WHERE `key` = :key")
    suspend fun getValue(key: String): Long?

    @Query("UPDATE app_usage SET value = :value WHERE `key` = :key")
    suspend fun updateValue(key: String, value: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(usage: AppUsageEntity)
    
    @Query("SELECT * FROM app_usage")
    suspend fun getAllUsage(): List<AppUsageEntity>
}
