package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.db.SoundDatabase
import com.example.service.SoundPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.example.firebase.FirebaseManager
import com.example.firebase.FirebaseState

import android.content.IntentFilter
import android.os.BatteryManager

enum class ThemeStyle {
    DARK, NIGHT_AMBER, LIGHT, AUTO, SUNSET_AUTO
}

enum class SuggestionType {
    SHARE, RATE, UPDATE
}

data class MainUiState(
    val tracks: List<SoundTrackState> = TrackType.entries.map { SoundTrackState(type = it) },
    val masterVolume: Float = 0.8f,
    val isPlaying: Boolean = false,
    val isServiceBound: Boolean = false,
    val timerTotalSeconds: Int = 0,
    val timerRemainingSeconds: Int = 0,
    val isTimerActive: Boolean = false,
    val selectedPresetName: String? = "Rain & Thunder",
    val customPresets: List<Preset> = emptyList(),
    val sleepLogs: List<SleepLog> = emptyList(),
    val currentTab: Int = 0, // 0: Mixer, 1: Presets, 2: OLED Clock, 3: Sleep Log & Stats, 4: Settings, 5: Quick
    val activeSoundCount: Int = 4,
    val firebaseState: FirebaseState = FirebaseState(),
    val themeStyle: ThemeStyle = ThemeStyle.DARK,
    val suggestionToShow: SuggestionType? = null,
    val currentSessionMinutes: Int = 0,
    val totalMinutesPlayed: Long = 0L,
    val smartRecommendation: String = "Combine Rain & Soft Thunder for optimal REM sleep cycles.",
    val isAutoCapEnabled: Boolean = true,
    val volumeCappedNotice: Boolean = false,
    val batteryLevel: Int = 88,
    val isCharging: Boolean = false,
    val estimatedDrainPerHour: Float = 2.4f,
    val exportShareText: String? = null,
    val importNoticeMessage: String? = null,
    val isPowerSaveEnabled: Boolean = false,
    val isNormalizationEnabled: Boolean = true,
    val isGentleWakeEnabled: Boolean = false,
    val isGentleWaking: Boolean = false,
    val gentleWakeProgress: Float = 0f,
    val recommendedPresets: List<Preset> = emptyList(),
    val showOnboarding: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SoundRepository
    private var playbackService: SoundPlaybackService? = null
    private var isBound = false

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var playbackDurationJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SoundPlaybackService.LocalBinder
            val s = binder.getService()
            playbackService = s
            s.isPowerSaveEnabled = _uiState.value.isPowerSaveEnabled
            s.isNormalizationEnabled = _uiState.value.isNormalizationEnabled
            isBound = true
            _uiState.update { it.copy(isServiceBound = true) }
            syncAudioEngineWithUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
            _uiState.update { it.copy(isServiceBound = false) }
        }
    }

    init {
        val database = SoundDatabase.getDatabase(application)
        repository = SoundRepository(database.soundDao(), database.appUsageDao())
        
        viewModelScope.launch {
            repository.incrementUsage("launch_count")
            checkSuggestions()
            val onboardingCompleted = repository.getUsageValue("onboarding_completed")
            if (onboardingCompleted != 1L) {
                _uiState.update { it.copy(showOnboarding = true) }
            }
        }
        val intent = Intent(application, SoundPlaybackService::class.java)
        application.startService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Observe Room Database custom presets and sleep logs
        viewModelScope.launch {
            repository.customPresetsFlow.collect { customPresets ->
                _uiState.update { it.copy(customPresets = customPresets) }
            }
        }

        viewModelScope.launch {
            repository.sleepLogsFlow.collect { logs ->
                _uiState.update { it.copy(sleepLogs = logs) }
                generateRecommendations(logs)
            }
        }

        // Observe Firebase State
        viewModelScope.launch {
            FirebaseManager.state.collect { fbState ->
                _uiState.update { it.copy(firebaseState = fbState) }
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
        val screenName = when (index) {
            0 -> "Mixer"
            1 -> "Presets"
            2 -> "Clock"
            3 -> "Stats"
            4 -> "Settings"
            else -> "Unknown"
        }
        FirebaseManager.logScreenView(screenName)
    }

    private suspend fun checkSuggestions() {
        val launchCount = repository.getUsageValue("launch_count")
        val soundsPlayed = repository.getUsageValue("sounds_played")
        val totalMinutesPlayed = repository.getUsageValue("total_minutes_played")
        
        val rateDismissed = repository.getUsageValue("rate_dismissed")
        val shareDismissed = repository.getUsageValue("share_dismissed")
        val updateDismissed = repository.getUsageValue("update_dismissed")

        val currentVersion = com.example.BuildConfig.VERSION_CODE
        val minRequiredVersion = repository.getUsageValue("min_required_version")

        val newRecommendation = when {
            totalMinutesPlayed > 120 -> "Deep Sleep Achieved: Low-frequency Brown Noise helps extend stage 3 non-REM sleep."
            soundsPlayed > 5 -> "Custom Soundscape Pro: Save your mix as a preset for 1-tap bedtime restore."
            else -> "Combine Rain & Soft Thunder for optimal REM sleep cycles."
        }

        _uiState.update { currentState ->
            currentState.copy(
                totalMinutesPlayed = totalMinutesPlayed,
                smartRecommendation = newRecommendation
            )
        }

        when {
            currentVersion < minRequiredVersion && updateDismissed != 1L -> {
                _uiState.update { it.copy(suggestionToShow = SuggestionType.UPDATE) }
            }
            (totalMinutesPlayed >= 30 || launchCount >= 3) && rateDismissed != 1L -> {
                _uiState.update { it.copy(suggestionToShow = SuggestionType.RATE) }
            }
            soundsPlayed >= 4 && shareDismissed != 1L -> {
                _uiState.update { it.copy(suggestionToShow = SuggestionType.SHARE) }
            }
        }
    }

    fun dismissSuggestion() {
        val currentSuggestion = _uiState.value.suggestionToShow
        viewModelScope.launch {
            when (currentSuggestion) {
                SuggestionType.RATE -> repository.setUsageValue("rate_dismissed", 1)
                SuggestionType.SHARE -> repository.setUsageValue("share_dismissed", 1)
                SuggestionType.UPDATE -> repository.setUsageValue("update_dismissed", 1)
                null -> {}
            }
            _uiState.update { it.copy(suggestionToShow = null) }
        }
    }

    fun setThemeStyle(style: ThemeStyle) {
        _uiState.update { it.copy(themeStyle = style) }
    }

    fun playRandomMix() {
        val randomVolumes = TrackType.entries.associate {
            it to (200..800).random() / 1000f
        }
        val randomPreset = Preset(
            id = "random_mix",
            name = "Random Mix",
            volumes = randomVolumes
        )
        applyPreset(randomPreset)
        if (!_uiState.value.isPlaying) {
            togglePlayback()
        }
    }

    fun togglePlayback() {
        val newPlayState = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = newPlayState) }

        playbackService?.let { service ->
            if (newPlayState) {
                syncAudioEngineWithUi()
                service.startPlayback()
                FirebaseManager.logPlaySoundscape(_uiState.value.selectedPresetName)
                startPlaybackDurationTracker()
                viewModelScope.launch {
                    repository.incrementUsage("sounds_played")
                    checkSuggestions()
                }
            } else {
                service.stopPlayback()
                stopPlaybackDurationTracker()
            }
        }
    }

    private fun startPlaybackDurationTracker() {
        playbackDurationJob?.cancel()
        playbackDurationJob = viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                delay(60000L) // every 1 minute of playback
                if (_uiState.value.isPlaying) {
                    repository.incrementUsage("total_minutes_played")
                    val currentMins = repository.getUsageValue("total_minutes_played")
                    _uiState.update { 
                        it.copy(
                            currentSessionMinutes = it.currentSessionMinutes + 1,
                            totalMinutesPlayed = currentMins
                        ) 
                    }
                }
            }
        }
    }

    private fun stopPlaybackDurationTracker() {
        playbackDurationJob?.cancel()
        playbackDurationJob = null
        val sessionMins = _uiState.value.currentSessionMinutes
        if (sessionMins >= 1) {
            val presetUsed = _uiState.value.selectedPresetName ?: "Custom Mix"
            viewModelScope.launch {
                val log = SleepLog(durationMinutes = sessionMins, presetUsed = presetUsed)
                repository.logSleepSession(sessionMins, presetUsed)
                FirebaseManager.syncSleepLogToCloud(log)
            }
        }
        _uiState.update { it.copy(currentSessionMinutes = 0) }
    }

    fun setMasterVolume(volume: Float) {
        _uiState.update { it.copy(masterVolume = volume) }
        playbackService?.soundEngine?.masterVolume = volume
    }

    fun toggleAutoCapVolume() {
        _uiState.update { it.copy(isAutoCapEnabled = !it.isAutoCapEnabled) }
    }

    fun updateTrackVolume(type: TrackType, volume: Float) {
        var isCapApplied = false
        _uiState.update { currentState ->
            val activeOtherTracksCount = currentState.tracks.count { it.type != type && it.effectiveVolume > 0.05f }
            val adjustedVolume = if (currentState.isAutoCapEnabled && activeOtherTracksCount >= 2 && volume > 0.65f) {
                isCapApplied = true
                0.65f // Cap track at 65% max when 2 or more other tracks are active to prevent sudden loud audio spikes
            } else {
                volume
            }

            val updatedTracks = currentState.tracks.map { track ->
                if (track.type == type) track.copy(volume = adjustedVolume) else track
            }
            val activeCount = updatedTracks.count { it.effectiveVolume > 0.01f }
            currentState.copy(
                tracks = updatedTracks,
                activeSoundCount = activeCount,
                selectedPresetName = null,
                volumeCappedNotice = isCapApplied
            )
        }
        val finalVolume = _uiState.value.tracks.find { it.type == type }?.volume ?: volume
        playbackService?.soundEngine?.setTrackVolume(type, finalVolume)
    }

    fun toggleMuteTrack(type: TrackType) {
        _uiState.update { currentState ->
            val updatedTracks = currentState.tracks.map { track ->
                if (track.type == type) track.copy(isMuted = !track.isMuted) else track
            }
            currentState.copy(tracks = updatedTracks)
        }
        val track = _uiState.value.tracks.find { it.type == type }
        playbackService?.soundEngine?.setTrackVolume(type, track?.effectiveVolume ?: 0f)
    }

    fun applyPreset(preset: Preset) {
        _uiState.update { currentState ->
            val updatedTracks = currentState.tracks.map { track ->
                val presetVol = preset.volumes[track.type] ?: 0f
                track.copy(volume = presetVol, isMuted = false)
            }
            val activeCount = updatedTracks.count { it.effectiveVolume > 0.01f }
            currentState.copy(
                tracks = updatedTracks,
                selectedPresetName = preset.name,
                activeSoundCount = activeCount
            )
        }
        syncAudioEngineWithUi()
    }

    fun saveCurrentMixAsPreset(name: String) {
        val currentVolumes = _uiState.value.tracks
            .filter { it.effectiveVolume > 0f }
            .associate { it.type to it.effectiveVolume }

        if (currentVolumes.isEmpty()) return

        val newPreset = Preset(
            id = "preset_custom_${System.currentTimeMillis()}",
            name = name.ifBlank { "Custom Soundscape" },
            volumes = currentVolumes
        )

        viewModelScope.launch {
            repository.savePreset(newPreset)
            FirebaseManager.syncPresetToCloud(newPreset)
            _uiState.update { it.copy(selectedPresetName = newPreset.name) }
        }
    }

    fun deleteCustomPreset(preset: Preset) {
        viewModelScope.launch {
            repository.deletePreset(preset)
            FirebaseManager.deletePresetFromCloud(preset.id)
        }
    }

    fun setTimerMinutes(minutes: Int) {
        val totalSeconds = minutes * 60
        _uiState.update {
            it.copy(
                timerTotalSeconds = totalSeconds,
                timerRemainingSeconds = totalSeconds,
                isTimerActive = true
            )
        }
        FirebaseManager.logTimerStart(minutes)
        startTimerCountdown()
    }

    fun addTimerMinutes(minutes: Int) {
        val currentRemaining = _uiState.value.timerRemainingSeconds
        val addedSeconds = minutes * 60
        val newTotal = currentRemaining + addedSeconds

        _uiState.update {
            it.copy(
                timerTotalSeconds = if (it.isTimerActive) it.timerTotalSeconds + addedSeconds else newTotal,
                timerRemainingSeconds = newTotal,
                isTimerActive = true
            )
        }

        FirebaseManager.logTimerStart(minutes)

        if (timerJob?.isActive != true) {
            startTimerCountdown()
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        playbackService?.soundEngine?.fadeOutMultiplier = 1.0f
        _uiState.update {
            it.copy(
                timerTotalSeconds = 0,
                timerRemainingSeconds = 0,
                isTimerActive = false
            )
        }
    }

    private fun startTimerCountdown() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            if (!_uiState.value.isPlaying) {
                togglePlayback()
            }

            while (_uiState.value.timerRemainingSeconds > 0 && _uiState.value.isTimerActive) {
                delay(1000L)
                if (!_uiState.value.isPlaying) {
                    continue
                }

                val remaining = _uiState.value.timerRemainingSeconds - 1

                val totalSecs = _uiState.value.timerTotalSeconds.coerceAtLeast(1)
                val fadeWindow = 300.coerceAtMost(totalSecs)
                val fadeOutMult = if (remaining in 1..fadeWindow) {
                    (remaining / fadeWindow.toFloat()).coerceIn(0.05f, 1.0f)
                } else {
                    1.0f
                }

                playbackService?.soundEngine?.fadeOutMultiplier = fadeOutMult

                if (remaining <= 0) {
                    val totalDurationMins = (_uiState.value.timerTotalSeconds / 60).coerceAtLeast(1)
                    val presetUsed = _uiState.value.selectedPresetName ?: "Custom Mix"

                    val log = SleepLog(
                        durationMinutes = totalDurationMins,
                        presetUsed = presetUsed
                    )
                    repository.logSleepSession(totalDurationMins, presetUsed)
                    FirebaseManager.syncSleepLogToCloud(log)
                    FirebaseManager.logSleepCompleted(totalDurationMins, presetUsed)

                    if (_uiState.value.isGentleWakeEnabled) {
                        _uiState.update {
                            it.copy(
                                timerTotalSeconds = 0,
                                timerRemainingSeconds = 0,
                                isTimerActive = false,
                                isGentleWaking = true,
                                gentleWakeProgress = 0f
                            )
                        }
                        startGentleWakeRamp()
                    } else {
                        _uiState.update {
                            it.copy(
                                timerTotalSeconds = 0,
                                timerRemainingSeconds = 0,
                                isTimerActive = false,
                                isPlaying = false
                            )
                        }
                        playbackService?.soundEngine?.fadeOutMultiplier = 1.0f
                        playbackService?.stopPlayback()
                    }
                    break
                } else {
                    _uiState.update { it.copy(timerRemainingSeconds = remaining) }
                }
            }
        }
    }

    fun refreshBatteryStatus() {
        val context = getApplication<Application>()
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 88
        val isPlaying = _uiState.value.isPlaying
        val activeCount = _uiState.value.activeSoundCount
        
        val estDrain = if (isPlaying) {
            (1.8f + (activeCount * 0.35f)).coerceAtMost(5.0f)
        } else {
            1.1f
        }

        _uiState.update {
            it.copy(
                batteryLevel = pct,
                isCharging = isCharging,
                estimatedDrainPerHour = estDrain
            )
        }
    }

    fun exportPresetAsLink(preset: Preset): String {
        val sb = StringBuilder("https://soundslumber.app/preset?name=")
        sb.append(java.net.URLEncoder.encode(preset.name, "UTF-8"))
        preset.volumes.forEach { (type, vol) ->
            if (vol > 0.01f) {
                sb.append("&").append(type.name).append("=").append((vol * 100).toInt())
            }
        }
        val generatedLink = sb.toString()
        _uiState.update { it.copy(exportShareText = generatedLink) }
        return generatedLink
    }

    fun importPresetFromLink(linkString: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(linkString.trim())
            val name = uri.getQueryParameter("name") ?: "Shared Mix"
            val newVolumes = mutableMapOf<TrackType, Float>()

            TrackType.entries.forEach { type ->
                val paramVal = uri.getQueryParameter(type.name)
                if (paramVal != null) {
                    val volPct = paramVal.toFloatOrNull() ?: 0f
                    if (volPct > 0f) {
                        newVolumes[type] = (volPct / 100f).coerceIn(0f, 1f)
                    }
                }
            }

            if (newVolumes.isNotEmpty()) {
                val importedPreset = Preset(
                    id = "preset_import_${System.currentTimeMillis()}",
                    name = name,
                    volumes = newVolumes
                )
                viewModelScope.launch {
                    repository.savePreset(importedPreset)
                    applyPreset(importedPreset)
                }
                _uiState.update { it.copy(importNoticeMessage = "Imported preset '$name' successfully!") }
                true
            } else {
                _uiState.update { it.copy(importNoticeMessage = "Invalid preset link format.") }
                false
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(importNoticeMessage = "Error parsing preset link.") }
            false
        }
    }

    fun clearNotices() {
        _uiState.update { it.copy(exportShareText = null, importNoticeMessage = null) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private fun syncAudioEngineWithUi() {
        playbackService?.soundEngine?.let { engine ->
            engine.masterVolume = _uiState.value.masterVolume
            _uiState.value.tracks.forEach { track ->
                engine.setTrackVolume(track.type, track.effectiveVolume)
            }
        }
    }

    fun togglePowerSave() {
        val newState = !_uiState.value.isPowerSaveEnabled
        _uiState.update { it.copy(isPowerSaveEnabled = newState) }
        playbackService?.isPowerSaveEnabled = newState
        if (_uiState.value.isPlaying) {
            playbackService?.stopPlayback()
            syncAudioEngineWithUi()
            playbackService?.startPlayback()
        }
    }

    fun toggleNormalization() {
        val newState = !_uiState.value.isNormalizationEnabled
        _uiState.update { it.copy(isNormalizationEnabled = newState) }
        playbackService?.isNormalizationEnabled = newState
    }

    fun toggleGentleWake() {
        val newState = !_uiState.value.isGentleWakeEnabled
        _uiState.update { it.copy(isGentleWakeEnabled = newState) }
    }

    fun dismissGentleWake() {
        gentleWakeJob?.cancel()
        gentleWakeJob = null
        _uiState.update { it.copy(isGentleWaking = false, gentleWakeProgress = 0f, isPlaying = false) }
        playbackService?.stopPlayback()
    }

    private var gentleWakeJob: Job? = null

    private fun startGentleWakeRamp() {
        gentleWakeJob?.cancel()
        val wakePreset = Preset(
            id = "gentle_wake_preset",
            name = "Morning Gentle Wake",
            volumes = mapOf(
                TrackType.GENTLE_WIND to 0.45f,
                TrackType.OCEAN_WAVES to 0.55f
            )
        )
        applyPreset(wakePreset)

        gentleWakeJob = viewModelScope.launch {
            val totalSteps = 300
            val targetVolume = 0.8f
            playbackService?.soundEngine?.fadeOutMultiplier = 1.0f
            
            for (step in 1..totalSteps) {
                delay(1000L)
                val progress = step.toFloat() / totalSteps
                val stepVolume = progress * targetVolume
                setMasterVolume(stepVolume)
                _uiState.update { it.copy(gentleWakeProgress = progress) }
            }
        }
    }

    private val activeTrackFadeJobs = java.util.concurrent.ConcurrentHashMap<TrackType, Job>()

    fun fadeTrackOutOver3Seconds(type: TrackType) {
        activeTrackFadeJobs[type]?.cancel()
        activeTrackFadeJobs[type] = viewModelScope.launch {
            val trackState = _uiState.value.tracks.find { it.type == type } ?: return@launch
            if (trackState.effectiveVolume <= 0.01f) return@launch

            val startVol = trackState.volume
            val steps = 15
            val decrement = startVol / steps

            for (step in 1..steps) {
                delay(200L)
                val currentVol = (startVol - (step * decrement)).coerceAtLeast(0f)
                _uiState.update { currentState ->
                    val updatedTracks = currentState.tracks.map { track ->
                        if (track.type == type) track.copy(volume = currentVol) else track
                    }
                    currentState.copy(tracks = updatedTracks)
                }
                playbackService?.soundEngine?.setTrackVolume(type, currentVol)
            }

            _uiState.update { currentState ->
                val updatedTracks = currentState.tracks.map { track ->
                    if (track.type == type) track.copy(volume = 0f, isMuted = true) else track
                }
                currentState.copy(
                    tracks = updatedTracks,
                    activeSoundCount = updatedTracks.count { it.effectiveVolume > 0.01f }
                )
            }
            playbackService?.soundEngine?.setTrackVolume(type, 0f)
            activeTrackFadeJobs.remove(type)
        }
    }

    private fun generateRecommendations(logs: List<SleepLog>) {
        val rainCount = logs.count { it.presetUsed.contains("Rain", ignoreCase = true) || it.presetUsed.contains("Storm", ignoreCase = true) }
        val noiseCount = logs.count { it.presetUsed.contains("Noise", ignoreCase = true) || it.presetUsed.contains("Fan", ignoreCase = true) }
        val ambientCount = logs.count { it.presetUsed.contains("Coffee", ignoreCase = true) || it.presetUsed.contains("Fire", ignoreCase = true) }

        val recommendations = mutableListOf<Preset>()

        if (rainCount >= noiseCount && rainCount >= ambientCount) {
            recommendations.add(Preset(
                id = "rec_storm",
                name = "Deep Thunder Canopy",
                volumes = mapOf(
                    TrackType.HEAVY_RAIN to 0.70f,
                    TrackType.SOFT_THUNDER to 0.40f,
                    TrackType.GENTLE_WIND to 0.30f
                )
            ))
            recommendations.add(Preset(
                id = "rec_ocean",
                name = "Ocean Wave Hypnosis",
                volumes = mapOf(
                    TrackType.OCEAN_WAVES to 0.80f,
                    TrackType.GENTLE_WIND to 0.25f,
                    TrackType.PINK_NOISE to 0.20f
                )
            ))
        } else if (noiseCount >= rainCount && noiseCount >= ambientCount) {
            recommendations.add(Preset(
                id = "rec_noise_fan",
                name = "Aero Static Dream",
                volumes = mapOf(
                    TrackType.CEILING_FAN to 0.65f,
                    TrackType.BROWN_NOISE to 0.45f,
                    TrackType.WHITE_NOISE to 0.20f
                )
            ))
            recommendations.add(Preset(
                id = "rec_zen",
                name = "Harmonic Pink Focus",
                volumes = mapOf(
                    TrackType.PINK_NOISE to 0.60f,
                    TrackType.CEILING_FAN to 0.30f
                )
            ))
        } else {
            recommendations.add(Preset(
                id = "rec_cozy",
                name = "Cozy Fireplace Cabin",
                volumes = mapOf(
                    TrackType.FIREPLACE to 0.75f,
                    TrackType.GENTLE_WIND to 0.35f,
                    TrackType.COFFEE_SHOP to 0.20f
                )
            ))
            recommendations.add(Preset(
                id = "rec_cafe",
                name = "Rainy Street Cafe",
                volumes = mapOf(
                    TrackType.COFFEE_SHOP to 0.55f,
                    TrackType.HEAVY_RAIN to 0.40f
                )
            ))
        }

        recommendations.add(Preset(
            id = "rec_sleep_well",
            name = "Astral Sleep Drones",
            volumes = mapOf(
                TrackType.BROWN_NOISE to 0.40f,
                TrackType.CEILING_FAN to 0.30f,
                TrackType.OCEAN_WAVES to 0.30f
            )
        ))

        _uiState.update { it.copy(recommendedPresets = recommendations) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.setUsageValue("onboarding_completed", 1L)
            _uiState.update { it.copy(showOnboarding = false) }
        }
    }

    override fun onCleared() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
        super.onCleared()
    }
}
