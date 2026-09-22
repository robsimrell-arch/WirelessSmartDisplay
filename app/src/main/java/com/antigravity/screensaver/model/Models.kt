package com.antigravity.screensaver.model

import androidx.compose.ui.graphics.Color

enum class NightColor(
    val id: String,
    val label: String,
    val primaryColor: Color,
    val dimColor: Color
) {
    RED("red", "Red", Color(0xFFFF2D2D), Color(0x99FF2D2D)),
    AMBER("amber", "Amber", Color(0xFFFF9800), Color(0x99FF9800)),
    DEEP_ORANGE("deep_orange", "Orange", Color(0xFFFF5722), Color(0x99FF5722)),
    SOFT_GREEN("soft_green", "Green", Color(0xFF81C995), Color(0x9981C995)),
    WARM_WHITE("warm_white", "Warm White", Color(0xFFFFE0B2), Color(0x99FFE0B2));

    companion object {
        fun fromId(id: String): NightColor = entries.find { it.id.equals(id, ignoreCase = true) } ?: RED
    }
}

enum class ThermostatMode {
    HEAT, COOL, ECO, OFF
}

data class ThermostatInfo(
    val name: String = "Home Thermostat",
    val currentTemp: Float = 71f,
    val targetTemp: Float = 72f,
    val mode: ThermostatMode = ThermostatMode.HEAT
)

data class DisplaySettings(
    val wirelessOnly: Boolean = false,
    val burnInProtection: Boolean = true,
    val nightModeEnabled: Boolean = false,
    val tapToToggleNight: Boolean = true,
    val nightBrightness: Float = 0.20f,
    val nightColor: String = "red",
    val showSeconds: Boolean = false,
    val showBattery: Boolean = true,
    val showChargingWatts: Boolean = true,
    val showAlarm: Boolean = true,
    val showWeather: Boolean = true,
    val showTomorrowWeather: Boolean = true,
    val showThermostat: Boolean = true,
    val thermostatName: String = "Home Thermostat",
    val thermostatTargetTemp: Float = 72f,
    val thermostatMode: String = "HEAT",
    val showClimateCard: Boolean = true,
    val climateZone1Name: String = "Downstairs",
    val climateZone2Name: String = "Upstairs",
    val showGoogleHomePill: Boolean = true,
    val smartHomeProvider: String = "google",
    val showBrownNoise: Boolean = true,
    val customCity: String = "",
    val useFahrenheit: Boolean = false,
    val orientationMode: String = "sensor",
    val enableDndInNightMode: Boolean = true,
    val muteLocationInNightMode: Boolean = true,
    val requireProppedUp: Boolean = true,
    val minTiltAngleDegrees: Int = 30,
    val enableMidnightPath: Boolean = true,
    val midnightPathBrightness: Float = 0.70f
) {
    val activeNightColor: NightColor
        get() = NightColor.fromId(nightColor)
}

data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isWireless: Boolean = false,
    val pluggedType: Int = 0,
    val chargingWatts: Float? = null,
    val statusText: String = "Ready"
)

data class WeatherInfo(
    val temperature: Float = 0f,
    val weatherCode: Int = 0,
    val conditionDescription: String = "Clear",
    val cityName: String = "Local",
    val isLoaded: Boolean = false,
    val tomorrowWeatherCode: Int = 0,
    val tomorrowTempMax: Float = 0f,
    val tomorrowTempMin: Float = 0f,
    val tomorrowConditionDescription: String = "",
    val hasTomorrowForecast: Boolean = false
)
