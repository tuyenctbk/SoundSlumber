package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PresetEntity::class, SleepLogEntity::class, AppUsageEntity::class], version = 2, exportSchema = false)
abstract class SoundDatabase : RoomDatabase() {
    abstract fun soundDao(): SoundDao
    abstract fun appUsageDao(): AppUsageDao

    companion object {
        @Volatile
        private var INSTANCE: SoundDatabase? = null

        fun getDatabase(context: Context): SoundDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SoundDatabase::class.java,
                    "soundslumber_db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
