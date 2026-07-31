package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trackVolumesJson: String // Serialized map of track ID to volume
)
@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey val key: String,
    val value: Long = 0
)

@Entity(tableName = "sleep_logs")
data class SleepLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val durationMinutes: Int,
    val presetUsed: String
)
