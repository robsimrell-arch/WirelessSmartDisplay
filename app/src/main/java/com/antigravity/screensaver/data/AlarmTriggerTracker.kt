package com.antigravity.screensaver.data

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Multi-channel watchdog and detector for active morning alarms.
 *
 * Employs 4 complementary detection layers to guarantee the screensaver yields
 * the moment a system or Google Clock alarm triggers:
 *
 * 1. AudioManager USAGE_ALARM stream monitoring (API 26+)
 * 2. Exported BroadcastReceiver for system & OEM alarm alert actions
 * 3. NextAlarmClock trigger-time millisecond watchdog
 * 4. Window focus loss coordinator (detects when AlarmActivity grabs focus)
 */
class AlarmTriggerTracker(
    private val context: Context,
    private val onAlarmTriggered: () -> Unit
) {

    companion object {
        private const val TAG = "AlarmTriggerTracker"

        // Broadcast actions fired when an alarm fires across stock Android, Google Clock, and OEMs
        private val ALARM_ALERT_ACTIONS = listOf(
            "com.google.android.deskclock.ALARM_ALERT",
            "com.google.android.deskclock.action.ALARM_ALERT",
            "com.android.deskclock.ALARM_ALERT",
            "android.app.action.NEXT_ALARM_CLOCK_CHANGED",
            "com.sec.android.app.clockpackage.ALARM_ALERT",
            "com.samsung.sec.android.clockpackage.alarm.ALARM_ALERT",
            "com.sonyericsson.alarm.ALARM_ALERT",
            "org.codeaurora.poweroffalarm.action.UPDATE_ALARM"
        )

        // Threshold around scheduled alarm time to consider an alarm "active" (e.g. within 5 minutes)
        private const val ALARM_ACTIVE_WINDOW_MS = 5 * 60 * 1000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive: StateFlow<Boolean> = _isAlarmActive.asStateFlow()

    private var lastScheduledAlarmTime: Long = 0L
    private var isStarted = false

    /**
     * Start monitoring all alarm channels.
     */
    fun start() {
        if (isStarted) return
        isStarted = true

        updateScheduledAlarmTime()
        registerAudioPlaybackListener()
        registerBroadcastReceiver()
        startWatchdog()

        Log.d(TAG, "AlarmTriggerTracker started. Next alarm at: $lastScheduledAlarmTime")
    }

    /**
     * Stop and cleanup all listeners and coroutines.
     */
    fun stop() {
        if (!isStarted) return
        isStarted = false

        unregisterAudioPlaybackListener()
        unregisterBroadcastReceiver()
        watchdogJob?.cancel()
        watchdogJob = null
        _isAlarmActive.value = false

        Log.d(TAG, "AlarmTriggerTracker stopped.")
    }

    /**
     * Called when the screensaver or activity loses window focus (hasFocus == false).
     *
     * If an alarm is actively playing audio, or if current time is within the expected
     * alarm firing window, or if an external top-level activity (e.g. AlarmActivity or
     * incoming call) has grabbed focus, signals immediate trigger so the screensaver yields.
     */
    fun onWindowFocusLost() {
        if (!isStarted) return

        val now = System.currentTimeMillis()
        val isNearAlarmTime = lastScheduledAlarmTime > 0L &&
                now >= (lastScheduledAlarmTime - 5000L) &&
                now <= (lastScheduledAlarmTime + ALARM_ACTIVE_WINDOW_MS)

        val isAudioAlarm = isAlarmAudioPlaying()

        Log.d(TAG, "Window focus lost. isNearAlarmTime=$isNearAlarmTime, isAudioAlarm=$isAudioAlarm")

        if (isNearAlarmTime || isAudioAlarm || _isAlarmActive.value) {
            triggerAlarmEvent("Focus lost during active alarm window")
        } else {
            // Even if slightly outside the exact window, an activity took full-screen focus over us
            // (e.g., incoming phone call, alarm from another clock app, timer, or user action).
            // A screensaver must yield to foreground activities!
            triggerAlarmEvent("Focus lost to foreground activity")
        }
    }

    private fun triggerAlarmEvent(reason: String) {
        if (!isStarted) return
        Log.i(TAG, ">>> ALARM EVENT TRIGGERED ($reason) <<<")
        _isAlarmActive.value = true
        try {
            onAlarmTriggered()
        } catch (t: Throwable) {
            Log.e(TAG, "Error invoking onAlarmTriggered callback: ${t.message}", t)
        }
    }

    private fun isAlarmAudioPlaying(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            try {
                val configs = audioManager.activePlaybackConfigurations
                return configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking active playback configurations: ${e.message}")
            }
        }
        return false
    }

    private fun updateScheduledAlarmTime() {
        try {
            val nextAlarm = alarmManager?.nextAlarmClock
            if (nextAlarm != null) {
                lastScheduledAlarmTime = nextAlarm.triggerTime
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting next alarm clock: ${e.message}")
        }
    }

    /**
     * Channel 1: Audio stream monitoring for USAGE_ALARM.
     */
    private fun registerAudioPlaybackListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            try {
                audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                        val alarmPlaying = configs.any {
                            it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
                        }
                        if (alarmPlaying) {
                            Log.d(TAG, "AudioPlaybackCallback: USAGE_ALARM playback detected!")
                            triggerAlarmEvent("USAGE_ALARM audio playback detected")
                        } else if (_isAlarmActive.value && !isAlarmAudioPlaying()) {
                            // Alarm sound stopped (user snoozed or dismissed)
                            _isAlarmActive.value = false
                        }
                    }
                }
                audioManager.registerAudioPlaybackCallback(
                    audioPlaybackCallback!!,
                    Handler(Looper.getMainLooper())
                )
                Log.d(TAG, "Registered AudioManager.AudioPlaybackCallback successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register AudioPlaybackCallback: ${e.message}")
            }
        }
    }

    private fun unregisterAudioPlaybackListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            audioPlaybackCallback?.let {
                try {
                    audioManager.unregisterAudioPlaybackCallback(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unregister AudioPlaybackCallback: ${e.message}")
                }
                audioPlaybackCallback = null
            }
        }
    }

    /**
     * Channel 2: Broadcast receiver for system & OEM alarm actions.
     */
    private fun registerBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                Log.d(TAG, "Received alarm broadcast action: $action")

                if (action == "android.app.action.NEXT_ALARM_CLOCK_CHANGED") {
                    val prevTime = lastScheduledAlarmTime
                    updateScheduledAlarmTime()
                    val now = System.currentTimeMillis()

                    // If the scheduled alarm just fired (prevTime was within 60s of now), trigger wake-up
                    if (prevTime > 0L && abs(now - prevTime) < 60_000L) {
                        triggerAlarmEvent("NEXT_ALARM_CLOCK_CHANGED at trigger time")
                    }
                } else {
                    // Specific alarm alert intent from Google Clock or OEM clock
                    triggerAlarmEvent("Broadcast action $action")
                }
            }
        }

        val filter = IntentFilter().apply {
            for (action in ALARM_ALERT_ACTIONS) {
                addAction(action)
            }
        }

        try {
            // Must be RECEIVER_EXPORTED on API 33+ to receive broadcasts from Google Clock
            ContextCompat.registerReceiver(
                context,
                broadcastReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.d(TAG, "Registered alarm alert broadcast receiver (RECEIVER_EXPORTED)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register alarm broadcast receiver: ${e.message}")
        }
    }

    private fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister alarm broadcast receiver: ${e.message}")
            }
            broadcastReceiver = null
        }
    }

    /**
     * Channel 3: Coroutine watchdog that monitors the exact trigger time.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                updateScheduledAlarmTime()
                val now = System.currentTimeMillis()

                if (lastScheduledAlarmTime > 0L) {
                    val diff = lastScheduledAlarmTime - now
                    if (diff in 0..1500L) {
                        // Alarm is about to fire or firing right now!
                        delay(diff.coerceAtLeast(0L))
                        Log.d(TAG, "Watchdog: Alarm trigger time reached ($lastScheduledAlarmTime)")
                        triggerAlarmEvent("Alarm trigger time reached")
                        break
                    } else if (diff < 0 && diff >= -ALARM_ACTIVE_WINDOW_MS) {
                        // Current time is within the active alarm window
                        if (isAlarmAudioPlaying()) {
                            triggerAlarmEvent("Watchdog: Alarm active and audio playing")
                            break
                        }
                    }
                }

                // Periodic poll every 5 seconds
                delay(5000L)
            }
        }
    }
}
