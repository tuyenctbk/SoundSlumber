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

enum class ThemeStyle {
    DARK, LIGHT, AUTO
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
    val currentTab: Int = 0, // 0: Mixer, 1: Presets, 2: OLED Clock, 3: Sleep Log & Stats, 4: Settings
    val activeSoundCount: Int = 4,
    val firebaseState: FirebaseState = FirebaseState(),
    val themeStyle: ThemeStyle = ThemeStyle.DARK
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SoundRepository
    private var playbackService: SoundPlaybackService? = null
    private var isBound = false

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SoundPlaybackService.LocalBinder
            playbackService = binder.getService()
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
        repository = SoundRepository(database.soundDao())

        // Bind foreground service
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
            } else {
                service.stopPlayback()
            }
        }
    }

    fun setMasterVolume(volume: Float) {
        _uiState.update { it.copy(masterVolume = volume) }
        playbackService?.soundEngine?.masterVolume = volume
    }

    fun updateTrackVolume(type: TrackType, volume: Float) {
        _uiState.update { currentState ->
            val updatedTracks = currentState.tracks.map { track ->
                if (track.type == type) track.copy(volume = volume) else track
            }
            val activeCount = updatedTracks.count { it.effectiveVolume > 0.01f }
            currentState.copy(
                tracks = updatedTracks,
                activeSoundCount = activeCount,
                selectedPresetName = null
            )
        }
        playbackService?.soundEngine?.setTrackVolume(type, volume)
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

    fun addTimerMinutes(minutes: Int) {
        val currentRemaining = _uiState.value.timerRemainingSeconds
        val addedSeconds = minutes * 60
        val newTotal = currentRemaining + addedSeconds

        _uiState.update {
            it.copy(
                timerTotalSeconds = newTotal,
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
                val remaining = _uiState.value.timerRemainingSeconds - 1

                val fadeOutMult = if (remaining in 1..300) {
                    (remaining / 300.0f).coerceIn(0.05f, 1.0f)
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
                    break
                } else {
                    _uiState.update { it.copy(timerRemainingSeconds = remaining) }
                }
            }
        }
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

    override fun onCleared() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
        super.onCleared()
    }
}
