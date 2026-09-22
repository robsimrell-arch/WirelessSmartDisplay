package com.antigravity.screensaver.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AmbientLightTracker(private val context: Context) {

    companion object {
        const val DARK_THRESHOLD_LUX = 5.0f
        const val BRIGHT_THRESHOLD_LUX = 12.0f

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
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    val hasSensor: Boolean get() = lightSensor != null

    fun ambientLightFlow(): Flow<Boolean> = callbackFlow {
        if (sensorManager == null || lightSensor == null) {
            awaitClose {}
            return@callbackFlow
        }

        var isDark = false
        var hasEmittedInitial = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                    val lux = event.values.firstOrNull() ?: return
                    val newDark = evaluateDarkState(isDark, lux)
                    if (!hasEmittedInitial || newDark != isDark) {
                        hasEmittedInitial = true
                        isDark = newDark
                        trySend(isDark)
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
            if (registered) {
                try {
                    sensorManager.unregisterListener(listener)
                } catch (_: Exception) {}
            }
        }
    }
}
