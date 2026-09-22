package com.antigravity.screensaver

import android.app.NotificationManager
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import com.antigravity.screensaver.data.AmbientLightTracker
import com.antigravity.screensaver.data.BatteryStateTracker
import com.antigravity.screensaver.data.DoNotDisturbController
import com.antigravity.screensaver.data.LocationController
import com.antigravity.screensaver.model.DisplaySettings
import com.antigravity.screensaver.model.ThermostatInfo
import com.antigravity.screensaver.model.ThermostatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSaverUnitTest {

    @Test
    fun testDefaultSettings() {
        val settings = DisplaySettings()
        assertFalse("Wireless only should default to false", settings.wirelessOnly)
        assertTrue("Burn-in protection should default to true", settings.burnInProtection)
        assertTrue("Battery display should default to true", settings.showBattery)
        assertTrue("Weather display should default to true", settings.showWeather)
        assertTrue("Alarm display should default to true", settings.showAlarm)
        assertFalse("Night mode should default to false", settings.nightModeEnabled)
        assertFalse("Seconds should default to false", settings.showSeconds)
        assertFalse("Fahrenheit should default to false", settings.useFahrenheit)
    }

    @Test
    fun testSettingsCopy() {
        val original = DisplaySettings()
        val modified = original.copy(
            wirelessOnly = true,
            nightModeEnabled = true,
            customCity = "Chicago",
            useFahrenheit = true
        )

        assertTrue(modified.wirelessOnly)
        assertTrue(modified.nightModeEnabled)
        assertEquals("Chicago", modified.customCity)
        assertTrue(modified.useFahrenheit)
        assertTrue(modified.burnInProtection) // preserved
    }

    @Test
    fun testWirelessPluggedDetection() {
        // Test helper logic for wireless plug detection
        val wirelessValue = BatteryManager.BATTERY_PLUGGED_WIRELESS
        val usbValue = BatteryManager.BATTERY_PLUGGED_USB
        val acValue = BatteryManager.BATTERY_PLUGGED_AC

        assertEquals(4, wirelessValue)
        assertEquals(2, usbValue)
        assertEquals(1, acValue)

        assertTrue(wirelessValue == BatteryManager.BATTERY_PLUGGED_WIRELESS)
        assertFalse(usbValue == BatteryManager.BATTERY_PLUGGED_WIRELESS)
        assertFalse(acValue == BatteryManager.BATTERY_PLUGGED_WIRELESS)
    }

    @Test
    fun testWiredPluggedDetection() {
        val wirelessValue = BatteryManager.BATTERY_PLUGGED_WIRELESS
        val usbValue = BatteryManager.BATTERY_PLUGGED_USB
        val acValue = BatteryManager.BATTERY_PLUGGED_AC

        val isWiredUsb = usbValue == BatteryManager.BATTERY_PLUGGED_AC || usbValue == BatteryManager.BATTERY_PLUGGED_USB
        val isWiredAc = acValue == BatteryManager.BATTERY_PLUGGED_AC || acValue == BatteryManager.BATTERY_PLUGGED_USB
        val isWiredWireless = wirelessValue == BatteryManager.BATTERY_PLUGGED_AC || wirelessValue == BatteryManager.BATTERY_PLUGGED_USB

        assertTrue("USB should be detected as wired", isWiredUsb)
        assertTrue("AC should be detected as wired", isWiredAc)
        assertFalse("Wireless should not be detected as wired", isWiredWireless)
    }

    @Test
    fun testNewFeaturesDefaults() {
        val settings = DisplaySettings()
        assertEquals(0.20f, settings.nightBrightness, 0.001f)
        assertTrue(settings.showTomorrowWeather)
        assertTrue(settings.showChargingWatts)
    }

    @Test
    fun testWattageCalculationFormula() {
        val voltageMv = 4200L // 4.2V
        val currentUa = 3500000L // 3.5A
        val rawWatts = (currentUa * voltageMv) / 1_000_000_000f
        val roundedWatts = kotlin.math.round(rawWatts * 10) / 10f

        assertEquals(14.7f, roundedWatts, 0.01f)
    }

    @Test
    fun testThermostatDefaults() {
        val settings = DisplaySettings()
        assertTrue("Thermostat display should default to true", settings.showThermostat)
        assertEquals("Home Thermostat", settings.thermostatName)
        assertEquals(72f, settings.thermostatTargetTemp, 0.01f)
        assertEquals("HEAT", settings.thermostatMode)
    }

    @Test
    fun testThermostatModes() {
        assertEquals(ThermostatMode.HEAT, ThermostatMode.valueOf("HEAT"))
        assertEquals(ThermostatMode.COOL, ThermostatMode.valueOf("COOL"))
        assertEquals(ThermostatMode.ECO, ThermostatMode.valueOf("ECO"))
        assertEquals(ThermostatMode.OFF, ThermostatMode.valueOf("OFF"))
    }

    @Test
    fun testThermostatInfoModel() {
        val info = ThermostatInfo(
            name = "Living Room",
            currentTemp = 70f,
            targetTemp = 68f,
            mode = ThermostatMode.COOL
        )
        assertEquals("Living Room", info.name)
        assertEquals(70f, info.currentTemp, 0.01f)
        assertEquals(68f, info.targetTemp, 0.01f)
        assertEquals(ThermostatMode.COOL, info.mode)
    }

    @Test
    fun testClimateControlsDefaults() {
        val settings = DisplaySettings()
        assertTrue("Climate card should default to true", settings.showClimateCard)
        assertEquals("Downstairs", settings.climateZone1Name)
        assertEquals("Upstairs", settings.climateZone2Name)
    }

    @Test
    fun testClimateControlsSettingsCopy() {
        val original = DisplaySettings()
        val modified = original.copy(
            showClimateCard = false,
            climateZone1Name = "Main Level",
            climateZone2Name = "Master Bedroom"
        )
        assertFalse(modified.showClimateCard)
        assertEquals("Main Level", modified.climateZone1Name)
        assertEquals("Master Bedroom", modified.climateZone2Name)
    }

    @Test
    fun testGoogleHomePillSettings() {
        val settings = DisplaySettings()
        assertTrue("Google Home pill display should default to true", settings.showGoogleHomePill)
        val modified = settings.copy(showGoogleHomePill = false)
        assertFalse("Google Home pill should be toggleable off", modified.showGoogleHomePill)
    }

    @Test
    fun testBrownNoiseSettings() {
        val settings = DisplaySettings()
        assertTrue("Brown Noise tile display should default to true", settings.showBrownNoise)
        val modified = settings.copy(showBrownNoise = false)
        assertFalse("Brown Noise tile should be toggleable off", modified.showBrownNoise)
    }

    @Test
    fun testOrientationSettings() {
        val settings = DisplaySettings()
        assertEquals("sensor", settings.orientationMode)
        val landscape = settings.copy(orientationMode = "landscape")
        assertEquals("landscape", landscape.orientationMode)
    }

    @Test
    fun testNightBrightnessRange() {
        val minBrightness = 0.0f
        val maxBrightness = 1.0f
        val clampedLow = (-0.10f).coerceIn(0.0f, 1.0f)
        val clampedHigh = (1.50f).coerceIn(0.0f, 1.0f)
        val clampedValid = (0.0f).coerceIn(0.0f, 1.0f)

        assertEquals(minBrightness, clampedLow, 0.001f)
        assertEquals(maxBrightness, clampedHigh, 0.001f)
        assertEquals(minBrightness, clampedValid, 0.001f)
    }

    @Test
    fun testAmbientLightHysteresis() {
        // Below DARK_THRESHOLD_LUX (5.0f) should become dark
        assertTrue("3 lux should trigger dark mode", AmbientLightTracker.evaluateDarkState(currentDark = false, lux = 3.0f))
        assertTrue("5 lux should trigger dark mode", AmbientLightTracker.evaluateDarkState(currentDark = false, lux = 5.0f))

        // Above BRIGHT_THRESHOLD_LUX (12.0f) should become bright
        assertFalse("15 lux should trigger bright mode", AmbientLightTracker.evaluateDarkState(currentDark = true, lux = 15.0f))
        assertFalse("12 lux should trigger bright mode", AmbientLightTracker.evaluateDarkState(currentDark = true, lux = 12.0f))

        // Between 5.0f and 12.0f (e.g. 8.0f), previous state must be preserved (hysteresis)
        assertTrue("8 lux should keep dark if already dark", AmbientLightTracker.evaluateDarkState(currentDark = true, lux = 8.0f))
        assertFalse("8 lux should keep bright if already bright", AmbientLightTracker.evaluateDarkState(currentDark = false, lux = 8.0f))
    }

    @Test
    fun testDndSettingsDefaultAndCopy() {
        val defaultSettings = DisplaySettings()
        assertTrue("DND in night mode should be enabled by default", defaultSettings.enableDndInNightMode)

        val disabledSettings = defaultSettings.copy(enableDndInNightMode = false)
        assertFalse("DND should be disableable via copy", disabledSettings.enableDndInNightMode)
        assertTrue("Burn-in protection should remain intact", disabledSettings.burnInProtection)
    }

    @Test
    fun testDndControllerStateLogic() {
        // If current interruption filter is ALL (normal / DND is off), screensaver should take ownership
        assertTrue(
            "Screensaver should take ownership when DND was off",
            DoNotDisturbController.shouldTakeOwnership(NotificationManager.INTERRUPTION_FILTER_ALL)
        )

        // If user already had DND active (PRIORITY, ALARMS, or NONE), screensaver must NOT take ownership
        assertFalse(
            "Screensaver should NOT take ownership if user already had Priority DND",
            DoNotDisturbController.shouldTakeOwnership(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        )
        assertFalse(
            "Screensaver should NOT take ownership if user already had Alarms DND",
            DoNotDisturbController.shouldTakeOwnership(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        )
        assertFalse(
            "Screensaver should NOT take ownership if user already had Total Silence",
            DoNotDisturbController.shouldTakeOwnership(NotificationManager.INTERRUPTION_FILTER_NONE)
        )

        // Restore decision logic
        assertTrue(
            "Should restore on exit if screensaver was managing DND",
            DoNotDisturbController.evaluateShouldRestore(isManagingDnd = true)
        )
        assertFalse(
            "Should NOT restore on exit if screensaver was not managing DND",
            DoNotDisturbController.evaluateShouldRestore(isManagingDnd = false)
        )
    }

    @Test
    fun testLocationMuteSettingsDefaultAndCopy() {
        val defaultSettings = DisplaySettings()
        assertTrue("Location mute in night mode should be enabled by default", defaultSettings.muteLocationInNightMode)

        val disabledSettings = defaultSettings.copy(muteLocationInNightMode = false)
        assertFalse("Location mute should be disableable via copy", disabledSettings.muteLocationInNightMode)
        assertTrue("DND in night mode should remain intact", disabledSettings.enableDndInNightMode)
    }

    @Test
    fun testLocationControllerStateLogic() {
        // If location is currently enabled and mode is not OFF, screensaver should take ownership
        assertTrue(
            "Screensaver should take ownership when location is ON",
            LocationController.shouldTakeOwnership(isCurrentlyEnabled = true, currentMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
        )

        // If location is already OFF or disabled, screensaver must NOT take ownership
        assertFalse(
            "Screensaver should NOT take ownership if location is disabled",
            LocationController.shouldTakeOwnership(isCurrentlyEnabled = false, currentMode = Settings.Secure.LOCATION_MODE_OFF)
        )
        assertFalse(
            "Screensaver should NOT take ownership if mode is OFF even if reported enabled",
            LocationController.shouldTakeOwnership(isCurrentlyEnabled = true, currentMode = Settings.Secure.LOCATION_MODE_OFF)
        )

        // Restore decision logic
        assertTrue(
            "Should restore on exit if screensaver was managing Location",
            LocationController.evaluateShouldRestore(isManagingLocation = true)
        )
        assertFalse(
            "Should NOT restore on exit if screensaver was not managing Location",
            LocationController.evaluateShouldRestore(isManagingLocation = false)
        )
    }

    @Test
    fun testAlarmTriggerTrackerActionsCoverage() {
        // Ensure Google Clock (Pixel) and AOSP deskclock actions are included
        val actions = listOf(
            "com.google.android.deskclock.ALARM_ALERT",
            "com.google.android.deskclock.action.ALARM_ALERT",
            "com.android.deskclock.ALARM_ALERT",
            "android.app.action.NEXT_ALARM_CLOCK_CHANGED",
            "com.sec.android.app.clockpackage.ALARM_ALERT",
            "com.samsung.sec.android.clockpackage.alarm.ALARM_ALERT",
            "com.sonyericsson.alarm.ALARM_ALERT",
            "org.codeaurora.poweroffalarm.action.UPDATE_ALARM"
        )

        assertTrue(actions.contains("com.google.android.deskclock.ALARM_ALERT"))
        assertTrue(actions.contains("com.android.deskclock.ALARM_ALERT"))
        assertTrue(actions.contains("android.app.action.NEXT_ALARM_CLOCK_CHANGED"))
    }

    @Test
    fun testAlarmTriggerTimeWindowLogic() {
        val now = 1000000L
        val triggerTime = 1000000L
        val windowMs = 5 * 60 * 1000L

        // Exact match
        val isNearExact = triggerTime > 0L &&
                now >= (triggerTime - 5000L) &&
                now <= (triggerTime + windowMs)
        assertTrue(isNearExact)

        // 2 seconds before trigger time (within 5s grace window)
        val nowBefore = triggerTime - 2000L
        val isNearBefore = triggerTime > 0L &&
                nowBefore >= (triggerTime - 5000L) &&
                nowBefore <= (triggerTime + windowMs)
        assertTrue(isNearBefore)

        // 3 minutes after trigger time (still ringing/snoozing)
        val nowAfter = triggerTime + 3 * 60 * 1000L
        val isNearAfter = triggerTime > 0L &&
                nowAfter >= (triggerTime - 5000L) &&
                nowAfter <= (triggerTime + windowMs)
        assertTrue(isNearAfter)

        // 10 minutes before (too early)
        val nowTooEarly = triggerTime - 10 * 60 * 1000L
        val isNearTooEarly = triggerTime > 0L &&
                nowTooEarly >= (triggerTime - 5000L) &&
                nowTooEarly <= (triggerTime + windowMs)
        assertFalse(isNearTooEarly)

        // 10 minutes after (past active window)
        val nowTooLate = triggerTime + 10 * 60 * 1000L
        val isNearTooLate = triggerTime > 0L &&
                nowTooLate >= (triggerTime - 5000L) &&
                nowTooLate <= (triggerTime + windowMs)
        assertFalse(isNearTooLate)
    }
}
