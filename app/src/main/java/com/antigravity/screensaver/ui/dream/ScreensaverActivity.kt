package com.antigravity.screensaver.ui.dream

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.antigravity.screensaver.data.AlarmTriggerTracker
import com.antigravity.screensaver.data.DoNotDisturbController
import com.antigravity.screensaver.data.LocationController
import com.antigravity.screensaver.data.PreferencesManager
import com.antigravity.screensaver.data.TiltSensorTracker
import com.antigravity.screensaver.ui.settings.SettingsActivity
import com.antigravity.screensaver.ui.theme.ScreenSaverTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Fullscreen, interactive screensaver activity that acts as the back-stack root
 * when navigating to the Settings page, Clock app, or Smart Home controls.
 *
 * When the user finishes making adjustments in any of those apps and presses Back,
 * Android pops directly back to this activity, returning instantly to the live
 * screensaver dashboard without dropping to the launcher or waiting for system timeout.
 * Pressing Back a second time from this activity exits cleanly to the Android Home screen.
 */
class ScreensaverActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var tiltSensorTracker: TiltSensorTracker

    private var orientationJob: Job? = null
    private var tiltJob: Job? = null
    private val isFlatDarkened = MutableStateFlow(false)
    private var alarmTriggerTracker: AlarmTriggerTracker? = null
    private var hasGainedFocus = false

    companion object {
        const val EXTRA_LAUNCH_TARGET = "com.antigravity.screensaver.EXTRA_LAUNCH_TARGET"
        const val EXTRA_SMART_HOME_PROVIDER = "com.antigravity.screensaver.EXTRA_SMART_HOME_PROVIDER"

        const val TARGET_NONE = "none"
        const val TARGET_SETTINGS = "settings"
        const val TARGET_CLOCK = "clock"
        const val TARGET_SMART_HOME = "smart_home"

        /**
         * Universal launch helper to route actions through ScreensaverActivity.
         * If already running inside ScreensaverActivity, dispatches directly in-place.
         * Otherwise, launches ScreensaverActivity with singleTop and passes target extras.
         */
        fun launch(context: Context, target: String = TARGET_NONE, provider: String? = null) {
            if (context is ScreensaverActivity) {
                context.handleLaunchTarget(target, provider)
            } else {
                val intent = Intent(context, ScreensaverActivity::class.java).apply {
                    putExtra(EXTRA_LAUNCH_TARGET, target)
                    if (provider != null) {
                        putExtra(EXTRA_SMART_HOME_PROVIDER, provider)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        tiltSensorTracker = TiltSensorTracker(this)

        // Show over lockscreen and wake display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Keep screen on while screensaver is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Edge-to-edge layout and display cutout handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // Handle Back button: cleanly exit screensaver to Home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Apply display orientation and sensor posture tracking
        val settings = preferencesManager.getSettings()
        applyOrientation(settings.orientationMode)

        if (settings.requireProppedUp && tiltSensorTracker.hasSensor) {
            tiltJob?.cancel()
            tiltJob = lifecycleScope.launch {
                tiltSensorTracker.proppedUpStateFlow(
                    thresholdDegrees = settings.minTiltAngleDegrees,
                    flatGracePeriodMs = 2000L
                ).collect { isProppedUp ->
                    isFlatDarkened.value = !isProppedUp
                }
            }
        }

        setContent {
            ScreenSaverTheme {
                val isFlat by isFlatDarkened.collectAsState()
                if (isFlat) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable { finish() }
                    )
                } else {
                    SmartDisplayScreen(
                        onExitScreensaver = { finish() }
                    )
                }
            }
        }

        // Only handle launch target on first creation (not on configuration changes/rotations)
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        alarmTriggerTracker = AlarmTriggerTracker(this) {
            android.util.Log.i("ScreensaverActivity", "Alarm event detected! Finishing ScreensaverActivity to reveal AlarmActivity.")
            finish()
        }
        alarmTriggerTracker?.start()
    }

    override fun onStop() {
        super.onStop()
        alarmTriggerTracker?.stop()
        alarmTriggerTracker = null
        hasGainedFocus = false
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hasGainedFocus = true
            hideSystemBars()
        } else if (hasGainedFocus) {
            android.util.Log.d("ScreensaverActivity", "Window focus lost. Yielding to foreground activity/alarm.")
            alarmTriggerTracker?.onWindowFocusLost()
        }
    }

    private fun hideSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val target = intent.getStringExtra(EXTRA_LAUNCH_TARGET) ?: TARGET_NONE
        val provider = intent.getStringExtra(EXTRA_SMART_HOME_PROVIDER)
        // Clear extras so subsequent pauses/resumes do not re-trigger
        intent.removeExtra(EXTRA_LAUNCH_TARGET)
        intent.removeExtra(EXTRA_SMART_HOME_PROVIDER)

        if (target != TARGET_NONE) {
            handleLaunchTarget(target, provider)
        }
    }

    fun handleLaunchTarget(target: String, provider: String? = null) {
        when (target) {
            TARGET_SETTINGS -> launchSettings()
            TARGET_CLOCK -> launchClockTarget()
            TARGET_SMART_HOME -> launchSmartHomeTarget(provider)
        }
    }

    /**
     * Launches SettingsActivity directly in the same task stack.
     * When user presses Back, Android pops SettingsActivity and reveals ScreensaverActivity.
     */
    fun launchSettings() {
        try {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ScreensaverActivity", "Failed to launch Settings: ${e.message}")
        }
    }

    /**
     * Requests keyguard dismissal if locked, then launches target Clock app.
     * Leaves ScreensaverActivity active in the task stack beneath the Clock app.
     */
    fun launchClockTarget() {
        dismissKeyguardAndRun {
            performLaunchClock()
        }
    }

    private fun performLaunchClock() {
        val pm = packageManager

        // 1. Google Clock (Pixel / stock Android) - explicit intent for alarms tab
        try {
            val googleClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                setPackage("com.google.android.deskclock")
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (googleClockIntent.resolveActivity(pm) != null) {
                startActivity(googleClockIntent)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreensaverActivity", "Failed Google Clock ACTION_SHOW_ALARMS: ${e.message}")
        }

        // 2. Google Clock launch intent
        val googleClockLaunch = pm.getLaunchIntentForPackage("com.google.android.deskclock")
        if (googleClockLaunch != null) {
            try {
                googleClockLaunch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(googleClockLaunch)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverActivity", "Failed Google Clock launch: ${e.message}")
            }
        }

        // 3. Samsung Clock launch intent
        val samsungClockLaunch = pm.getLaunchIntentForPackage("com.sec.android.app.clockpackage")
        if (samsungClockLaunch != null) {
            try {
                samsungClockLaunch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(samsungClockLaunch)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverActivity", "Failed Samsung Clock: ${e.message}")
            }
        }

        // 4. Generic ACTION_SHOW_ALARMS
        try {
            val showAlarmsIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (showAlarmsIntent.resolveActivity(pm) != null) {
                startActivity(showAlarmsIntent)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreensaverActivity", "Failed generic ACTION_SHOW_ALARMS: ${e.message}")
        }

        // 5. Generic ACTION_SET_ALARM
        try {
            val setAlarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (setAlarmIntent.resolveActivity(pm) != null) {
                startActivity(setAlarmIntent)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreensaverActivity", "Failed generic ACTION_SET_ALARM: ${e.message}")
        }

        // 6. Generic Clock packages fallback
        val otherPackages = listOf(
            "com.android.deskclock",
            "com.oneplus.deskclock",
            "com.coloros.alarm",
            "com.huawei.deskclock"
        )
        for (pkg in otherPackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                    Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                    return
                } catch (_: Exception) {}
            }
        }

        Toast.makeText(this, "Clock app not found", Toast.LENGTH_SHORT).show()
    }

    /**
     * Requests keyguard dismissal if locked, then launches Google Home or Amazon Alexa.
     * Leaves ScreensaverActivity active in the task stack beneath the smart home app.
     */
    fun launchSmartHomeTarget(provider: String? = null) {
        dismissKeyguardAndRun {
            performLaunchSmartHome(provider)
        }
    }

    private fun performLaunchSmartHome(provider: String?) {
        val pm = packageManager
        val targetProvider = provider ?: preferencesManager.getSettings().smartHomeProvider

        if (targetProvider.equals("alexa", ignoreCase = true)) {
            // Amazon Alexa
            val alexaIntent = pm.getLaunchIntentForPackage("com.amazon.dee.app")
            if (alexaIntent != null) {
                try {
                    alexaIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(alexaIntent)
                    Toast.makeText(this, "Opening Amazon Alexa…", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    android.util.Log.e("ScreensaverActivity", "Failed to start Amazon Alexa: ${e.message}")
                }
            }

            // Fallback: Play Store / Web
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.amazon.dee.app"))
                startActivity(marketIntent)
                Toast.makeText(this, "Opening Alexa on Google Play…", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Exception) {
                try {
                    val playWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.amazon.dee.app"))
                    startActivity(playWebIntent)
                    Toast.makeText(this, "Opening Alexa on Google Play…", Toast.LENGTH_SHORT).show()
                    return
                } catch (_: Exception) {}
            }

            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://alexa.amazon.com"))
                startActivity(webIntent)
                Toast.makeText(this, "Opening Alexa Web…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverActivity", "Failed to start Alexa Web: ${e.message}")
            }

            Toast.makeText(this, "Amazon Alexa app not found", Toast.LENGTH_LONG).show()
        } else {
            // Google Home
            val homeIntent = pm.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
            if (homeIntent != null) {
                try {
                    homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(homeIntent)
                    Toast.makeText(this, "Opening Google Home…", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    android.util.Log.e("ScreensaverActivity", "Failed to start Google Home: ${e.message}")
                }
            }

            // Fallback: Google Nest
            val nestIntent = pm.getLaunchIntentForPackage("com.nest.android")
            if (nestIntent != null) {
                try {
                    nestIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(nestIntent)
                    Toast.makeText(this, "Opening Google Nest…", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    android.util.Log.e("ScreensaverActivity", "Failed to start Nest: ${e.message}")
                }
            }

            // Fallback: Google Home Web
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://home.google.com"))
                startActivity(webIntent)
                Toast.makeText(this, "Opening Google Home Web…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("ScreensaverActivity", "Failed to start Web: ${e.message}")
            }

            // Fallback: Play Store
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.chromecast.app"))
                startActivity(marketIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Google Home app not found", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dismissKeyguardAndRun(action: () -> Unit) {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager != null && keyguardManager.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        action()
                    }

                    override fun onDismissCancelled() {
                        android.util.Log.d("ScreensaverActivity", "Keyguard dismissal cancelled")
                    }

                    override fun onDismissError() {
                        action()
                    }
                })
            } else {
                action()
            }
        } else {
            action()
        }
    }

    private fun applyOrientation(mode: String) {
        val forcedOrientation = when (mode) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "system" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> null
        }

        if (forcedOrientation != null) {
            orientationJob?.cancel()
            orientationJob = null
            requestedOrientation = forcedOrientation
            return
        }

        // "sensor" mode: dynamically track 3D orientation from gravity vector
        orientationJob?.cancel()
        orientationJob = lifecycleScope.launch {
            tiltSensorTracker.orientationFlow().collect { desiredOrientation ->
                if (requestedOrientation != desiredOrientation) {
                    requestedOrientation = desiredOrientation
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        alarmTriggerTracker?.stop()
        alarmTriggerTracker = null
        orientationJob?.cancel()
        orientationJob = null
        tiltJob?.cancel()
        tiltJob = null
        DoNotDisturbController.getInstance(this).reset()
        LocationController.getInstance(this).reset()
    }
}
