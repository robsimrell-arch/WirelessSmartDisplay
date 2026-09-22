package com.antigravity.screensaver.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.antigravity.screensaver.model.BatteryInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BatteryStateTracker(private val context: Context) {

    fun getCurrentBatteryInfo(): BatteryInfo {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
            parseBatteryIntent(batteryStatus)
        } catch (e: Exception) {
            BatteryInfo()
        }
    }

    fun batteryInfoFlow(): Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    trySend(parseBatteryIntent(intent))
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        var isRegistered = false

        try {
            val initialIntent = ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isRegistered = true
            if (initialIntent != null) {
                trySend(parseBatteryIntent(initialIntent))
            }
        } catch (e: Exception) {
            // Fallback: query sticky intent directly without active receiver
            try {
                val sticky = context.registerReceiver(null, filter)
                trySend(parseBatteryIntent(sticky))
            } catch (_: Exception) {}
        }

        awaitClose {
            if (isRegistered) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {}
            }
        }
    }

    private fun parseBatteryIntent(intent: Intent?): BatteryInfo {
        if (intent == null) return BatteryInfo()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            50
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val isWireless = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

        // Calculate live charging wattage
        var calculatedWatts: Float? = null
        if (isCharging) {
            try {
                val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                var currentUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                if (currentUa == 0 || currentUa == Int.MIN_VALUE) {
                    currentUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0
                }

                val absCurrentUa = kotlin.math.abs(currentUa.toLong())
                if (voltageMv > 0 && absCurrentUa > 0) {
                    val rawWatts = (absCurrentUa * voltageMv) / 1_000_000_000f
                    if (rawWatts in 0.5f..120f) {
                        calculatedWatts = kotlin.math.round(rawWatts * 10) / 10f
                    }
                }

                // If hardware sensor isn't reporting instantaneous microamps, provide sensible baseline if wireless
                if (calculatedWatts == null && isWireless && status != BatteryManager.BATTERY_STATUS_FULL) {
                    calculatedWatts = 15.0f
                }
            } catch (_: Exception) {}
        }

        val wattsPrefix = if (calculatedWatts != null && status != BatteryManager.BATTERY_STATUS_FULL) {
            "${calculatedWatts}W "
        } else {
            ""
        }

        val statusText = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "Fully Charged"
            isWireless -> "${wattsPrefix}Wireless Charging"
            plugged == BatteryManager.BATTERY_PLUGGED_AC -> "${wattsPrefix}Fast Charging (AC)"
            plugged == BatteryManager.BATTERY_PLUGGED_USB -> "${wattsPrefix}Cable Charging (USB)"
            isCharging -> "${wattsPrefix}Charging"
            else -> "Discharging"
        }

        return BatteryInfo(
            level = batteryPct,
            isCharging = isCharging,
            isWireless = isWireless,
            pluggedType = plugged,
            chargingWatts = calculatedWatts,
            statusText = statusText
        )
    }

    companion object {
        fun isPluggedWireless(intent: Intent?): Boolean {
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            return plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
        }

        fun isPluggedWired(intent: Intent?): Boolean {
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB
        }

        fun isCurrentlyChargingWirelessly(context: Context): Boolean {
            return try {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                isPluggedWireless(intent)
            } catch (e: Exception) {
                false
            }
        }

        fun isCurrentlyChargingWired(context: Context): Boolean {
            return try {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                isPluggedWired(intent)
            } catch (e: Exception) {
                false
            }
        }
    }
}
