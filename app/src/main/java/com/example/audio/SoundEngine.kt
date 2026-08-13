package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.TrackType
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.*

class SoundEngine {

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    var isPowerSaveEnabled: Boolean = false

    @Volatile
    var isNormalizationEnabled: Boolean = true

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var masterVolume: Float = 0.8f

    @Volatile
    var fadeOutMultiplier: Float = 1.0f

    private val trackVolumes = java.util.concurrent.ConcurrentHashMap<TrackType, Float>()

    init {
        TrackType.entries.forEach { track ->
            trackVolumes[track] = track.defaultVolume
        }
    }

    fun setTrackVolume(type: TrackType, volume: Float) {
        trackVolumes[type] = volume.coerceIn(0f, 1f)
    }

    fun getTrackVolume(type: TrackType): Float {
        return trackVolumes[type] ?: 0f
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true

        val sRate = if (isPowerSaveEnabled) 22050 else 44100
        val minBuffer = AudioTrack.getMinBufferSize(
            sRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBuffer * 2).coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        synthJob = scope.launch {
            synthesizeAudioLoop()
        }
    }

    fun stop() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null

        try {
            val track = audioTrack
            audioTrack = null
            track?.stop()
            track?.release()
        } catch (_: Exception) {}
    }

    private suspend fun synthesizeAudioLoop() = withContext(Dispatchers.Default) {
        val frameSize = 1024
        val buffer = ShortArray(frameSize)
        val floatBuffer = FloatArray(frameSize)
        val sRate = if (isPowerSaveEnabled) 22050f else 44100f

        // DSP filter states for each generator
        val random = Random()
        var brownLast = 0f

        // Pink noise filter state
        var b0 = 0f; var b1 = 0f; var b2 = 0f
        var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f

        // Rain state
        var rainDropEnv = 0f

        // Thunder state
        var thunderPhase = 0f
        var thunderBursts = 0f
        var thunderLp = 0f

        // Fan state
        var fanPhase = 0f
        var fanLp = 0f

        // Ocean state
        var oceanPhase = 0f
        var oceanLp = 0f

        // Wind state
        var windPhase = 0f
        var windFilterState = 0f

        // Coffee drone state
        var dronePhase = 0f

        var sampleIndex = 0L

        while (isPlaying && isActive) {
            floatBuffer.fill(0f)

            val vRain = (trackVolumes[TrackType.HEAVY_RAIN] ?: 0f)
            val vBrown = (trackVolumes[TrackType.BROWN_NOISE] ?: 0f)
            val vThunder = (trackVolumes[TrackType.SOFT_THUNDER] ?: 0f)
            val vFan = (trackVolumes[TrackType.CEILING_FAN] ?: 0f)
            val vOcean = (trackVolumes[TrackType.OCEAN_WAVES] ?: 0f)
            val vFire = (trackVolumes[TrackType.FIREPLACE] ?: 0f)
            val vPink = (trackVolumes[TrackType.PINK_NOISE] ?: 0f)
            val vWhite = (trackVolumes[TrackType.WHITE_NOISE] ?: 0f)
            val vWind = (trackVolumes[TrackType.GENTLE_WIND] ?: 0f)
            val vCoffee = (trackVolumes[TrackType.COFFEE_SHOP] ?: 0f)

            // Calculate frame-level AGC gain & normalization factors once per frame (1024 samples)
            val activeTrackSum = vRain + vBrown + vThunder + vFan + vOcean + vFire + vPink + vWhite + vWind + vCoffee
            val normalizationFactor = if (isNormalizationEnabled && activeTrackSum > 1.0f) {
                1.0f / activeTrackSum
            } else {
                1.0f
            }
            val dynamicGain = if (activeTrackSum > 1.0f) {
                1.0f / sqrt(1.0f + 0.45f * (activeTrackSum - 1.0f))
            } else {
                1.0f
            }
            val finalVol = (masterVolume * fadeOutMultiplier * dynamicGain * normalizationFactor).coerceIn(0f, 1f)

            for (i in 0 until frameSize) {
                sampleIndex++
                var mixSample = 0f
                val white = (random.nextFloat() * 2f - 1f)

                // 1. Brown Noise Generator
                if (vBrown > 0.001f) {
                    brownLast = (brownLast + (0.02f * white)) / 1.02f
                    mixSample += brownLast * 3.5f * vBrown
                }

                // 2. Pink Noise Generator (Paul Kellet algorithm)
                val pinkSample: Float
                if (vPink > 0.001f || vRain > 0.001f) {
                    b0 = 0.99886f * b0 + white * 0.0555179f
                    b1 = 0.99332f * b1 + white * 0.0750759f
                    b2 = 0.96900f * b2 + white * 0.1538520f
                    b3 = 0.86650f * b3 + white * 0.3104856f
                    b4 = 0.55000f * b4 + white * 0.5329522f
                    b5 = -0.7616f * b5 - white * 0.0168980f
                    pinkSample = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f) * 0.11f
                    b6 = white * 0.115926f

                    if (vPink > 0.001f) {
                        mixSample += pinkSample * vPink
                    }
                } else {
                    pinkSample = 0f
                }

                // 3. White Noise
                if (vWhite > 0.001f) {
                    mixSample += white * 0.35f * vWhite
                }

                // 4. Heavy Rain
                if (vRain > 0.001f) {
                    // Rain droplets
                    if (random.nextFloat() < 0.0005f) {
                        rainDropEnv = 1.0f
                    }
                    rainDropEnv *= 0.998f
                    val drop = white * rainDropEnv * 0.4f
                    val rainBase = pinkSample * 0.7f + drop
                    mixSample += rainBase * vRain
                }

                // 5. Soft Thunder
                if (vThunder > 0.001f) {
                    thunderPhase += 1f / sRate
                    // Periodic burst (~every 12 seconds)
                    if (thunderPhase > 12f) {
                        thunderPhase = 0f
                        if (random.nextFloat() < 0.7f) {
                            thunderBursts = 1.0f
                        }
                    }
                    thunderBursts *= 0.99992f
                    thunderLp += 0.015f * (white * thunderBursts - thunderLp)
                    val rumble = sin(2.0 * Math.PI * 45.0 * sampleIndex / sRate).toFloat() * 0.2f
                    mixSample += (thunderLp * 3.0f + rumble * thunderBursts) * vThunder
                }

                // 6. Ceiling Fan
                if (vFan > 0.001f) {
                    fanPhase += 12.0f / sRate // 12Hz blade rotation
                    val fanMod = 0.75f + 0.25f * sin(2.0 * Math.PI * fanPhase).toFloat()
                    fanLp += 0.05f * (white * fanMod - fanLp)
                    mixSample += fanLp * 1.8f * vFan
                }

                // 7. Ocean Waves
                if (vOcean > 0.001f) {
                    oceanPhase += 0.1f / sRate // 0.1 Hz surge
                    val waveSurf = (0.5f + 0.5f * sin(2.0 * Math.PI * oceanPhase)).toFloat()
                    val waveMod = waveSurf * waveSurf
                    oceanLp += (0.01f + 0.03f * waveMod) * (white - oceanLp)
                    mixSample += oceanLp * waveMod * 2.5f * vOcean
                }

                // 8. Fireplace
                if (vFire > 0.001f) {
                    var crackle = 0f
                    if (random.nextFloat() < 0.0008f) {
                        crackle = (random.nextFloat() * 0.8f + 0.2f)
                    }
                    brownLast = (brownLast + (0.02f * white)) / 1.02f
                    mixSample += (brownLast * 1.2f + crackle) * vFire
                }

                // 9. Gentle Wind
                if (vWind > 0.001f) {
                    windPhase += 0.15f / sRate
                    val windSweep = 0.02f + 0.02f * (0.5f + 0.5f * sin(2.0 * Math.PI * windPhase)).toFloat()
                    windFilterState += windSweep * (white - windFilterState)
                    mixSample += windFilterState * 1.5f * vWind
                }

                // 10. Coffee Shop Ambient Drones
                if (vCoffee > 0.001f) {
                    dronePhase += 1f / sRate
                    val d1 = sin(2.0 * Math.PI * 110.0 * dronePhase).toFloat() * 0.15f
                    val d2 = sin(2.0 * Math.PI * 165.0 * dronePhase).toFloat() * 0.10f
                    val murm = (random.nextFloat() * 0.1f - 0.05f)
                    mixSample += (d1 + d2 + murm) * vCoffee
                }

                val scaledSample = mixSample * finalVol

                // Fast rational soft-knee saturation curve (eliminates heavy trigonometric atan calls in hot DSP loop)
                val absSample = abs(scaledSample)
                val output = (scaledSample / (1.0f + absSample * 0.45f)).coerceIn(-0.98f, 0.98f)

                floatBuffer[i] = output
            }

            // Convert float buffer to 16-bit PCM
            for (i in 0 until frameSize) {
                buffer[i] = (floatBuffer[i] * 32767f).toInt().toShort()
            }

            try {
                audioTrack?.write(buffer, 0, frameSize)
            } catch (_: Exception) {}
        }
    }
}
