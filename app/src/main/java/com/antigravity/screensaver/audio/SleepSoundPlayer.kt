package com.antigravity.screensaver.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.antigravity.screensaver.model.SleepSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time sleep sound synthesizer using Android's streaming AudioTrack API.
 *
 * - Plays any [SleepSound] (Brown, Pink, White, Rain, Ocean Waves, Fan), all generated in code.
 * - Switching sounds while playing crossfades smoothly (no clicks or pops).
 * - Optional sleep timer: at the end of the timer the sound fades out gently over
 *   [TIMER_FADE_OUT_MS] and stops. A timer of 0 minutes plays continuously.
 * - Keeps playing continuously when audio routes switch (e.g. Bluetooth speaker
 *   connects or disconnects) with automatic AudioTrack recovery.
 */
class SleepSoundPlayer {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSound = MutableStateFlow(SleepSound.BROWN)
    val currentSound: StateFlow<SleepSound> = _currentSound.asStateFlow()

    /** Timer duration selected by the user (0 = Off / continuous). */
    private val _timerMinutes = MutableStateFlow(0)
    val timerMinutes: StateFlow<Int> = _timerMinutes.asStateFlow()

    /** [SystemClock.elapsedRealtime] at which playback will have fully faded out, or null. */
    private val _timerEndsAtElapsedMs = MutableStateFlow<Long?>(null)
    val timerEndsAtElapsedMs: StateFlow<Long?> = _timerEndsAtElapsedMs.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var synthesisJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Generator requested by the UI; the synthesis loop crossfades to it. */
    @Volatile
    private var requestedSound: SleepSound = SleepSound.BROWN

    companion object {
        private const val SAMPLE_RATE = SoundGenerators.SAMPLE_RATE
        private const val BUFFER_SIZE_SAMPLES = 2048
        const val TIMER_FADE_OUT_MS = 60_000L
        private const val CROSSFADE_SAMPLES = SAMPLE_RATE / 2 // 0.5 s
        private const val START_RAMP_SAMPLES = SAMPLE_RATE / 2 // 0.5 s soft start

        /**
         * Gain multiplier applied for the sleep timer fade-out.
         * Returns 1.0 until the last [fadeMs] milliseconds, then ramps linearly to 0.0.
         */
        fun computeTimerFadeGain(remainingMs: Long?, fadeMs: Long = TIMER_FADE_OUT_MS): Float {
            if (remainingMs == null) return 1f
            if (remainingMs <= 0L) return 0f
            if (fadeMs <= 0L || remainingMs >= fadeMs) return 1f
            return (remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
        }
    }

    /** Restores the user's remembered sound + timer choice without starting playback. */
    fun restore(sound: SleepSound, timerMinutes: Int) {
        requestedSound = sound
        _currentSound.value = sound
        _timerMinutes.value = timerMinutes.coerceAtLeast(0)
    }

    fun setSound(sound: SleepSound) {
        requestedSound = sound
        _currentSound.value = sound
    }

    /**
     * Sets the sleep timer. If audio is playing the countdown (re)starts now;
     * otherwise it starts when playback starts. 0 = Off (play continuously).
     */
    fun setTimerMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(0)
        _timerMinutes.value = safeMinutes
        if (_isPlaying.value) armTimer(safeMinutes)
    }

    private fun armTimer(minutes: Int) {
        _timerEndsAtElapsedMs.value = if (minutes > 0) {
            SystemClock.elapsedRealtime() + minutes * 60_000L
        } else {
            null
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize * 2, BUFFER_SIZE_SAMPLES * 2 * 2)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    @Synchronized
    private fun rebuildAudioTrack(): AudioTrack? {
        try {
            audioTrack?.let { track ->
                try {
                    track.pause()
                    track.flush()
                    track.stop()
                } catch (_: Exception) {}
                track.release()
            }
        } catch (_: Exception) {}

        return try {
            val newTrack = createAudioTrack()
            newTrack.play()
            audioTrack = newTrack
            newTrack
        } catch (e: Exception) {
            android.util.Log.e("SleepSoundPlayer", "Failed to recreate AudioTrack: ${e.message}")
            audioTrack = null
            null
        }
    }

    @Synchronized
    fun toggle() {
        if (_isPlaying.value) {
            stop()
        } else {
            play()
        }
    }

    @Synchronized
    fun play() {
        if (_isPlaying.value) return

        try {
            val track = createAudioTrack()
            track.play()
            audioTrack = track
            _isPlaying.value = true
            armTimer(_timerMinutes.value)

            synthesisJob = scope.launch {
                val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
                var activeSound = requestedSound
                var activeGenerator = SoundGenerators.create(activeSound)
                var fadingGenerator: SoundGenerator? = null
                var crossfadePos = 0

                var startRamp = 0
                var lastFadeGain = computeTimerFadeGain(remainingMs())

                while (isActive && _isPlaying.value) {
                    // Sound switch requested -> begin crossfade from the old generator
                    val wanted = requestedSound
                    if (wanted != activeSound) {
                        fadingGenerator = activeGenerator
                        activeGenerator = SoundGenerators.create(wanted)
                        activeSound = wanted
                        crossfadePos = 0
                    }

                    // Sleep timer: compute fade gain at buffer boundaries, interpolate per sample
                    val remaining = remainingMs()
                    if (remaining != null && remaining <= 0L) {
                        // Timer finished: fully faded out -> stop playback
                        scope.launch { stop() }
                        break
                    }
                    val targetFadeGain = computeTimerFadeGain(remaining)
                    val fadeStep = (targetFadeGain - lastFadeGain) / buffer.size

                    for (i in buffer.indices) {
                        var sample = activeGenerator.next()

                        val fading = fadingGenerator
                        if (fading != null) {
                            val t = crossfadePos.toFloat() / CROSSFADE_SAMPLES
                            sample = sample * t + fading.next() * (1f - t)
                            crossfadePos++
                            if (crossfadePos >= CROSSFADE_SAMPLES) fadingGenerator = null
                        }

                        if (startRamp < START_RAMP_SAMPLES) {
                            sample *= startRamp.toFloat() / START_RAMP_SAMPLES
                            startRamp++
                        }

                        lastFadeGain += fadeStep
                        sample *= lastFadeGain.coerceIn(0f, 1f)

                        buffer[i] = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    lastFadeGain = targetFadeGain

                    val currentTrack = audioTrack
                    if (currentTrack == null) {
                        if (isActive && _isPlaying.value) {
                            delay(100L)
                            rebuildAudioTrack()
                            continue
                        } else {
                            break
                        }
                    }

                    val written = try {
                        currentTrack.write(buffer, 0, buffer.size)
                    } catch (e: Exception) {
                        -1
                    }

                    // On Bluetooth disconnect or route switch, keep playing continuously
                    if (written < 0) {
                        if (isActive && _isPlaying.value) {
                            android.util.Log.w("SleepSoundPlayer", "AudioTrack write failed ($written); recovering route...")
                            delay(150L)
                            rebuildAudioTrack()
                            continue
                        } else {
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SleepSoundPlayer", "Failed to start sleep sound audio: ${e.message}")
            stop()
        }
    }

    private fun remainingMs(): Long? =
        _timerEndsAtElapsedMs.value?.let { it - SystemClock.elapsedRealtime() }

    @Synchronized
    fun stop() {
        _isPlaying.value = false
        _timerEndsAtElapsedMs.value = null
        synthesisJob?.cancel()
        synthesisJob = null

        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        track.pause()
                        track.flush()
                        track.stop()
                    } catch (_: Exception) {}
                }
                track.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("SleepSoundPlayer", "Error releasing AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
