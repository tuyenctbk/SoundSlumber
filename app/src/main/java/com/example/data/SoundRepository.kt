package com.example.data

import com.example.data.db.AppUsageDao
import com.example.data.db.AppUsageEntity
import com.example.data.db.PresetEntity
import com.example.data.db.SleepLogEntity
import com.example.data.db.SoundDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SoundRepository(private val soundDao: SoundDao, private val appUsageDao: AppUsageDao) {
    // ...
    suspend fun getUsageValue(key: String): Long = appUsageDao.getValue(key) ?: 0L
    suspend fun incrementUsage(key: String) {
        val currentValue = getUsageValue(key)
        setUsageValue(key, currentValue + 1)
    }

    suspend fun setUsageValue(key: String, value: Long) {
        val rowsUpdated = appUsageDao.updateValue(key, value)
        if (rowsUpdated == 0) {
            appUsageDao.insertValue(AppUsageEntity(key, value))
        }
    }

    companion object {
        val BUILT_IN_PRESETS = listOf(
            Preset(
                id = "preset_rain_thunder",
                name = "Rain & Thunder",
                volumes = mapOf(
                    TrackType.HEAVY_RAIN to 0.65f,
                    TrackType.BROWN_NOISE to 0.42f,
                    TrackType.SOFT_THUNDER to 0.25f,
                    TrackType.CEILING_FAN to 0.20f
                )
            ),
            Preset(
                id = "preset_deep_brown",
                name = "Deep Brown Noise",
                volumes = mapOf(
                    TrackType.BROWN_NOISE to 0.85f,
                    TrackType.PINK_NOISE to 0.30f,
                    TrackType.CEILING_FAN to 0.25f
                )
            ),
            Preset(
                id = "preset_night_beach",
                name = "Night Surf",
                volumes = mapOf(
                    TrackType.OCEAN_WAVES to 0.75f,
                    TrackType.GENTLE_WIND to 0.30f,
                    TrackType.SOFT_THUNDER to 0.10f
                )
            ),
            Preset(
                id = "preset_cozy_fireplace",
                name = "Cozy Fireplace",
                volumes = mapOf(
                    TrackType.FIREPLACE to 0.65f,
                    TrackType.HEAVY_RAIN to 0.40f,
                    TrackType.BROWN_NOISE to 0.20f
                )
            ),
            Preset(
                id = "preset_fan_masking",
                name = "Bedroom Fan",
                volumes = mapOf(
                    TrackType.CEILING_FAN to 0.70f,
                    TrackType.WHITE_NOISE to 0.20f,
                    TrackType.BROWN_NOISE to 0.20f
                )
            ),
            Preset(
                id = "preset_coffee_focus",
                name = "Focus Drones",
                volumes = mapOf(
                    TrackType.COFFEE_SHOP to 0.55f,
                    TrackType.BROWN_NOISE to 0.35f,
                    TrackType.GENTLE_WIND to 0.20f
                )
            )
        )
    }

    val builtInPresets = BUILT_IN_PRESETS

    val customPresetsFlow: Flow<List<Preset>> = soundDao.getAllPresets().map { entities ->
        entities.map { entity ->
            Preset(
                id = entity.id,
                name = entity.name,
                volumes = parseVolumesJson(entity.trackVolumesJson)
            )
        }
    }

    val sleepLogsFlow: Flow<List<SleepLog>> = soundDao.getRecentSleepLogs().map { entities ->
        entities.map {
            SleepLog(
                id = it.id,
                timestamp = it.timestamp,
                durationMinutes = it.durationMinutes,
                presetUsed = it.presetUsed
            )
        }
    }

    suspend fun savePreset(preset: Preset) {
        val json = serializeVolumes(preset.volumes)
        soundDao.insertPreset(PresetEntity(id = preset.id, name = preset.name, trackVolumesJson = json))
    }

    suspend fun deletePreset(preset: Preset) {
        val json = serializeVolumes(preset.volumes)
        soundDao.deletePreset(PresetEntity(id = preset.id, name = preset.name, trackVolumesJson = json))
    }

    suspend fun logSleepSession(durationMinutes: Int, presetName: String) {
        soundDao.insertSleepLog(
            SleepLogEntity(
                timestamp = System.currentTimeMillis(),
                durationMinutes = durationMinutes,
                presetUsed = presetName
            )
        )
    }

    suspend fun clearHistory() {
        soundDao.clearSleepLogs()
    }

    private fun serializeVolumes(map: Map<TrackType, Float>): String {
        return map.entries.joinToString(";") { "${it.key.id}:${it.value}" }
    }

    private fun parseVolumesJson(raw: String): Map<TrackType, Float> {
        val map = mutableMapOf<TrackType, Float>()
        if (raw.isBlank()) return map
        raw.split(";").forEach { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val type = TrackType.entries.find { it.id == parts[0] }
                val vol = parts[1].toFloatOrNull()
                if (type != null && vol != null) {
                    map[type] = vol
                }
            }
        }
        return map
    }
}
