package com.antigravity.screensaver.ui.dream

import android.app.Activity
import android.app.KeyguardManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.service.dreams.DreamService
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
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
import com.antigravity.screensaver.data.SmartHomeWidgetBridge
import com.antigravity.screensaver.data.TiltSensorTracker
import com.antigravity.screensaver.ui.settings.SettingsActivity
import com.antigravity.screensaver.ui.theme.ScreenSaverTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var lastBackPressTime = 0L
    private var isNavigatingToChild = false
    private var automationHostView: AppWidgetHostView? = null
    private var mountedWidgetId: Int = -1
    private var pendingWidgetId: Int = -1

    companion object {
        const val EXTRA_LAUNCH_TARGET = "com.antigravity.screensaver.EXTRA_LAUNCH_TARGET"
        const val EXTRA_SMART_HOME_PROVIDER = "com.antigravity.screensaver.EXTRA_SMART_HOME_PROVIDER"

        const val TARGET_NONE = "none"
        const val TARGET_SETTINGS = "settings"
        const val TARGET_CLOCK = "clock"
        const val TARGET_SMART_HOME = "smart_home"
        const val TARGET_SMART_HOME_AUTOMATION = "smart_home_automation"

        /**
         * Evaluates whether focus loss on ScreensaverActivity should yield and finish.
         * Only yields if an alarm is active. Does NOT yield during child navigation
         * (Settings/Clock/Home) or normal activity focus loss, ensuring ScreensaverActivity
         * remains alive in the back stack.
         */
        fun evaluateShouldYieldOnFocusLost(isAlarmActive: Boolean, isNavigatingToChild: Boolean = false): Boolean {
            return isAlarmActive && !isNavigatingToChild
        }

        fun evaluateShouldYieldOnFocusLost(isNavigatingToChild: Boolean): Boolean {
            return evaluateShouldYieldOnFocusLost(isAlarmActive = false, isNavigatingToChild = isNavigatingToChild)
        }

        /**
         * Universal launch helper to route actions through ScreensaverActivity.
         * If already running inside ScreensaverActivity, dispatches directly in-place.
         * Otherwise, launches ScreensaverActivity with singleTop and passes target extras.
         */
        fun launch(context: Context, target: String = TARGET_NONE, provider: String? = null) {
            var ctx = context
            while (ctx is ContextWrapper && ctx !is DreamService && ctx !is ScreensaverActivity) {
                ctx = ctx.baseContext
            }
            if (ctx is ScreensaverActivity) {
                ctx.handleLaunchTarget(target, provider)
            } else {
                val intent = Intent(context, ScreensaverActivity::class.java).apply {
                    putExtra(EXTRA_LAUNCH_TARGET, target)
                    if (provider != null) {
                        putExtra(EXTRA_SMART_HOME_PROVIDER, provider)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                if (ctx is DreamService) {
                    try {
                        val dreamClass = DreamService::class.java
                        val previewField = dreamClass.getDeclaredField("mPreviewMode")
                        previewField.isAccessible = true
                        previewField.setBoolean(ctx, false)
                    } catch (_: Throwable) {}
                    ctx.finish()
                }
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

        // Handle Back button: require double Back press to exit to Home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this@ScreensaverActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // Apply display orientation and sensor posture tracking
        val settings = preferencesManager.getSettings()
        applyOrientation(settings.orientationMode)

        var lastOrientationMode = settings.orientationMode
        lifecycleScope.launch {
            preferencesManager.settingsFlow().collect { latest ->
                if (latest.orientationMode != lastOrientationMode) {
                    lastOrientationMode = latest.orientationMode
                    applyOrientation(latest.orientationMode)
                }
            }
        }

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
        try {
            SmartHomeWidgetBridge.getHost(this).startListening()
        } catch (_: Exception) {}
        ensureAutomationWidgetMounted()
        alarmTriggerTracker = AlarmTriggerTracker(this) {
            android.util.Log.i("ScreensaverActivity", "Alarm event detected! Finishing ScreensaverActivity to reveal AlarmActivity.")
            finish()
        }
        alarmTriggerTracker?.start()
    }

    override fun onStop() {
        super.onStop()
        try {
            SmartHomeWidgetBridge.getHost(this).stopListening()
        } catch (_: Exception) {}
        alarmTriggerTracker?.stop()
        alarmTriggerTracker = null
        hasGainedFocus = false
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToChild = false
        ensureAutomationWidgetMounted()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hasGainedFocus = true
            isNavigatingToChild = false
            hideSystemBars()
        } else {
            android.util.Log.d("ScreensaverActivity", "Window focus lost. ScreensaverActivity remains alive in back stack.")
            // Only yield if AlarmTriggerTracker detects an active or scheduled morning alarm
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
            TARGET_SMART_HOME_AUTOMATION -> launchSmartHomeAutomationTarget(provider)
        }
    }

    /**
     * Launches SettingsActivity directly in the same task stack.
     * When user presses Back, Android pops SettingsActivity and reveals ScreensaverActivity.
     */
    fun launchSettings() {
        try {
            isNavigatingToChild = true
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            isNavigatingToChild = false
            android.util.Log.e("ScreensaverActivity", "Failed to launch Settings: ${e.message}")
        }
    }

    /**
     * Launches target Clock app directly without biometric bouncer.
     * Leaves ScreensaverActivity active in the task stack beneath the Clock app.
     */
    fun launchClockTarget() {
        isNavigatingToChild = true
        performLaunchClock()
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
     * Launches Google Home or Amazon Alexa directly without biometric bouncer.
     * Leaves ScreensaverActivity active in the task stack beneath the smart home app.
     */
    fun launchSmartHomeTarget(provider: String? = null) {
        isNavigatingToChild = true
        performLaunchSmartHome(provider)
    }

    /**
     * Launches the configured Smart Home Automation (e.g. "Lights Out") directly via
     * Google Home's native Favorites Widget bridge (`RunAutomation`), keeping
     * ScreensaverActivity active without calling Gemini or opening the Automations tab.
     */
    fun launchSmartHomeAutomationTarget(provider: String? = null) {
        performLaunchSmartHomeAutomation(provider)
    }

    private fun ensureAutomationWidgetMounted() {
        val widgetId = preferencesManager.getSettings().smartHomeAutomationWidgetId
        if (!SmartHomeWidgetBridge.isWidgetValid(this, widgetId)) {
            automationHostView?.let { oldView ->
                (oldView.parent as? ViewGroup)?.removeView(oldView)
            }
            automationHostView = null
            mountedWidgetId = -1
            return
        }
        if (automationHostView != null && mountedWidgetId == widgetId) {
            return
        }

        val decorGroup = window.decorView as? ViewGroup ?: return
        automationHostView?.let { oldView ->
            (oldView.parent as? ViewGroup)?.removeView(oldView)
        }

        val hostView = SmartHomeWidgetBridge.createPreparedHostView(this, widgetId) ?: return
        val density = resources.displayMetrics.density
        val widthPx = (200f * density).toInt()
        val heightPx = (120f * density).toInt()

        hostView.alpha = 0f
        hostView.translationX = -10000f
        hostView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        decorGroup.addView(hostView, FrameLayout.LayoutParams(widthPx, heightPx))
        automationHostView = hostView
        mountedWidgetId = widgetId
    }

    private fun performLaunchSmartHomeAutomation(provider: String?) {
        val pm = packageManager
        val settings = preferencesManager.getSettings()
        val targetProvider = provider ?: settings.smartHomeProvider
        val label = settings.smartHomeAutomationLabel.trim().ifEmpty { "Lights Out" }
        val customUri = settings.smartHomeAutomationIntentUri.trim()

        // 1. Custom shortcut / deep-link Intent URI if configured
        if (customUri.isNotEmpty()) {
            try {
                isNavigatingToChild = true
                val parsedIntent = if (customUri.startsWith("intent:", ignoreCase = true)) {
                    Intent.parseUri(customUri, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent(Intent.ACTION_VIEW, Uri.parse(customUri))
                }
                parsedIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(parsedIntent)
                Toast.makeText(this, "Running $label…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                isNavigatingToChild = false
                android.util.Log.e("ScreensaverActivity", "Failed custom automation URI: ${e.message}")
            }
        }

        // 2. Direct 1-tap execution via bound Google Home / Alexa Favorites Widget
        val widgetId = settings.smartHomeAutomationWidgetId
        if (SmartHomeWidgetBridge.isWidgetValid(this, widgetId)) {
            ensureAutomationWidgetMounted()
            val result = SmartHomeWidgetBridge.triggerAutomationInHostView(automationHostView, label)
            if (result == SmartHomeWidgetBridge.TriggerResult.TRIGGERED) {
                Toast.makeText(this, "Running $label…", Toast.LENGTH_SHORT).show()
                return
            }

            // If Glance RemoteViews are still inflating, request an update and retry once
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
            if (info != null) {
                SmartHomeWidgetBridge.applyStandardWidgetOptions(this, widgetId, info)
            }
            Toast.makeText(this, "Running $label…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                delay(400L)
                val retry = SmartHomeWidgetBridge.triggerAutomationInHostView(automationHostView, label)
                if (retry != SmartHomeWidgetBridge.TriggerResult.TRIGGERED) {
                    Toast.makeText(
                        this@ScreensaverActivity,
                        "Select '$label' in Google Home widget setup",
                        Toast.LENGTH_SHORT
                    ).show()
                    startBindAndConfigureAutomationWidget(targetProvider, label)
                }
            }
            return
        }

        // 3. First-time setup: bind Google Home Favorites Widget so "Lights Out" runs in 1 tap
        if (startBindAndConfigureAutomationWidget(targetProvider, label)) {
            return
        }

        // 4. Fallback if provider app / widget is not installed
        if (targetProvider.equals("alexa", ignoreCase = true)) {
            try {
                isNavigatingToChild = true
                val alexaRoutinesIntent = Intent(Intent.ACTION_VIEW, Uri.parse("alexa://routines")).apply {
                    setPackage("com.amazon.dee.app")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (alexaRoutinesIntent.resolveActivity(pm) != null) {
                    startActivity(alexaRoutinesIntent)
                    Toast.makeText(this, "Opening Alexa Routines ($label)…", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (e: Exception) {
                isNavigatingToChild = false
                android.util.Log.e("ScreensaverActivity", "Failed Alexa routines deep link: ${e.message}")
            }
            performLaunchSmartHome("alexa")
        } else {
            performLaunchSmartHome("google")
        }
    }

    fun startBindAndConfigureAutomationWidget(providerKey: String, label: String): Boolean {
        val providerInfo = SmartHomeWidgetBridge.findWidgetProvider(this, providerKey) ?: return false
        return try {
            isNavigatingToChild = true
            val host = SmartHomeWidgetBridge.getHost(this)
            val appWidgetId = host.allocateAppWidgetId()
            pendingWidgetId = appWidgetId
            val mgr = AppWidgetManager.getInstance(this)

            Toast.makeText(
                this,
                "One-time setup: select '$label' in Google Home to enable 1-tap run",
                Toast.LENGTH_LONG
            ).show()

            if (mgr.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)) {
                launchWidgetConfigureOrFinish(appWidgetId)
            } else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(bindIntent, SmartHomeWidgetBridge.REQUEST_CODE_BIND_WIDGET)
            }
            true
        } catch (e: Exception) {
            isNavigatingToChild = false
            android.util.Log.e("ScreensaverActivity", "Failed to bind automation widget: ${e.message}", e)
            false
        }
    }

    private fun launchWidgetConfigureOrFinish(appWidgetId: Int) {
        val mgr = AppWidgetManager.getInstance(this)
        val info = mgr.getAppWidgetInfo(appWidgetId)
        if (info?.configure != null) {
            isNavigatingToChild = true
            val launched = SmartHomeWidgetBridge.startConfigureActivity(this, appWidgetId)
            if (!launched) {
                isNavigatingToChild = false
            }
        } else {
            onAutomationWidgetConfigured(appWidgetId)
        }
    }

    private fun onAutomationWidgetConfigured(appWidgetId: Int) {
        val oldId = preferencesManager.getSettings().smartHomeAutomationWidgetId
        if (oldId > 0 && oldId != appWidgetId) {
            try {
                SmartHomeWidgetBridge.getHost(this).deleteAppWidgetId(oldId)
            } catch (_: Exception) {}
        }
        preferencesManager.setSmartHomeAutomationWidgetId(appWidgetId)
        pendingWidgetId = -1
        ensureAutomationWidgetMounted()
        val label = preferencesManager.getSettings().smartHomeAutomationLabel.trim().ifEmpty { "Lights Out" }
        Toast.makeText(this, "$label linked! Tap '$label' anytime to run it directly.", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId

        when (requestCode) {
            SmartHomeWidgetBridge.REQUEST_CODE_BIND_WIDGET -> {
                if (resultCode == Activity.RESULT_OK && widgetId > 0) {
                    launchWidgetConfigureOrFinish(widgetId)
                } else {
                    if (widgetId > 0) {
                        try {
                            SmartHomeWidgetBridge.getHost(this).deleteAppWidgetId(widgetId)
                        } catch (_: Exception) {}
                    }
                    pendingWidgetId = -1
                }
            }
            SmartHomeWidgetBridge.REQUEST_CODE_CONFIGURE_WIDGET -> {
                if (resultCode == Activity.RESULT_OK && widgetId > 0) {
                    onAutomationWidgetConfigured(widgetId)
                } else {
                    if (widgetId > 0) {
                        try {
                            SmartHomeWidgetBridge.getHost(this).deleteAppWidgetId(widgetId)
                        } catch (_: Exception) {}
                    }
                    pendingWidgetId = -1
                }
            }
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
        action()
    }

    private fun applyOrientation(mode: String) {
        when (mode) {
            "portrait" -> {
                orientationJob?.cancel()
                orientationJob = null
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                return
            }
            "system" -> {
                orientationJob?.cancel()
                orientationJob = null
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                return
            }
            "landscape" -> {
                val currentSensor = tiltSensorTracker.getCurrentOrientation(awaitSensorMs = 60L)
                requestedOrientation = if (
                    currentSensor == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                    currentSensor == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                ) {
                    currentSensor
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                orientationJob?.cancel()
                orientationJob = lifecycleScope.launch {
                    tiltSensorTracker.orientationFlow().collect { desired ->
                        if ((desired == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                                desired == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) &&
                            requestedOrientation != desired
                        ) {
                            requestedOrientation = desired
                        }
                    }
                }
                return
            }
        }

        // Immediately set initial orientation synchronously to avoid portrait flash
        val initialOrientation = tiltSensorTracker.getCurrentOrientation(awaitSensorMs = 80L)
        if (requestedOrientation != initialOrientation) {
            requestedOrientation = initialOrientation
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
