package com.antigravity.screensaver.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioOutputState(
    val isBluetooth: Boolean = false,
    val deviceName: String = "Phone Speaker"
)

/**
 * Tracks current audio output devices (Bluetooth speakers, wired headsets, phone speaker)
 * in real-time using AudioManager's AudioDeviceCallback.
 */
class AudioDeviceTracker(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _outputState = MutableStateFlow(getCurrentOutputState())
    val outputState: StateFlow<AudioOutputState> = _outputState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _outputState.value = getCurrentOutputState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _outputState.value = getCurrentOutputState()
        }
    }

    init {
        try {
            audioManager?.registerAudioDeviceCallback(deviceCallback, handler)
        } catch (e: Exception) {
            android.util.Log.e("AudioDeviceTracker", "Failed to register audio device callback: ${e.message}")
        }
    }

    fun getCurrentOutputState(): AudioOutputState {
        val manager = audioManager ?: return AudioOutputState()
        val devices = try {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            emptyArray()
        }

        // 1. Check for active/connected Bluetooth audio devices
        for (device in devices) {
            if (isBluetoothDevice(device)) {
                val name = device.productName?.toString()?.trim()
                val displayName = if (!name.isNullOrBlank()) name else "Bluetooth Speaker"
                return AudioOutputState(
                    isBluetooth = true,
                    deviceName = displayName
                )
            }
        }

        // 2. Check for wired headset or USB audio
        for (device in devices) {
            if (isWiredOrUsbDevice(device)) {
                val name = device.productName?.toString()?.trim()
                val displayName = if (!name.isNullOrBlank()) name else "Headphones"
                return AudioOutputState(
                    isBluetooth = false,
                    deviceName = displayName
                )
            }
        }

        // 3. Fallback to built-in speaker
        return AudioOutputState(
            isBluetooth = false,
            deviceName = "Phone Speaker"
        )
    }

    private fun isBluetoothDevice(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (device.type) {
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
                        else -> false
                    }
                } else false
            }
        }
    }

    private fun isWiredOrUsbDevice(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> false
        }
    }

    fun release() {
        try {
            audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
    }
}
