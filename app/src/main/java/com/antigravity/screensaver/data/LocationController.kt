package com.antigravity.screensaver.data

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

class LocationController(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isManagingLocation: Boolean
        get() = prefs.getBoolean(KEY_IS_MANAGING_LOCATION, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_MANAGING_LOCATION, value).apply()

    var originalLocationMode: Int
        get() = prefs.getInt(KEY_ORIGINAL_LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
        private set(value) = prefs.edit().putInt(KEY_ORIGINAL_LOCATION_MODE, value).apply()

    var originalWifiScan: Int
        get() = prefs.getInt(KEY_ORIGINAL_WIFI_SCAN, -1)
        private set(value) = prefs.edit().putInt(KEY_ORIGINAL_WIFI_SCAN, value).apply()

    var originalBleScan: Int
        get() = prefs.getInt(KEY_ORIGINAL_BLE_SCAN, -1)
        private set(value) = prefs.edit().putInt(KEY_ORIGINAL_BLE_SCAN, value).apply()

    /**
     * Checks if the app has been granted WRITE_SECURE_SETTINGS permission.
     */
    fun hasPermission(): Boolean {
        return try {
            context.checkSelfPermission(PERMISSION_WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WRITE_SECURE_SETTINGS permission", e)
            false
        }
    }

    /**
     * Checks if Location is currently enabled in system settings.
     */
    fun isLocationEnabled(): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.isLocationEnabled == true
        } catch (e: Exception) {
            try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Internal multi-layer location toggle:
     * 1. Direct LocationManager.setLocationEnabledForUser via HiddenApiBypass (Android 9+)
     * 2. Settings.Secure.LOCATION_MODE (Legacy / compatibility)
     * 3. Settings.Secure.LOCATION_PROVIDERS_ALLOWED (Provider strings)
     */
    private fun setLocationEnabledInternal(enabled: Boolean): Boolean {
        var anySuccess = false

        // Layer 1: LocationManager.setLocationEnabledForUser (AOSP system API)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("")
            }
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val method = LocationManager::class.java.getMethod(
                    "setLocationEnabledForUser",
                    Boolean::class.javaPrimitiveType,
                    UserHandle::class.java
                )
                method.invoke(locationManager, enabled, Process.myUserHandle())
                Log.i(TAG, "Invoked LocationManager.setLocationEnabledForUser($enabled)")
                anySuccess = true
            }
        } catch (t: Throwable) {
            Log.d(TAG, "LocationManager.setLocationEnabledForUser invocation: ${t.message}")
        }

        // Layer 2: Settings.Secure.LOCATION_MODE
        try {
            val targetMode = if (enabled) {
                if (originalLocationMode != Settings.Secure.LOCATION_MODE_OFF) {
                    originalLocationMode
                } else {
                    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                }
            } else {
                Settings.Secure.LOCATION_MODE_OFF
            }
            val secSuccess = Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                targetMode
            )
            if (secSuccess) anySuccess = true
        } catch (e: Throwable) {
            Log.w(TAG, "Error updating Settings.Secure.LOCATION_MODE", e)
        }

        // Layer 3: Settings.Secure.LOCATION_PROVIDERS_ALLOWED
        try {
            if (!enabled) {
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                    ""
                )
            }
        } catch (_: Throwable) {}

        return anySuccess
    }

    /**
     * Mutes Location and background Wi-Fi/Bluetooth scanning for Night Mode.
     *
     * State preservation: If Location was already OFF before this call, we record that
     * state but do NOT mark `isManagingLocation = true`, so we will NOT enable Location
     * when night mode ends if the user had explicitly turned it off.
     *
     * @return true if Location was muted or already muted; false if permission is missing.
     */
    fun muteLocation(): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Cannot mute Location: WRITE_SECURE_SETTINGS not granted")
            return false
        }

        try {
            if (isManagingLocation) {
                return true
            }

            val isCurrentlyEnabled = isLocationEnabled()
            val currentMode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
            )

            if (!isCurrentlyEnabled || currentMode == Settings.Secure.LOCATION_MODE_OFF) {
                Log.i(TAG, "Location was already OFF ($currentMode). Will not take management ownership.")
                originalLocationMode = Settings.Secure.LOCATION_MODE_OFF
                isManagingLocation = false
                return true
            }

            // Location is currently ON. Save original mode and turn OFF across all layers.
            originalLocationMode = currentMode
            val success = setLocationEnabledInternal(false)

            // Mute background Wi-Fi scanning if active (prevents background Wi-Fi location probes)
            try {
                val currentWifiScan = Settings.Global.getInt(context.contentResolver, "wifi_scan_always_enabled", 0)
                if (currentWifiScan == 1) {
                    originalWifiScan = 1
                    Settings.Global.putInt(context.contentResolver, "wifi_scan_always_enabled", 0)
                    Log.i(TAG, "Muted wifi_scan_always_enabled for Night Mode")
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Could not mute wifi_scan_always_enabled: ${e.message}")
            }

            // Mute background Bluetooth scanning if active
            try {
                val currentBleScan = Settings.Global.getInt(context.contentResolver, "ble_scan_always_enabled", 0)
                if (currentBleScan == 1) {
                    originalBleScan = 1
                    Settings.Global.putInt(context.contentResolver, "ble_scan_always_enabled", 0)
                    Log.i(TAG, "Muted ble_scan_always_enabled for Night Mode")
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Could not mute ble_scan_always_enabled: ${e.message}")
            }

            if (success) {
                isManagingLocation = true
                Log.i(TAG, "Muted Location for Night Mode. Original mode was: $currentMode")
            } else {
                Log.w(TAG, "Failed to mute location")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error muting Location", e)
            return false
        }
    }

    /**
     * Enforces location & scanning silence during Night Mode if an external service toggled it back on.
     */
    fun enforceMute() {
        if (!hasPermission() || !isManagingLocation) return
        try {
            setLocationEnabledInternal(false)
            if (originalWifiScan == 1) {
                Settings.Global.putInt(context.contentResolver, "wifi_scan_always_enabled", 0)
            }
            if (originalBleScan == 1) {
                Settings.Global.putInt(context.contentResolver, "ble_scan_always_enabled", 0)
            }
            Log.i(TAG, "Enforced Location and scan mute during Night Mode")
        } catch (e: Throwable) {
            Log.e(TAG, "Error enforcing Location mute", e)
        }
    }

    /**
     * Restores the previous Location mode and scanning settings if and only if this screensaver was the one that muted them.
     */
    fun restoreLocation() {
        if (!hasPermission()) {
            return
        }

        if (!isManagingLocation) {
            Log.i(TAG, "Skipping Location restore: screensaver was not managing Location")
            return
        }

        try {
            val restoreMode = if (originalLocationMode != Settings.Secure.LOCATION_MODE_OFF) {
                originalLocationMode
            } else {
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
            }

            val success = setLocationEnabledInternal(true)

            // Restore Wi-Fi scanning if screensaver muted it
            if (originalWifiScan == 1) {
                try {
                    Settings.Global.putInt(context.contentResolver, "wifi_scan_always_enabled", 1)
                    Log.i(TAG, "Restored wifi_scan_always_enabled to 1")
                } catch (_: Throwable) {}
                originalWifiScan = -1
            }

            // Restore Bluetooth scanning if screensaver muted it
            if (originalBleScan == 1) {
                try {
                    Settings.Global.putInt(context.contentResolver, "ble_scan_always_enabled", 1)
                    Log.i(TAG, "Restored ble_scan_always_enabled to 1")
                } catch (_: Throwable) {}
                originalBleScan = -1
            }

            Log.i(TAG, "Restored Location to mode: $restoreMode (success=$success)")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring Location mode", e)
        } finally {
            isManagingLocation = false
            originalLocationMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
        }
    }

    /**
     * Reset on cleanup/destroy. Guarantees that any Location muted by the screensaver is restored.
     */
    fun reset() {
        restoreLocation()
    }

    companion object {
        private const val TAG = "LocationController"
        private const val PREFS_NAME = "location_controller_prefs"
        private const val KEY_IS_MANAGING_LOCATION = "key_is_managing_location"
        private const val KEY_ORIGINAL_LOCATION_MODE = "key_original_location_mode"
        private const val KEY_ORIGINAL_WIFI_SCAN = "key_original_wifi_scan"
        private const val KEY_ORIGINAL_BLE_SCAN = "key_original_ble_scan"
        const val PERMISSION_WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

        @Volatile
        private var instance: LocationController? = null

        fun getInstance(context: Context): LocationController {
            return instance ?: synchronized(this) {
                instance ?: LocationController(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Pure logic evaluator for unit testing: decides whether Location should be restored on exit.
         */
        fun evaluateShouldRestore(isManagingLocation: Boolean): Boolean {
            return isManagingLocation
        }

        /**
         * Pure logic evaluator for unit testing: determines if pre-existing state should take ownership.
         */
        fun shouldTakeOwnership(isCurrentlyEnabled: Boolean, currentMode: Int): Boolean {
            return isCurrentlyEnabled && currentMode != Settings.Secure.LOCATION_MODE_OFF
        }
    }
}
