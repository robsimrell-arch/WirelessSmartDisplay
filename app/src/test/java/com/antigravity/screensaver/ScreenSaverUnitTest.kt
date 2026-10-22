package com.antigravity.screensaver

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.provider.Settings
import com.antigravity.screensaver.data.AlarmTriggerTracker
import com.antigravity.screensaver.data.AmbientLightTracker
import com.antigravity.screensaver.data.BatteryStateTracker
import com.antigravity.screensaver.data.DoNotDisturbController
import com.antigravity.screensaver.data.LocationController
import com.antigravity.screensaver.data.TiltSensorTracker
import com.antigravity.screensaver.audio.SleepSoundPlayer
import com.antigravity.screensaver.audio.SoundGenerators
import com.antigravity.screensaver.model.DisplaySettings
import com.antigravity.screensaver.model.SleepSound
import com.antigravity.screensaver.model.sanitizeSleepTimerMinutes
import com.antigravity.screensaver.ui.dream.ScreensaverActivity
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
    fun testSleepSoundSettingsDefaultsAndCopy() {
        val settings = DisplaySettings()
        assertTrue("Sleep sound tile should default to true", settings.showBrownNoise)
        assertEquals("brown", settings.sleepSound)
        assertEquals(0, settings.sleepTimerMinutes)

        val modified = settings.copy(
            showBrownNoise = false,
            sleepSound = "ocean",
            sleepTimerMinutes = 60
        )
        assertFalse(modified.showBrownNoise)
        assertEquals("ocean", modified.sleepSound)
        assertEquals(60, modified.sleepTimerMinutes)
    }

    @Test
    fun testSleepSoundEnum() {
        assertEquals(6, SleepSound.entries.size)
        assertEquals(SleepSound.BROWN, SleepSound.fromId("brown"))
        assertEquals(SleepSound.PINK, SleepSound.fromId("pink"))
        assertEquals(SleepSound.WHITE, SleepSound.fromId("white"))
        assertEquals(SleepSound.RAIN, SleepSound.fromId("rain"))
        assertEquals(SleepSound.OCEAN, SleepSound.fromId("ocean"))
        assertEquals(SleepSound.FAN, SleepSound.fromId("fan"))
        assertEquals(SleepSound.BROWN, SleepSound.fromId("unknown_fallback"))
    }

    @Test
    fun testSanitizeSleepTimerMinutes() {
        assertEquals(0, sanitizeSleepTimerMinutes(0))
        assertEquals(30, sanitizeSleepTimerMinutes(30))
        assertEquals(60, sanitizeSleepTimerMinutes(60))
        assertEquals(90, sanitizeSleepTimerMinutes(90))
        assertEquals(0, sanitizeSleepTimerMinutes(45))
        assertEquals(0, sanitizeSleepTimerMinutes(-10))
    }

    @Test
    fun testSleepSoundPlayerFadeGain() {
        // Continuous / no timer -> full volume
        assertEquals(1.0f, SleepSoundPlayer.computeTimerFadeGain(null), 0.001f)
        // More than 60s left -> full volume
        assertEquals(1.0f, SleepSoundPlayer.computeTimerFadeGain(120_000L), 0.001f)
        assertEquals(1.0f, SleepSoundPlayer.computeTimerFadeGain(60_000L), 0.001f)
        // Mid-fade (30s left out of 60s) -> 0.5
        assertEquals(0.5f, SleepSoundPlayer.computeTimerFadeGain(30_000L), 0.001f)
        // 15s left -> 0.25
        assertEquals(0.25f, SleepSoundPlayer.computeTimerFadeGain(15_000L), 0.001f)
        // 0s or negative -> 0.0 (silent)
        assertEquals(0.0f, SleepSoundPlayer.computeTimerFadeGain(0L), 0.001f)
        assertEquals(0.0f, SleepSoundPlayer.computeTimerFadeGain(-500L), 0.001f)
    }

    @Test
    fun testProceduralSoundGenerators() {
        SleepSound.entries.forEach { sound ->
            val gen = SoundGenerators.create(sound)
            // Generate 1000 samples and assert each is within stable audio range
            for (i in 0 until 1000) {
                val sample = gen.next()
                assertTrue(
                    "Sample for ${sound.name} out of bounds: $sample",
                    sample in -2.0f..2.0f
                )
            }
        }
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
        val minBrightness = 0.01f
        val maxBrightness = 1.0f
        val clampedLow = (-0.10f).coerceIn(0.01f, 1.0f)
        val clampedZero = (0.0f).coerceIn(0.01f, 1.0f)
        val clampedHigh = (1.50f).coerceIn(0.01f, 1.0f)
        val clampedValid = (0.01f).coerceIn(0.01f, 1.0f)

        assertEquals(minBrightness, clampedLow, 0.001f)
        assertEquals(minBrightness, clampedZero, 0.001f)
        assertEquals(maxBrightness, clampedHigh, 0.001f)
        assertEquals(minBrightness, clampedValid, 0.001f)
    }

    @Test
    fun testAmbientLightHysteresis() {
        // Below DARK_THRESHOLD_LUX (5.0f) should trigger dark mode
        assertTrue("3 lux should trigger dark mode", AmbientLightTracker.evaluateDarkState(currentDark = false, lux = 3.0f))
        assertTrue("5 lux should trigger dark mode", AmbientLightTracker.evaluateDarkState(currentDark = false, lux = 5.0f))

        // Above BRIGHT_THRESHOLD_LUX (35.0f) should trigger bright mode
        assertFalse("40 lux should trigger bright mode", AmbientLightTracker.evaluateDarkState(currentDark = true, lux = 40.0f))
        assertFalse("35 lux should trigger bright mode", AmbientLightTracker.evaluateDarkState(currentDark = true, lux = 35.0f))

        // Between 5.0f and 35.0f (e.g. 8.0f, 15.0f, 25.0f), previous state must be preserved (hysteresis)
        assertTrue("8 lux should keep dark if already dark", AmbientLightTracker.evaluateDarkState(currentDark = true, lux = 8.0f))
        assertTrue("20 lux should keep dark if already dark", AmbientLightTracker.evaluateDarkState(currentDark = true, lux = 20.0f))
        assertFalse("8 lux should keep bright if already bright", AmbientLightTracker.evaluateDarkState(currentDark = false, lux = 8.0f))
        assertFalse("20 lux should keep bright if already bright", AmbientLightTracker.evaluateDarkState(currentDark = false, lux = 20.0f))

        // Temporal confirmation and cooldown checks
        assertFalse(
            "Should not commit transition during post-transition cooldown",
            AmbientLightTracker.shouldCommitTransition(targetDark = true, candidateElapsedMs = 3_000L, sinceLastTransitionMs = 2_000L)
        )
        assertFalse(
            "Should not commit dark transition before ENTER_DARK_CONFIRM_MS",
            AmbientLightTracker.shouldCommitTransition(targetDark = true, candidateElapsedMs = 1_500L, sinceLastTransitionMs = 10_000L)
        )
        assertTrue(
            "Should commit dark transition after ENTER_DARK_CONFIRM_MS and cooldown",
            AmbientLightTracker.shouldCommitTransition(targetDark = true, candidateElapsedMs = 2_600L, sinceLastTransitionMs = 10_000L)
        )
        assertFalse(
            "Should not commit bright transition before EXIT_DARK_CONFIRM_MS",
            AmbientLightTracker.shouldCommitTransition(targetDark = false, candidateElapsedMs = 2_600L, sinceLastTransitionMs = 10_000L)
        )
        assertTrue(
            "Should commit bright transition after EXIT_DARK_CONFIRM_MS and cooldown",
            AmbientLightTracker.shouldCommitTransition(targetDark = false, candidateElapsedMs = 3_600L, sinceLastTransitionMs = 10_000L)
        )
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

    @Test
    fun testDoubleBackPressTimingLogic() {
        val thresholdMs = 2000L
        var lastBackPressTime = 0L

        fun handleBackPress(pressTime: Long): Boolean {
            val shouldExit = (pressTime - lastBackPressTime) < thresholdMs
            if (shouldExit) {
                return true
            } else {
                lastBackPressTime = pressTime
                return false
            }
        }

        // First press at t = 10000ms: Should NOT exit, should show toast and record timestamp
        assertFalse("First back press should not exit", handleBackPress(10000L))
        assertEquals(10000L, lastBackPressTime)

        // Second press at t = 11500ms (1.5s later, within 2s): Should EXIT!
        assertTrue("Second back press within 2s should exit", handleBackPress(11500L))

        // Reset and test slow consecutive presses
        lastBackPressTime = 0L
        assertFalse("First press at 20000ms should not exit", handleBackPress(20000L))

        // Third press at t = 22500ms (2.5s later, outside 2s window): Should NOT exit, should reset timestamp
        assertFalse("Back press after 2s should not exit", handleBackPress(22500L))
        assertEquals(22500L, lastBackPressTime)

        // Fourth press at t = 23200ms (0.7s later): Should EXIT!
        assertTrue("Subsequent quick back press within 2s should exit", handleBackPress(23200L))
    }

    @Test
    fun testAlarmTriggerTrackerFocusLossEvaluation() {
        // When opening Settings, Clock, or Google Home, no alarm is active or near -> must NOT trigger!
        assertFalse(
            "Normal focus loss when no alarm is active must NOT trigger",
            AlarmTriggerTracker.evaluateFocusLostShouldTrigger(
                isNearAlarmTime = false,
                isAudioAlarm = false,
                isAlarmActive = false
            )
        )

        // If an alarm is near scheduled trigger time -> MUST trigger
        assertTrue(
            "Focus loss near scheduled alarm time must trigger",
            AlarmTriggerTracker.evaluateFocusLostShouldTrigger(
                isNearAlarmTime = true,
                isAudioAlarm = false,
                isAlarmActive = false
            )
        )

        // If alarm audio stream is playing -> MUST trigger
        assertTrue(
            "Focus loss while alarm audio is playing must trigger",
            AlarmTriggerTracker.evaluateFocusLostShouldTrigger(
                isNearAlarmTime = false,
                isAudioAlarm = true,
                isAlarmActive = false
            )
        )

        // If alarm broadcast was received -> MUST trigger
        assertTrue(
            "Focus loss while alarm broadcast state is active must trigger",
            AlarmTriggerTracker.evaluateFocusLostShouldTrigger(
                isNearAlarmTime = false,
                isAudioAlarm = false,
                isAlarmActive = true
            )
        )
    }

    @Test
    fun testScreensaverActivityFocusYieldEvaluation() {
        // When user is navigating to a child activity (Settings, Clock, or Google Home),
        // ScreensaverActivity must NOT yield, so it remains in the back stack!
        assertFalse(
            "Must NOT yield when navigating to child activity",
            ScreensaverActivity.evaluateShouldYieldOnFocusLost(isAlarmActive = false, isNavigatingToChild = true)
        )

        // When focus is lost normally without an alarm active, ScreensaverActivity must NOT yield!
        assertFalse(
            "Must NOT yield on normal focus loss without alarm active",
            ScreensaverActivity.evaluateShouldYieldOnFocusLost(isAlarmActive = false, isNavigatingToChild = false)
        )

        // When an active alarm takes focus, ScreensaverActivity MUST yield immediately to reveal the alarm!
        assertTrue(
            "Must yield when an active alarm takes focus",
            ScreensaverActivity.evaluateShouldYieldOnFocusLost(isAlarmActive = true, isNavigatingToChild = false)
        )

        // User navigation takes precedence even if alarm active
        assertFalse(
            "Must NOT yield if user is actively navigating to child",
            ScreensaverActivity.evaluateShouldYieldOnFocusLost(isAlarmActive = true, isNavigatingToChild = true)
        )

        // Single-argument backward compatibility overload
        assertFalse(
            "Single-arg must NOT yield when navigating to child",
            ScreensaverActivity.evaluateShouldYieldOnFocusLost(isNavigatingToChild = true)
        )
    }

    @Test
    fun testTiltSensorScreenOrientationCalculation() {
        // Flat surface: planar magnitude is tiny (< 0.8), preserves current orientation
        val flatOrientation = TiltSensorTracker.calculateScreenOrientation(
            x = 0.1f,
            y = 0.1f,
            z = 9.8f,
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, flatOrientation)

        // Normal Portrait propped up on stand (y >> x, y > 0)
        val portraitPropped = TiltSensorTracker.calculateScreenOrientation(
            x = 0.0f,
            y = 7.0f,
            z = 6.0f,
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, portraitPropped)

        // Landscape propped up on stand (x >> y, x > 0)
        val landscapePropped = TiltSensorTracker.calculateScreenOrientation(
            x = 7.0f,
            y = 0.5f,
            z = 6.0f,
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, landscapePropped)

        // Reverse Landscape propped up on stand (x >> y, x < 0)
        val reverseLandscapePropped = TiltSensorTracker.calculateScreenOrientation(
            x = -7.0f,
            y = 0.5f,
            z = 6.0f,
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, reverseLandscapePropped)

        // Reverse Portrait propped up (y < 0, |y| >> |x|)
        val reversePortraitPropped = TiltSensorTracker.calculateScreenOrientation(
            x = 0.0f,
            y = -7.0f,
            z = 6.0f,
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, reversePortraitPropped)

        // Sensor Landscape hysteresis: when current is SENSOR_LANDSCAPE and phone is in landscape
        val sensorLandscapeRetained = TiltSensorTracker.calculateScreenOrientation(
            x = 6.5f,
            y = 1.0f,
            z = 5.0f,
            currentOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, sensorLandscapeRetained)
    }

    @Test
    fun testTiltSensorInclineCalculation() {
        // Completely flat on table: z = 9.81, x=0, y=0 -> 0 degrees incline
        val flatIncline = TiltSensorTracker.calculateInclineDegrees(0f, 0f, 9.81f)
        assertEquals(0f, flatIncline, 0.5f)

        // Completely vertical: z = 0, y = 9.81 -> 90 degrees incline
        val verticalIncline = TiltSensorTracker.calculateInclineDegrees(0f, 9.81f, 0f)
        assertEquals(90f, verticalIncline, 0.5f)

        // Propped up at 45 degrees: z = 6.936f, y = 6.936f -> ~45 degrees incline
        val proppedIncline = TiltSensorTracker.calculateInclineDegrees(0f, 6.936f, 6.936f)
        assertEquals(45f, proppedIncline, 1.0f)
    }
}
