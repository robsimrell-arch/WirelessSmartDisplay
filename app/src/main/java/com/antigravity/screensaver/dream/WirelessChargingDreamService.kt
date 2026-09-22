package com.antigravity.screensaver.dream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.hardware.SensorManager
import android.os.Build
import android.view.OrientationEventListener
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.antigravity.screensaver.data.AlarmTriggerTracker
import com.antigravity.screensaver.data.BatteryStateTracker
import com.antigravity.screensaver.data.DoNotDisturbController
import com.antigravity.screensaver.data.LocationController
import com.antigravity.screensaver.data.PreferencesManager
import com.antigravity.screensaver.data.TiltSensorTracker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.antigravity.screensaver.ui.dream.SmartDisplayScreen
import com.antigravity.screensaver.ui.theme.ScreenSaverTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class WirelessChargingDreamService : DreamService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val appViewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private lateinit var preferencesManager: PreferencesManager
    private var batteryReceiver: BroadcastReceiver? = null
    private var orientationJob: Job? = null
    private var currentAppliedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var tiltSensorTracker: TiltSensorTracker? = null
    private var tiltJob: Job? = null
    private val isFlatDarkened = MutableStateFlow(false)
    private var alarmTriggerTracker: AlarmTriggerTracker? = null
    private var hasGainedWindowFocus = false

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
            setScreenOrientation(forcedOrientation)
            return
        }

        // "sensor" mode: dynamically track 3D orientation from gravity vector
        // Eliminates AOSP's tilt cutoff that caused ORIENTATION_UNKNOWN when propped up on a stand
        orientationJob?.cancel()
        orientationJob = CoroutineScope(Dispatchers.Main).launch {
            tiltSensorTracker?.orientationFlow()?.collect { desiredOrientation ->
                setScreenOrientation(desiredOrientation)
            }
        }
    }

    private fun setScreenOrientation(desired: Int) {
        if (currentAppliedOrientation != desired) {
            android.util.Log.d(
                "DreamService",
                "Setting screen orientation to $desired (was $currentAppliedOrientation)"
            )
            currentAppliedOrientation = desired
            val lp = window.attributes
            lp.screenOrientation = desired
            window.attributes = lp
        }
    }

    private fun applyPostureState(isProppedUp: Boolean) {
        if (!isProppedUp) {
            // Device is flat: blackout the display completely without exiting the dream.
            // (Exiting or calling wakeUp() causes Android's PowerManager to fire WAKE_REASON_DREAM_FINISHED and turn the screen ON!)
            android.util.Log.d("DreamService", "Flat posture: blacking out screen (0 brightness, OLED true black)")
            isFlatDarkened.value = true
            isScreenBright = false
            val lp = window.attributes
            lp.screenBrightness = 0.0f
            window.attributes = lp
        } else {
            // Device is propped up: restore brightness and display smart display
            android.util.Log.d("DreamService", "Propped up posture: restoring screen and widgets")
            isFlatDarkened.value = false
            isScreenBright = true
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = appViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
                android.util.Log.d("DreamOverlay", "HiddenApiBypass exemptions applied successfully")
            } catch (t: Throwable) {
                android.util.Log.e("DreamOverlay", "HiddenApiBypass failed: ${t.message}", t)
            }
        }
        disconnectDreamOverlay()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        preferencesManager = PreferencesManager(this)
        tiltSensorTracker = TiltSensorTracker(this)
    }

    /**
     * Completely eliminates the system DreamOverlay (which renders bright white status icons).
     * 1. Forces mPreviewMode = true: SystemUI's DreamOverlay explicitly suppresses status bar
     *    and complications in preview mode (as observed: preview doesn't show the icons).
     * 2. Forces mShouldShowComplications = false.
     * 3. Unbinds and nullifies mOverlayConnection to tear down the overlay connection entirely.
     */
    private fun disconnectDreamOverlay() {
        try {
            val dreamClass = DreamService::class.java

            // 1. Force mPreviewMode = true
            try {
                val previewField = dreamClass.getDeclaredField("mPreviewMode")
                previewField.isAccessible = true
                previewField.setBoolean(this, true)
                android.util.Log.d("DreamOverlay", "Forced mPreviewMode = true")
            } catch (t: Throwable) {
                android.util.Log.d("DreamOverlay", "mPreviewMode field: ${t.message}")
            }

            // 2. Force mShouldShowComplications = false
            try {
                val compField = dreamClass.getDeclaredField("mShouldShowComplications")
                compField.isAccessible = true
                compField.setBoolean(this, false)
                android.util.Log.d("DreamOverlay", "Forced mShouldShowComplications = false")
            } catch (t: Throwable) {
                android.util.Log.d("DreamOverlay", "mShouldShowComplications field: ${t.message}")
            }

            // 3. Disconnect and unbind mOverlayConnection
            val fieldNames = listOf("mOverlayConnection", "mOverlayCallback")
            for (fieldName in fieldNames) {
                try {
                    val field = dreamClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val connection = field.get(this)
                    if (connection != null) {
                        android.util.Log.d("DreamOverlay", "Found $fieldName = ${connection.javaClass.name}")
                        try {
                            val unbindMethod = connection.javaClass.getMethod("unbind")
                            unbindMethod.invoke(connection)
                            android.util.Log.d("DreamOverlay", "Called unbind() on $fieldName")
                        } catch (t: Throwable) {
                            android.util.Log.d("DreamOverlay", "unbind error: ${t.message}")
                        }
                        field.set(this, null)
                        android.util.Log.d("DreamOverlay", "Nullified $fieldName")
                    }
                } catch (e: NoSuchFieldException) {
                    android.util.Log.d("DreamOverlay", "Field $fieldName not found")
                } catch (e: Throwable) {
                    android.util.Log.w("DreamOverlay", "Error accessing $fieldName: ${e.message}")
                }
            }

            // 4. Scan all declared fields for any remaining overlay objects
            for (field in dreamClass.declaredFields) {
                if (field.name.contains("Overlay", ignoreCase = true) ||
                    field.type.name.contains("Overlay", ignoreCase = true)
                ) {
                    try {
                        field.isAccessible = true
                        val value = field.get(this)
                        if (value != null && !field.type.isPrimitive) {
                            android.util.Log.d("DreamOverlay", "Clearing overlay field: ${field.name}")
                            try {
                                val unbindMethod = value.javaClass.getMethod("unbind")
                                unbindMethod.invoke(value)
                            } catch (_: Throwable) {}
                            field.set(this, null)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("DreamOverlay", "Could not disconnect DreamOverlay", t)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        disconnectDreamOverlay()

        isInteractive = true
        isFullscreen = true
        isScreenBright = true // MUST be true so Android does not enter doze and black out the screen!

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        // Apply sensor / forced orientation mode
        val settings = preferencesManager.getSettings()
        applyOrientation(settings.orientationMode)

        // Safe edge-to-edge system bar hiding via WindowCompat with transient swipe behavior
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        window.decorView.postDelayed({
            disconnectDreamOverlay()
        }, 300)

        // Additional delayed attempt in case overlay takes longer to appear
        window.decorView.postDelayed({
            disconnectDreamOverlay()
        }, 1000)

        // Exclude upper region from system gesture interception on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.post {
                val w = window.decorView.width
                val h = window.decorView.height
                if (w > 0 && h > 0) {
                    val exclusionZone = Rect(0, 0, w, (h * 0.4f).toInt())
                    window.decorView.systemGestureExclusionRects = listOf(exclusionZone)
                }
            }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // Attach lifecycle owners to decorView FIRST before adding content view
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeViewModelStoreOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)

        val composeView = ComposeView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@WirelessChargingDreamService)
            )
            setViewTreeLifecycleOwner(this@WirelessChargingDreamService)
            setViewTreeViewModelStoreOwner(this@WirelessChargingDreamService)
            setViewTreeSavedStateRegistryOwner(this@WirelessChargingDreamService)
            setContent {
                ScreenSaverTheme {
                    val isFlat by isFlatDarkened.collectAsState()
                    if (isFlat) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .clickable { wakeUp() }
                        )
                    } else {
                        SmartDisplayScreen(
                            onExitScreensaver = { wakeUp() }
                        )
                    }
                }
            }
        }

        setContentView(composeView)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hasGainedWindowFocus = true
            disconnectDreamOverlay()
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else if (hasGainedWindowFocus) {
            android.util.Log.d("DreamService", "Window focus lost while dreaming. Yielding to foreground activity/alarm.")
            alarmTriggerTracker?.onWindowFocusLost()
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        disconnectDreamOverlay()
        window.decorView.postDelayed({
            disconnectDreamOverlay()
        }, 1500)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Alarm & Activity yield tracker: ensures Google Clock alarm screen displays immediately
        alarmTriggerTracker = AlarmTriggerTracker(this) {
            android.util.Log.i("DreamService", "Alarm/Activity trigger received! Waking up and terminating dream.")
            try {
                // Restore preview mode flag so system cleans up normally
                val dreamClass = DreamService::class.java
                val previewField = dreamClass.getDeclaredField("mPreviewMode")
                previewField.isAccessible = true
                previewField.setBoolean(this, false)
            } catch (_: Throwable) {}
            wakeUp()
            finish()
        }
        alarmTriggerTracker?.start()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val settings = preferencesManager.getSettings()

        // If user explicitly enabled wirelessOnly in settings:
        if (settings.wirelessOnly) {
            val isWireless = BatteryStateTracker.isCurrentlyChargingWirelessly(this)
            val isWired = BatteryStateTracker.isCurrentlyChargingWired(this)
            if (isWired && !isWireless) {
                android.util.Log.d("DreamService", "Dismissing: wirelessOnly is enabled and wired charging detected")
                finish()
                return
            }
        }

        // Register live receiver to monitor if switched to wired cable while wirelessOnly is enabled
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    val isWired = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                            plugged == BatteryManager.BATTERY_PLUGGED_USB
                    val isWireless = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

                    val currentSettings = preferencesManager.getSettings()
                    if (currentSettings.wirelessOnly && isWired && !isWireless) {
                        finish()
                    }
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Tilt posture monitoring: If user enabled requireProppedUp, only stay active when propped up
        if (settings.requireProppedUp && tiltSensorTracker?.hasSensor == true) {
            tiltJob?.cancel()
            tiltJob = CoroutineScope(Dispatchers.Main).launch {
                tiltSensorTracker?.proppedUpStateFlow(
                    thresholdDegrees = settings.minTiltAngleDegrees,
                    flatGracePeriodMs = 2000L
                )?.collect { isProppedUp ->
                    applyPostureState(isProppedUp)
                }
            }
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        alarmTriggerTracker?.stop()
        alarmTriggerTracker = null
        hasGainedWindowFocus = false
        tiltJob?.cancel()
        tiltJob = null
        orientationJob?.cancel()
        orientationJob = null
        DoNotDisturbController.getInstance(this).reset()
        LocationController.getInstance(this).reset()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            batteryReceiver = null
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        alarmTriggerTracker?.stop()
        alarmTriggerTracker = null
        tiltJob?.cancel()
        tiltJob = null
        orientationJob?.cancel()
        orientationJob = null
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            appViewModelStore.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        alarmTriggerTracker?.stop()
        alarmTriggerTracker = null
        tiltJob?.cancel()
        tiltJob = null
        orientationJob?.cancel()
        orientationJob = null
        DoNotDisturbController.getInstance(this).reset()
        LocationController.getInstance(this).reset()
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            appViewModelStore.clear()
        }
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            batteryReceiver = null
        }
    }
}
