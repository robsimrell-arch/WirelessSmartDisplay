package com.antigravity.screensaver.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Ensures Android system screensaver settings are correctly configured so that
 * the screensaver can activate while charging via cable or wireless dock.
 *
 * Specifically addresses a Google Pixel / Android 15 issue where the OS sets
 * `screensaver_restrict_to_wireless_charging = 1` and `screensaver_activate_on_sleep = 0`
 * by default if a wireless charging dock/stand was ever used, which prevents the screensaver
 * from activating on wired cable charging.
 */
class DreamSystemSettingsController(private val context: Context) {

    companion object {
        private const val TAG = "DreamSystemSettings"
        private const val OUR_DREAM_COMPONENT =
            "com.antigravity.screensaver/com.antigravity.screensaver.dream.WirelessChargingDreamService"

        @Volatile
        private var instance: DreamSystemSettingsController? = null

        fun getInstance(context: Context): DreamSystemSettingsController {
            return instance ?: synchronized(this) {
                instance ?: DreamSystemSettingsController(context.applicationContext).also { instance = it }
            }
        }
    }

    fun hasSecureSettingsPermission(): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether the current system settings allow wired charging screensavers.
     */
    fun isWiredChargingAllowed(): Boolean {
        return try {
            val resolver = context.contentResolver
            val activateOnSleep = Settings.Secure.getInt(resolver, "screensaver_activate_on_sleep", 0)
            val restrictToWireless = Settings.Secure.getInt(resolver, "screensaver_restrict_to_wireless_charging", 0)
            val screensaverEnabled = Settings.Secure.getInt(resolver, "screensaver_enabled", 0)
            screensaverEnabled == 1 && activateOnSleep == 1 && restrictToWireless == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Synchronizes Android system settings to ensure our screensaver runs reliably
     * on wired cable charging or wireless docks based on user preference.
     */
    fun syncSystemScreensaverPolicy(wirelessOnly: Boolean): Boolean {
        if (!hasSecureSettingsPermission()) {
            Log.w(TAG, "Cannot sync screensaver policy: WRITE_SECURE_SETTINGS not granted")
            return false
        }

        return try {
            val resolver = context.contentResolver

            // 1. Enable screensaver system-wide
            Settings.Secure.putInt(resolver, "screensaver_enabled", 1)

            // 2. Ensure screensaver activates upon screen sleep timeout while charging
            Settings.Secure.putInt(resolver, "screensaver_activate_on_sleep", 1)

            // 3. Configure wireless-only restriction (0 allows wired, 1 restricts to wireless)
            val restrictValue = if (wirelessOnly) 1 else 0
            Settings.Secure.putInt(resolver, "screensaver_restrict_to_wireless_charging", restrictValue)

            // 4. Ensure our DreamService is registered as active component
            val currentComponent = Settings.Secure.getString(resolver, "screensaver_components")
            if (currentComponent != OUR_DREAM_COMPONENT) {
                Settings.Secure.putString(resolver, "screensaver_components", OUR_DREAM_COMPONENT)
            }

            Log.d(TAG, "Screensaver system policy synced successfully (wirelessOnly=$wirelessOnly)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write screensaver secure settings: ${e.message}", e)
            false
        }
    }
}
