package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class TrackType(
    val id: String,
    val title: String,
    val category: String,
    val defaultVolume: Float,
    val icon: ImageVector
) {
    HEAVY_RAIN("rain", "Heavy Rain", "Nature", 0.65f, Icons.Default.WaterDrop),
    BROWN_NOISE("brown", "Brown Noise", "Noise", 0.42f, Icons.Default.GraphicEq),
    SOFT_THUNDER("thunder", "Soft Thunder", "Nature", 0.15f, Icons.Default.Thunderstorm),
    CEILING_FAN("fan", "Ceiling Fan", "Home", 0.28f, Icons.Default.Air),
    OCEAN_WAVES("ocean", "Ocean Waves", "Nature", 0.00f, Icons.Default.Waves),
    FIREPLACE("fire", "Fireplace Crackle", "Home", 0.00f, Icons.Default.Whatshot),
    PINK_NOISE("pink", "Pink Noise", "Noise", 0.00f, Icons.Default.Grain),
    WHITE_NOISE("white", "White Noise", "Noise", 0.00f, Icons.Default.Curtains),
    GENTLE_WIND("wind", "Gentle Wind", "Nature", 0.00f, Icons.Default.Air),
    COFFEE_SHOP("coffee", "Coffee Shop Drones", "Ambient", 0.00f, Icons.Default.Coffee)
}

data class SoundTrackState(
    val type: TrackType,
    val volume: Float = type.defaultVolume,
    val isMuted: Boolean = false
) {
    val effectiveVolume: Float
        get() = if (isMuted) 0f else volume
}

data class Preset(
    val id: String,
    val name: String,
    val volumes: Map<TrackType, Float>
)

data class SleepLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMinutes: Int,
    val presetUsed: String
)
