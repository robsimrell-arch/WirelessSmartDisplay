package com.antigravity.screensaver.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class AmbientLightTracker(private val context: Context) {

    companion object {
        /**
         * Lux threshold at or below which the environment is considered dark enough for Night Mode.
         */
        const val DARK_THRESHOLD_LUX = 5.0f

        /**
         * Lux threshold at or above which the environment is considered bright enough to exit Night Mode.
         * Set well above DARK_THRESHOLD_LUX (35 lux vs 5 lux = 30-lux deadband) so that under-display
         * light sensor noise and screen self-illumination/LBE transients never cause Day/Night oscillation.
         */
        const val BRIGHT_THRESHOLD_LUX = 35.0f

        /**
         * Exponential Moving Average smoothing factor for incoming sensor readings.
         */
        const val EMA_ALPHA = 0.22f

        /**
         * Sustained duration (ms) required before transitioning into Night Mode.
         */
        const val ENTER_DARK_CONFIRM_MS = 2_500L

        /**
         * Sustained duration (ms) required before exiting Night Mode back to Day Mode.
         */
        const val EXIT_DARK_CONFIRM_MS = 3_500L

        /**
         * Minimum cooldown (ms) after any Night/Day mode switch before an opposite switch is allowed,
         * giving the OLED panel and under-display light bleed estimator time to settle.
         */
        const val TRANSITION_COOLDOWN_MS = 6_000L

        /**
         * Pure function with hysteresis to determine whether ambient light represents dark room conditions.
         * Hysteresis prevents rapid toggling around the threshold.
         */
        fun evaluateDarkState(currentDark: Boolean, lux: Float): Boolean {
            return when {
                lux <= DARK_THRESHOLD_LUX -> true
                lux >= BRIGHT_THRESHOLD_LUX -> false
                else -> currentDark
            }
        }

        /**
         * Computes a low-pass Exponential Moving Average (EMA) step to filter out transient
         * single-sample spikes or dips from under-display ambient light sensors.
         */
        fun computeSmoothedLux(previousSmoothed: Float, rawLux: Float, alpha: Float = EMA_ALPHA): Float {
            val clampedAlpha = alpha.coerceIn(0.01f, 1.0f)
            return clampedAlpha * rawLux + (1.0f - clampedAlpha) * previousSmoothed
        }

        /**
         * Evaluates whether a candidate transition has been continuously sustained long enough
         * and satisfies the post-transition cooldown window.
         */
        fun shouldCommitTransition(
            targetDark: Boolean,
            candidateElapsedMs: Long,
            sinceLastTransitionMs: Long
        ): Boolean {
            if (sinceLastTransitionMs < TRANSITION_COOLDOWN_MS) return false
            val requiredConfirmMs = if (targetDark) ENTER_DARK_CONFIRM_MS else EXIT_DARK_CONFIRM_MS
            return candidateElapsedMs >= requiredConfirmMs
        }
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    val hasSensor: Boolean get() = lightSensor != null

    @Volatile
    private var manualOverrideDark: Boolean? = null

    @Volatile
    private var lastTransitionElapsedMs: Long = 0L

    /**
     * Synchronizes tracker state when the user manually toggles Night Mode via screen tap,
     * applying the transition cooldown so the sensor doesn't immediately fight the user's tap.
     */
    fun notifyManualToggle(isDarkNow: Boolean) {
        manualOverrideDark = isDarkNow
        lastTransitionElapsedMs = SystemClock.elapsedRealtime()
    }

    fun ambientLightFlow(): Flow<Boolean> = callbackFlow {
        if (sensorManager == null || lightSensor == null) {
            awaitClose {}
            return@callbackFlow
        }

        var isDark = false
        var hasEmittedInitial = false
        var smoothedLux = 0f
        var latestRawLux = 0f
        var pendingTargetDark: Boolean? = null
        var pendingSinceElapsedMs = 0L
        var confirmJob: Job? = null

        fun evaluateAndSchedule(nowMs: Long) {
            // Sync if user manually toggled night mode
            val manual = manualOverrideDark
            if (manual != null) {
                manualOverrideDark = null
                if (isDark != manual) {
                    isDark = manual
                    pendingTargetDark = null
                    confirmJob?.cancel()
                    confirmJob = null
                }
            }

            // Require both raw and smoothed lux to support the transition, or smoothed lux after convergence
            val desiredDark = evaluateDarkState(isDark, smoothedLux)
            val rawSupportsDesired = evaluateDarkState(isDark, latestRawLux) == desiredDark

            if (desiredDark == isDark || !rawSupportsDesired) {
                // Inside hysteresis deadband or raw reading disagrees -> cancel any pending transition
                pendingTargetDark = null
                confirmJob?.cancel()
                confirmJob = null
                return
            }

            if (pendingTargetDark != desiredDark) {
                pendingTargetDark = desiredDark
                pendingSinceElapsedMs = nowMs
            }

            val candidateElapsed = nowMs - pendingSinceElapsedMs
            val sinceLastTransition = if (lastTransitionElapsedMs == 0L) {
                TRANSITION_COOLDOWN_MS
            } else {
                nowMs - lastTransitionElapsedMs
            }

            if (shouldCommitTransition(desiredDark, candidateElapsed, sinceLastTransition)) {
                pendingTargetDark = null
                confirmJob?.cancel()
                confirmJob = null
                isDark = desiredDark
                lastTransitionElapsedMs = nowMs
                trySend(isDark)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                    val lux = event.values.firstOrNull() ?: return
                    val nowMs = SystemClock.elapsedRealtime()
                    latestRawLux = lux

                    if (!hasEmittedInitial) {
                        hasEmittedInitial = true
                        smoothedLux = lux
                        isDark = evaluateDarkState(false, lux)
                        lastTransitionElapsedMs = nowMs
                        trySend(isDark)
                        return
                    }

                    smoothedLux = computeSmoothedLux(smoothedLux, lux)
                    evaluateAndSchedule(nowMs)

                    // Because Sensor.TYPE_LIGHT is an on-change sensor that may stop emitting once
                    // the room reaches a steady state (e.g., 0.0 lux), run a convergence ticker
                    // whenever latestRawLux would trigger a state change.
                    val rawTargetDark = evaluateDarkState(isDark, latestRawLux)
                    if (rawTargetDark != isDark && confirmJob?.isActive != true) {
                        confirmJob = launch {
                            while (true) {
                                delay(250L)
                                val tickMs = SystemClock.elapsedRealtime()
                                smoothedLux = computeSmoothedLux(smoothedLux, latestRawLux)
                                evaluateAndSchedule(tickMs)
                                if (evaluateDarkState(isDark, latestRawLux) == isDark) {
                                    break
                                }
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = sensorManager.registerListener(
            listener,
            lightSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        awaitClose {
            confirmJob?.cancel()
            if (registered) {
                try {
                    sensorManager.unregisterListener(listener)
                } catch (_: Exception) {}
            }
        }
    }
}
