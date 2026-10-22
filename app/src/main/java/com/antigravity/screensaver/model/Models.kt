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

/**
 * Procedurally synthesized sleep sounds (no bundled audio files, zero APK cost).
 */
enum class SleepSound(val id: String, val label: String) {
    BROWN("brown", "Brown Noise"),
    PINK("pink", "Pink Noise"),
    WHITE("white", "White Noise"),
    RAIN("rain", "Rain"),
    OCEAN("ocean", "Ocean Waves"),
    FAN("fan", "Fan");

    companion object {
        fun fromId(id: String?): SleepSound = entries.find { it.id.equals(id, ignoreCase = true) } ?: BROWN
    }
}

/** Sleep timer choices in minutes. 0 = Off (play continuously). */
val SLEEP_TIMER_OPTIONS_MINUTES = listOf(0, 30, 60, 90)

fun sanitizeSleepTimerMinutes(minutes: Int): Int =
    if (minutes in SLEEP_TIMER_OPTIONS_MINUTES) minutes else 0

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
    val showGoogleHomePill: Boolean = true,
    val smartHomeProvider: String = "google",
    val showSmartHomeAutomation: Boolean = true,
    val smartHomeAutomationLabel: String = "Lights Out",
    val smartHomeAutomationIntentUri: String = "",
    val smartHomeAutomationWidgetId: Int = -1,
    /** Shows the Sleep Sounds tile (key name kept for backward compatibility). */
    val showBrownNoise: Boolean = true,
    val sleepSound: String = "brown",
    val sleepTimerMinutes: Int = 0,
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
    val todayTempMax: Float = 0f,
    val todayTempMin: Float = 0f,
    val hasTodayHiLo: Boolean = false,
    val tomorrowWeatherCode: Int = 0,
    val tomorrowTempMax: Float = 0f,
    val tomorrowTempMin: Float = 0f,
    val tomorrowConditionDescription: String = "",
    val hasTomorrowForecast: Boolean = false,
    val isFahrenheit: Boolean = false
) {
    fun toFahrenheit(): WeatherInfo {
        if (isFahrenheit) return this
        return copy(
            temperature = temperature * 9f / 5f + 32f,
            todayTempMax = todayTempMax * 9f / 5f + 32f,
            todayTempMin = todayTempMin * 9f / 5f + 32f,
            tomorrowTempMax = tomorrowTempMax * 9f / 5f + 32f,
            tomorrowTempMin = tomorrowTempMin * 9f / 5f + 32f,
            isFahrenheit = true
        )
    }

    fun toCelsius(): WeatherInfo {
        if (!isFahrenheit) return this
        return copy(
            temperature = (temperature - 32f) * 5f / 9f,
            todayTempMax = (todayTempMax - 32f) * 5f / 9f,
            todayTempMin = (todayTempMin - 32f) * 5f / 9f,
            tomorrowTempMax = (tomorrowTempMax - 32f) * 5f / 9f,
            tomorrowTempMin = (tomorrowTempMin - 32f) * 5f / 9f,
            isFahrenheit = false
        )
    }

    fun forUnit(useFahrenheit: Boolean): WeatherInfo {
        return if (useFahrenheit) toFahrenheit() else toCelsius()
    }
}

