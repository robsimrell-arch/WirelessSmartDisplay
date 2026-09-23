package com.antigravity.screensaver.ui.dream

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.antigravity.screensaver.data.DoNotDisturbController
import com.antigravity.screensaver.data.LocationController
import com.antigravity.screensaver.data.PreferencesManager
import com.antigravity.screensaver.data.TiltSensorTracker
import com.antigravity.screensaver.ui.theme.ScreenSaverTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class PreviewActivity : ComponentActivity() {

    private var tiltSensorTracker: TiltSensorTracker? = null
    private var tiltJob: Job? = null
    private val isFlatDarkened = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefsManager = PreferencesManager(this)
        val settings = prefsManager.getSettings()
        tiltSensorTracker = TiltSensorTracker(this)

        requestedOrientation = when (settings.orientationMode) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "system" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }

        // Keep screen on during preview
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Safe edge-to-edge system bar hiding via WindowCompat
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        if (settings.requireProppedUp && tiltSensorTracker?.hasSensor == true) {
            tiltJob = CoroutineScope(Dispatchers.Main).launch {
                tiltSensorTracker?.proppedUpStateFlow(
                    thresholdDegrees = settings.minTiltAngleDegrees,
                    flatGracePeriodMs = 2000L
                )?.distinctUntilChanged()?.collect { isProppedUp ->
                    if (!isProppedUp) {
                        isFlatDarkened.value = true
                        val lp = window.attributes
                        lp.screenBrightness = 0.0f
                        window.attributes = lp
                    } else {
                        isFlatDarkened.value = false
                        val lp = window.attributes
                        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        window.attributes = lp
                    }
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
                            .clickable {
                                isFlatDarkened.value = false
                                val lp = window.attributes
                                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                window.attributes = lp
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Laying Flat (Tap to wake preview)",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SmartDisplayScreen(
                            onExitScreensaver = { finish() }
                        )

                        val configuration = LocalConfiguration.current
                        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                        // Floating Controls (TopStart in landscape so it never covers widgets)
                        Box(
                            modifier = Modifier
                                .align(if (isLandscape) Alignment.TopStart else Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0x66000000), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ScreenRotation,
                                        contentDescription = "Toggle Orientation",
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { finish() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0x66000000), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Exit Preview",
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tiltJob?.cancel()
        tiltJob = null
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        DoNotDisturbController.getInstance(this).reset()
        LocationController.getInstance(this).reset()
    }
}
