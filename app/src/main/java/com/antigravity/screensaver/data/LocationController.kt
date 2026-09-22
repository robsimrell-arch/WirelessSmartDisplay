package com.antigravity.screensaver.data

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.util.Log

class LocationController(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isManagingLocation: Boolean
        get() = prefs.getBoolean(KEY_IS_MANAGING_LOCATION, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_MANAGING_LOCATION, value).apply()

    var originalLocationMode: Int
        get() = prefs.getInt(KEY_ORIGINAL_LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
        private set(value) = prefs.edit().putInt(KEY_ORIGINAL_LOCATION_MODE, value).apply()

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
     * Mutes Location by setting LOCATION_MODE to OFF (0).
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

            // Location is currently ON. Save original mode and turn OFF.
            originalLocationMode = currentMode
            val success = Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )

            if (success) {
                isManagingLocation = true
                Log.i(TAG, "Muted Location for Night Mode. Original mode was: $currentMode")
            } else {
                Log.w(TAG, "Settings.Secure.putInt returned false while muting location")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error muting Location", e)
            return false
        }
    }

    /**
     * Restores the previous Location mode if and only if this screensaver was the one that muted it.
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

            val success = Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                restoreMode
            )
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
