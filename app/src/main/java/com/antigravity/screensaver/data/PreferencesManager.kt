package com.antigravity.screensaver.data

import android.content.Context
import android.content.SharedPreferences
import com.antigravity.screensaver.model.DisplaySettings
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
    }

    fun getSettings(): DisplaySettings {
        return DisplaySettings(
            wirelessOnly = prefs.getBoolean(KEY_WIRELESS_ONLY, false),
            burnInProtection = prefs.getBoolean(KEY_BURN_IN_PROTECTION, true),
            nightModeEnabled = prefs.getBoolean(KEY_NIGHT_MODE, false),
            tapToToggleNight = prefs.getBoolean(KEY_TAP_TO_TOGGLE_NIGHT, true),
            nightBrightness = prefs.getFloat(KEY_NIGHT_BRIGHTNESS, 0.20f).coerceIn(0.0f, 1.0f),
            nightColor = prefs.getString(KEY_NIGHT_COLOR, "red") ?: "red",
            showSeconds = prefs.getBoolean(KEY_SHOW_SECONDS, false),
            showBattery = prefs.getBoolean(KEY_SHOW_BATTERY, true),
            showChargingWatts = prefs.getBoolean(KEY_SHOW_CHARGING_WATTS, true),
            showAlarm = prefs.getBoolean(KEY_SHOW_ALARM, true),
            showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true),
            showTomorrowWeather = prefs.getBoolean(KEY_SHOW_TOMORROW_WEATHER, true),
            showThermostat = prefs.getBoolean(KEY_SHOW_THERMOSTAT, true),
            thermostatName = prefs.getString(KEY_THERMOSTAT_NAME, "Home Thermostat") ?: "Home Thermostat",
            thermostatTargetTemp = prefs.getFloat(KEY_THERMOSTAT_TARGET_TEMP, 72f),
            thermostatMode = prefs.getString(KEY_THERMOSTAT_MODE, "HEAT") ?: "HEAT",
            showClimateCard = prefs.getBoolean(KEY_SHOW_CLIMATE_CARD, true),
            climateZone1Name = prefs.getString(KEY_CLIMATE_ZONE1_NAME, "Downstairs") ?: "Downstairs",
            climateZone2Name = prefs.getString(KEY_CLIMATE_ZONE2_NAME, "Upstairs") ?: "Upstairs",
            showGoogleHomePill = prefs.getBoolean(KEY_SHOW_GOOGLE_HOME_PILL, true),
            smartHomeProvider = prefs.getString(KEY_SMART_HOME_PROVIDER, "google") ?: "google",
            showBrownNoise = prefs.getBoolean(KEY_SHOW_BROWN_NOISE, true),
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
            .putFloat(KEY_NIGHT_BRIGHTNESS, settings.nightBrightness.coerceIn(0.0f, 1.0f))
            .putString(KEY_NIGHT_COLOR, settings.nightColor)
            .putBoolean(KEY_SHOW_SECONDS, settings.showSeconds)
            .putBoolean(KEY_SHOW_BATTERY, settings.showBattery)
            .putBoolean(KEY_SHOW_CHARGING_WATTS, settings.showChargingWatts)
            .putBoolean(KEY_SHOW_ALARM, settings.showAlarm)
            .putBoolean(KEY_SHOW_WEATHER, settings.showWeather)
            .putBoolean(KEY_SHOW_TOMORROW_WEATHER, settings.showTomorrowWeather)
            .putBoolean(KEY_SHOW_THERMOSTAT, settings.showThermostat)
            .putString(KEY_THERMOSTAT_NAME, settings.thermostatName)
            .putFloat(KEY_THERMOSTAT_TARGET_TEMP, settings.thermostatTargetTemp)
            .putString(KEY_THERMOSTAT_MODE, settings.thermostatMode)
            .putBoolean(KEY_SHOW_CLIMATE_CARD, settings.showClimateCard)
            .putString(KEY_CLIMATE_ZONE1_NAME, settings.climateZone1Name)
            .putString(KEY_CLIMATE_ZONE2_NAME, settings.climateZone2Name)
            .putBoolean(KEY_SHOW_GOOGLE_HOME_PILL, settings.showGoogleHomePill)
            .putString(KEY_SMART_HOME_PROVIDER, settings.smartHomeProvider)
            .putBoolean(KEY_SHOW_BROWN_NOISE, settings.showBrownNoise)
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
            .putFloat(KEY_NIGHT_BRIGHTNESS, brightness.coerceIn(0.0f, 1.0f))
            .apply()
    }

    fun setNightColor(colorId: String) {
        prefs.edit()
            .putString(KEY_NIGHT_COLOR, colorId)
            .apply()
    }

    fun setThermostatTargetTemp(temp: Float) {
        prefs.edit()
            .putFloat(KEY_THERMOSTAT_TARGET_TEMP, temp)
            .apply()
    }

    fun setThermostatMode(mode: String) {
        prefs.edit()
            .putString(KEY_THERMOSTAT_MODE, mode)
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
        const val KEY_SHOW_THERMOSTAT = "pref_show_thermostat"
        const val KEY_THERMOSTAT_NAME = "pref_thermostat_name"
        const val KEY_THERMOSTAT_TARGET_TEMP = "pref_thermostat_target_temp"
        const val KEY_THERMOSTAT_MODE = "pref_thermostat_mode"
        const val KEY_SHOW_CLIMATE_CARD = "pref_show_climate_card"
        const val KEY_CLIMATE_ZONE1_NAME = "pref_climate_zone1_name"
        const val KEY_CLIMATE_ZONE2_NAME = "pref_climate_zone2_name"
        const val KEY_SHOW_GOOGLE_HOME_PILL = "pref_show_google_home_pill"
        const val KEY_SMART_HOME_PROVIDER = "pref_smart_home_provider"
        const val KEY_SHOW_BROWN_NOISE = "pref_show_brown_noise"
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
