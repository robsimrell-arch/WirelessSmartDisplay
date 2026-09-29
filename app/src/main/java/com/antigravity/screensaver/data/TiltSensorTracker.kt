package com.antigravity.screensaver.data

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Monitors the device's 3D physical inclination (elevation angle) relative to a flat horizontal surface,
 * and tracks device rotation (Portrait vs. Landscape) without the restrictive tilt cutoffs and dead zones
 * found in standard Android [android.view.OrientationEventListener].
 */
class TiltSensorTracker(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Hardware accelerometer: rapid, low-latency, and immediately responsive
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    // Filtered gravity sensor
    private val gravitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    // Primary tilt sensor (prefer gravity, fallback to accelerometer)
    private val tiltSensor: Sensor? = gravitySensor ?: accelerometer

    val hasSensor: Boolean get() = tiltSensor != null || accelerometer != null

    private val _currentInclineDegrees = MutableStateFlow(0f)
    val currentInclineDegrees: StateFlow<Float> = _currentInclineDegrees.asStateFlow()

    var lastComputedOrientation: Int = getSystemInitialOrientation(context)
        private set

    fun getCurrentOrientation(): Int = lastComputedOrientation

    init {
        warmUpSensor()
    }

    /**
     * Rapidly samples the hardware accelerometer upon tracker creation so that the device's
     * physical orientation on a dock is known before the first frame is composed.
     */
    private fun warmUpSensor() {
        val sm = sensorManager ?: return
        val sensorToWarm = accelerometer ?: tiltSensor ?: return

        val warmListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size >= 3) {
                    val computed = calculateScreenOrientation(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                        lastComputedOrientation
                    )
                    lastComputedOrientation = computed
                    val incline = calculateInclineDegrees(
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                    _currentInclineDegrees.value = incline
                    try {
                        sm.unregisterListener(this)
                    } catch (_: Throwable) {}
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        try {
            sm.registerListener(warmListener, sensorToWarm, SensorManager.SENSOR_DELAY_FASTEST)
        } catch (_: Throwable) {}
    }

    companion object {
        const val DEFAULT_THRESHOLD_DEGREES = 30
        const val FLAT_GRACE_PERIOD_MS = 2000L

        /**
         * Inspects current display rotation and configuration to initialize orientation synchronously
         * on launch before waiting for the first sensor event.
         */
        fun getSystemInitialOrientation(context: Context): Int {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val rotation = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display?.rotation ?: windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION")
                    windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                }
            } catch (_: Throwable) {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }

            val configOrientation = context.resources.configuration.orientation

            return when {
                rotation == Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                rotation == Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                rotation == Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                configOrientation == Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        /**
         * Pure mathematical calculation of elevation angle above the horizontal table plane.
         * Returns an angle in degrees from 0° (flat) to 90° (vertical).
         */
        fun calculateInclineDegrees(x: Float, y: Float, z: Float): Float {
            val magnitude = sqrt((x * x + y * y + z * z).toDouble())
            if (magnitude < 0.1) return 0f

            val normalizedZ = (Math.abs(z.toDouble()) / magnitude).coerceIn(0.0, 1.0)
            val angleFromVerticalRad = acos(normalizedZ)
            val inclineDegrees = (angleFromVerticalRad * (180.0 / PI)).toFloat()
            return inclineDegrees.coerceIn(0f, 90f)
        }

        /**
         * 3D-aware screen rotation calculation from gravity vector.
         *
         * Unlike standard AOSP OrientationEventListener which drops into ORIENTATION_UNKNOWN
         * whenever the phone is tilted back at an angle on a stand, this function projects
         * gravity onto the screen plane (X, Y) and provides full 360° coverage with hysteresis,
         * working seamlessly on propped-up docks.
         */
        fun calculateScreenOrientation(
            x: Float,
            y: Float,
            z: Float,
            currentOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ): Int {
            // Planar magnitude on the screen surface
            val planarMagnitude = sqrt((x * x + y * y).toDouble())
            // If phone is lying completely flat, preserve existing orientation
            if (planarMagnitude < 0.8) {
                return currentOrientation
            }

            // Standard Android sensor mapping without AOSP's aggressive tilt cutoff:
            val sensorX = -x.toDouble()
            val sensorY = -y.toDouble()
            var angle = (90.0 - atan2(-sensorY, sensorX) * (180.0 / PI)).toFloat()
            while (angle >= 360f) angle -= 360f
            while (angle < 0f) angle += 360f

            // Full 360° coverage with 10° hysteresis around the 45° boundaries:
            return when (currentOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> {
                    // Landscape is centered at 270° (225°..315°)
                    if (angle in 215f..325f) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else evaluateQuadrant(angle)
                }
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> {
                    // Reverse Landscape is centered at 90° (45°..135°)
                    if (angle in 35f..145f) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    else evaluateQuadrant(angle)
                }
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> {
                    if (angle in 215f..325f) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else if (angle in 35f..145f) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    else evaluateQuadrant(angle)
                }
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> {
                    // Reverse Portrait is centered at 180° (135°..225°)
                    if (angle in 125f..235f) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    else evaluateQuadrant(angle)
                }
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> {
                    if (angle in 125f..235f) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    else if (angle >= 305f || angle <= 55f) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else evaluateQuadrant(angle)
                }
                else -> {
                    // Portrait is centered at 0° (315°..45°)
                    if (angle >= 305f || angle <= 55f) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else evaluateQuadrant(angle)
                }
            }
        }

        private fun evaluateQuadrant(angle: Float): Int {
            return when {
                angle in 45f..135f -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                angle in 135f..225f -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                angle in 225f..315f -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    /**
     * Flow that continuously reports the raw elevation angle in degrees.
     */
    fun inclineAngleFlow(): Flow<Float> = callbackFlow {
        val activeSensor = tiltSensor ?: accelerometer
        if (sensorManager == null || activeSensor == null) {
            trySend(0f)
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size >= 3) {
                    val angle = calculateInclineDegrees(
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                    _currentInclineDegrees.value = angle
                    trySend(angle)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Flow that continuously reports device orientation (Portrait / Landscape / Reverse Landscape / Reverse Portrait)
     * computed from 3D gravity, guaranteeing immediate orientation detection when resting propped up on a stand.
     */
    fun orientationFlow(): Flow<Int> = callbackFlow {
        val activeSensor = tiltSensor ?: accelerometer
        if (sensorManager == null || activeSensor == null) {
            trySend(lastComputedOrientation)
            awaitClose {}
            return@callbackFlow
        }

        var current = lastComputedOrientation

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size >= 3) {
                    val newOrientation = calculateScreenOrientation(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                        current
                    )
                    if (newOrientation != current) {
                        current = newOrientation
                        lastComputedOrientation = newOrientation
                        trySend(newOrientation)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_GAME)

        // Send current orientation immediately
        trySend(current)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Returns a Flow that emits whether the phone is propped up (true) or flat (false).
     * When transitioning from propped up to flat, a grace period of [flatGracePeriodMs]
     * is enforced so that minor bumps or temporary movements do not cause false triggers.
     */
    fun proppedUpStateFlow(
        thresholdDegrees: Int = DEFAULT_THRESHOLD_DEGREES,
        flatGracePeriodMs: Long = FLAT_GRACE_PERIOD_MS
    ): Flow<Boolean> = callbackFlow {
        if (sensorManager == null || tiltSensor == null) {
            // If no sensor is available, default to true so screensaver is not blocked
            trySend(true)
            awaitClose {}
            return@callbackFlow
        }

        var currentlyPropped = false
        var flatGraceJob: Job? = null
        val scope = CoroutineScope(Dispatchers.Default)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size >= 3) {
                    val angle = calculateInclineDegrees(
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                    _currentInclineDegrees.value = angle
                    val isAnglePropped = angle >= thresholdDegrees

                    if (isAnglePropped) {
                        flatGraceJob?.cancel()
                        flatGraceJob = null
                        if (!currentlyPropped) {
                            currentlyPropped = true
                            trySend(true)
                        }
                    } else {
                        // Angle is below threshold (laying flat)
                        if (currentlyPropped && flatGraceJob == null) {
                            flatGraceJob = scope.launch {
                                delay(flatGracePeriodMs)
                                currentlyPropped = false
                                trySend(false)
                            }
                        } else if (!currentlyPropped && flatGraceJob == null) {
                            // Already considered flat
                            trySend(false)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, tiltSensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            flatGraceJob?.cancel()
            sensorManager.unregisterListener(listener)
        }
    }
}
