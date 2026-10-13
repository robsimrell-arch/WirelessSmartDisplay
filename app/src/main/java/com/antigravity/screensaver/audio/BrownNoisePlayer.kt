package com.antigravity.screensaver.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Real-time Brownian noise synthesizer using Android's streaming AudioTrack API.
 * Produces a soft, deep low-frequency acoustic rumble (6 dB/octave attenuation)
 * ideal for sleep and background masking with zero loop artifacts or static files.
 *
 * Supports seamless continuous playback when audio routes switch (e.g. Bluetooth speaker
 * connects or disconnects) with automatic recovery.
 */
class BrownNoisePlayer {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var synthesisJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE_SAMPLES = 2048
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
            android.util.Log.e("BrownNoisePlayer", "Failed to recreate AudioTrack: ${e.message}")
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

            synthesisJob = scope.launch {
                val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
                var lastVal = 0.0f
                val decay = 0.985f
                val gain = 0.08f

                // Initial soft ramp to avoid pop
                var ramp = 0.0f
                val rampStep = 0.0005f

                while (isActive && _isPlaying.value) {
                    for (i in buffer.indices) {
                        val white = (Random.nextFloat() * 2f - 1f)
                        lastVal = (lastVal * decay) + (white * gain)

                        if (ramp < 1.0f) {
                            ramp = (ramp + rampStep).coerceAtMost(1.0f)
                        }

                        val sample = (lastVal.coerceIn(-1.0f, 1.0f) * ramp * 32767f).toInt().toShort()
                        buffer[i] = sample
                    }

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
                            android.util.Log.w("BrownNoisePlayer", "AudioTrack write failed ($written); recovering route...")
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
            android.util.Log.e("BrownNoisePlayer", "Failed to start brown noise audio: ${e.message}")
            stop()
        }
    }

    @Synchronized
    fun stop() {
        _isPlaying.value = false
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
                    track.release()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BrownNoisePlayer", "Error releasing AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }

    fun release() {
        stop()
    }
}
