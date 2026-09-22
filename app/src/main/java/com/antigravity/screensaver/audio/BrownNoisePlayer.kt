package com.antigravity.screensaver.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 2, BUFFER_SIZE_SAMPLES * 2 * 2)

            val track = AudioTrack.Builder()
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
                    val written = track.write(buffer, 0, buffer.size)
                    if (written < 0) {
                        break
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
