package com.antigravity.screensaver.data

import android.content.Context
import android.content.SharedPreferences
import com.antigravity.screensaver.model.DisplaySettings
import com.antigravity.screensaver.model.SleepSound
import com.antigravity.screensaver.model.sanitizeSleepTimerMinutes
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val migrated = prefs.getBoolean(KEY_WIRELESS_ONLY_MIGRATED, false)
        if (!migrated) {
            prefs.edit()
                .putBoolean(KEY_WIRELESS_ONLY, false)
                .putBoolean(KEY_WIRELESS_ONLY_MIGRATED, true)
                .apply()
        }
        if (LEGACY_REMOVED_KEYS.any { prefs.contains(it) }) {
            val editor = prefs.edit()
            LEGACY_REMOVED_KEYS.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    fun getSettings(): DisplaySettings {
        return DisplaySettings(
            wirelessOnly = prefs.getBoolean(KEY_WIRELESS_ONLY, false),
            burnInProtection = prefs.getBoolean(KEY_BURN_IN_PROTECTION, true),
            nightModeEnabled = prefs.getBoolean(KEY_NIGHT_MODE, false),
            tapToToggleNight = prefs.getBoolean(KEY_TAP_TO_TOGGLE_NIGHT, true),
            nightBrightness = prefs.getFloat(KEY_NIGHT_BRIGHTNESS, 0.20f).coerceIn(0.01f, 1.0f),
            nightColor = prefs.getString(KEY_NIGHT_COLOR, "red") ?: "red",
            showSeconds = prefs.getBoolean(KEY_SHOW_SECONDS, false),
            showBattery = prefs.getBoolean(KEY_SHOW_BATTERY, true),
            showChargingWatts = prefs.getBoolean(KEY_SHOW_CHARGING_WATTS, true),
            showAlarm = prefs.getBoolean(KEY_SHOW_ALARM, true),
            showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true),
            showTomorrowWeather = prefs.getBoolean(KEY_SHOW_TOMORROW_WEATHER, true),
            showGoogleHomePill = prefs.getBoolean(KEY_SHOW_GOOGLE_HOME_PILL, true),
            smartHomeProvider = prefs.getString(KEY_SMART_HOME_PROVIDER, "google") ?: "google",
            showSmartHomeAutomation = prefs.getBoolean(KEY_SHOW_SMART_HOME_AUTOMATION, true),
            smartHomeAutomationLabel = prefs.getString(KEY_SMART_HOME_AUTOMATION_LABEL, "Lights Out") ?: "Lights Out",
            smartHomeAutomationIntentUri = prefs.getString(KEY_SMART_HOME_AUTOMATION_INTENT_URI, "") ?: "",
            smartHomeAutomationWidgetId = prefs.getInt(KEY_SMART_HOME_AUTOMATION_WIDGET_ID, -1),
            showBrownNoise = prefs.getBoolean(KEY_SHOW_BROWN_NOISE, true),
            sleepSound = SleepSound.fromId(prefs.getString(KEY_SLEEP_SOUND, SleepSound.BROWN.id)).id,
            sleepTimerMinutes = sanitizeSleepTimerMinutes(prefs.getInt(KEY_SLEEP_TIMER_MINUTES, 0)),
            customCity = prefs.getString(KEY_CUSTOM_CITY, "") ?: "",
            useFahrenheit = prefs.getBoolean(KEY_USE_FAHRENHEIT, false),
            orientationMode = prefs.getString(KEY_ORIENTATION_MODE, "sensor") ?: "sensor",
            enableDndInNightMode = prefs.getBoolean(KEY_ENABLE_DND_IN_NIGHT_MODE, true),
            muteLocationInNightMode = prefs.getBoolean(KEY_MUTE_LOCATION_IN_NIGHT_MODE, true),
            requireProppedUp = prefs.getBoolean(KEY_REQUIRE_PROPPED_UP, true),
            minTiltAngleDegrees = prefs.getInt(KEY_MIN_TILT_ANGLE, 30),
            enableMidnightPath = prefs.getBoolean(KEY_ENABLE_MIDNIGHT_PATH, true),
            midnightPathBrightness = prefs.getFloat(KEY_MIDNIGHT_PATH_BRIGHTNESS, 0.70f).coerceIn(0.10f, 1.0f)
        )
    }

    /**
     * Emits the current DisplaySettings and any updates whenever preferences change.
     */
    fun settingsFlow(): Flow<DisplaySettings> = callbackFlow {
        trySend(getSettings())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(getSettings())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun updateSettings(settings: DisplaySettings) {
        prefs.edit()
            .putBoolean(KEY_WIRELESS_ONLY, settings.wirelessOnly)
            .putBoolean(KEY_BURN_IN_PROTECTION, settings.burnInProtection)
            .putBoolean(KEY_NIGHT_MODE, settings.nightModeEnabled)
            .putBoolean(KEY_TAP_TO_TOGGLE_NIGHT, settings.tapToToggleNight)
            .putFloat(KEY_NIGHT_BRIGHTNESS, settings.nightBrightness.coerceIn(0.01f, 1.0f))
            .putString(KEY_NIGHT_COLOR, settings.nightColor)
            .putBoolean(KEY_SHOW_SECONDS, settings.showSeconds)
            .putBoolean(KEY_SHOW_BATTERY, settings.showBattery)
            .putBoolean(KEY_SHOW_CHARGING_WATTS, settings.showChargingWatts)
            .putBoolean(KEY_SHOW_ALARM, settings.showAlarm)
            .putBoolean(KEY_SHOW_WEATHER, settings.showWeather)
            .putBoolean(KEY_SHOW_TOMORROW_WEATHER, settings.showTomorrowWeather)
            .putBoolean(KEY_SHOW_GOOGLE_HOME_PILL, settings.showGoogleHomePill)
            .putString(KEY_SMART_HOME_PROVIDER, settings.smartHomeProvider)
            .putBoolean(KEY_SHOW_SMART_HOME_AUTOMATION, settings.showSmartHomeAutomation)
            .putString(KEY_SMART_HOME_AUTOMATION_LABEL, settings.smartHomeAutomationLabel)
            .putString(KEY_SMART_HOME_AUTOMATION_INTENT_URI, settings.smartHomeAutomationIntentUri)
            .putInt(KEY_SMART_HOME_AUTOMATION_WIDGET_ID, settings.smartHomeAutomationWidgetId)
            .putBoolean(KEY_SHOW_BROWN_NOISE, settings.showBrownNoise)
            .putString(KEY_SLEEP_SOUND, SleepSound.fromId(settings.sleepSound).id)
            .putInt(KEY_SLEEP_TIMER_MINUTES, sanitizeSleepTimerMinutes(settings.sleepTimerMinutes))
            .putString(KEY_CUSTOM_CITY, settings.customCity)
            .putBoolean(KEY_USE_FAHRENHEIT, settings.useFahrenheit)
            .putString(KEY_ORIENTATION_MODE, settings.orientationMode)
            .putBoolean(KEY_ENABLE_DND_IN_NIGHT_MODE, settings.enableDndInNightMode)
            .putBoolean(KEY_MUTE_LOCATION_IN_NIGHT_MODE, settings.muteLocationInNightMode)
            .putBoolean(KEY_REQUIRE_PROPPED_UP, settings.requireProppedUp)
            .putInt(KEY_MIN_TILT_ANGLE, settings.minTiltAngleDegrees)
            .putBoolean(KEY_ENABLE_MIDNIGHT_PATH, settings.enableMidnightPath)
            .putFloat(KEY_MIDNIGHT_PATH_BRIGHTNESS, settings.midnightPathBrightness.coerceIn(0.10f, 1.0f))
            .apply()
    }

    fun setEnableDndInNightMode(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ENABLE_DND_IN_NIGHT_MODE, enabled)
            .apply()
    }

    fun setMuteLocationInNightMode(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_MUTE_LOCATION_IN_NIGHT_MODE, enabled)
            .apply()
    }

    fun setNightBrightness(brightness: Float) {
        prefs.edit()
            .putFloat(KEY_NIGHT_BRIGHTNESS, brightness.coerceIn(0.01f, 1.0f))
            .apply()
    }

    fun setNightColor(colorId: String) {
        prefs.edit()
            .putString(KEY_NIGHT_COLOR, colorId)
            .apply()
    }

    fun setSleepSound(soundId: String) {
        prefs.edit()
            .putString(KEY_SLEEP_SOUND, SleepSound.fromId(soundId).id)
            .apply()
    }

    fun setSleepTimerMinutes(minutes: Int) {
        prefs.edit()
            .putInt(KEY_SLEEP_TIMER_MINUTES, sanitizeSleepTimerMinutes(minutes))
            .apply()
    }

    fun setRequireProppedUp(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_REQUIRE_PROPPED_UP, enabled)
            .apply()
    }

    fun setMinTiltAngleDegrees(degrees: Int) {
        prefs.edit()
            .putInt(KEY_MIN_TILT_ANGLE, degrees.coerceIn(15, 75))
            .apply()
    }

    fun toggleNightMode(): Boolean {
        val current = prefs.getBoolean(KEY_NIGHT_MODE, false)
        val newMode = !current
        prefs.edit().putBoolean(KEY_NIGHT_MODE, newMode).apply()
        return newMode
    }

    fun setSmartHomeAutomationWidgetId(widgetId: Int) {
        prefs.edit().putInt(KEY_SMART_HOME_AUTOMATION_WIDGET_ID, widgetId).apply()
    }

    companion object {
        private const val PREFS_NAME = "screen_saver_prefs"
        private const val KEY_WIRELESS_ONLY_MIGRATED = "pref_wireless_only_migrated_v2"
        const val KEY_WIRELESS_ONLY = "pref_wireless_only"
        const val KEY_BURN_IN_PROTECTION = "pref_burn_in_protection"
        const val KEY_NIGHT_MODE = "pref_night_mode"
        const val KEY_TAP_TO_TOGGLE_NIGHT = "pref_tap_to_toggle_night"
        const val KEY_NIGHT_BRIGHTNESS = "pref_night_brightness"
        const val KEY_NIGHT_COLOR = "pref_night_color"
        const val KEY_SHOW_SECONDS = "pref_show_seconds"
        const val KEY_SHOW_BATTERY = "pref_show_battery"
        const val KEY_SHOW_CHARGING_WATTS = "pref_show_charging_watts"
        const val KEY_SHOW_ALARM = "pref_show_alarm"
        const val KEY_SHOW_WEATHER = "pref_show_weather"
        const val KEY_SHOW_TOMORROW_WEATHER = "pref_show_tomorrow_weather"
        const val KEY_SHOW_GOOGLE_HOME_PILL = "pref_show_google_home_pill"
        const val KEY_SMART_HOME_PROVIDER = "pref_smart_home_provider"
        const val KEY_SHOW_SMART_HOME_AUTOMATION = "pref_show_smart_home_automation"
        const val KEY_SMART_HOME_AUTOMATION_LABEL = "pref_smart_home_automation_label"
        const val KEY_SMART_HOME_AUTOMATION_INTENT_URI = "pref_smart_home_automation_intent_uri"
        const val KEY_SMART_HOME_AUTOMATION_WIDGET_ID = "pref_smart_home_automation_widget_id"
        const val KEY_SHOW_BROWN_NOISE = "pref_show_brown_noise"
        const val KEY_SLEEP_SOUND = "pref_sleep_sound"
        const val KEY_SLEEP_TIMER_MINUTES = "pref_sleep_timer_minutes"

        /** Keys from the removed thermostat/climate feature, purged once on startup. */
        private val LEGACY_REMOVED_KEYS = listOf(
            "pref_show_thermostat",
            "pref_thermostat_name",
            "pref_thermostat_target_temp",
            "pref_thermostat_mode",
            "pref_show_climate_card",
            "pref_climate_zone1_name",
            "pref_climate_zone2_name"
        )
        const val KEY_CUSTOM_CITY = "pref_custom_city"
        const val KEY_USE_FAHRENHEIT = "pref_use_fahrenheit"
        const val KEY_ORIENTATION_MODE = "pref_orientation_mode"
        const val KEY_ENABLE_DND_IN_NIGHT_MODE = "pref_enable_dnd_in_night_mode"
        const val KEY_MUTE_LOCATION_IN_NIGHT_MODE = "pref_mute_location_in_night_mode"
        const val KEY_REQUIRE_PROPPED_UP = "pref_require_propped_up"
        const val KEY_MIN_TILT_ANGLE = "pref_min_tilt_angle"
        const val KEY_ENABLE_MIDNIGHT_PATH = "pref_enable_midnight_path"
        const val KEY_MIDNIGHT_PATH_BRIGHTNESS = "pref_midnight_path_brightness"
    }
}
