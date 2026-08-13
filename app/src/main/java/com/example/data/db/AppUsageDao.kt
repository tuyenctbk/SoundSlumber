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
    
    @Query("INSERT INTO app_usage (`key`, value) VALUES (:key, 1) ON CONFLICT(`key`) DO UPDATE SET value = value + 1")
    suspend fun incrementValue(key: String)

    @Query("SELECT * FROM app_usage")
    suspend fun getAllUsage(): List<AppUsageEntity>
}
