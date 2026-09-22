package com.antigravity.screensaver.ui.dream

import android.content.Intent
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.antigravity.screensaver.R
import com.antigravity.screensaver.ui.settings.SettingsActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.audio.BrownNoisePlayer
import com.antigravity.screensaver.data.AmbientLightTracker
import com.antigravity.screensaver.data.BatteryStateTracker
import com.antigravity.screensaver.data.DoNotDisturbController
import com.antigravity.screensaver.data.LocationController
import com.antigravity.screensaver.data.PreferencesManager
import com.antigravity.screensaver.data.WeatherRepository
import com.antigravity.screensaver.model.BatteryInfo
import com.antigravity.screensaver.model.DisplaySettings
import com.antigravity.screensaver.model.ThermostatInfo
import com.antigravity.screensaver.model.ThermostatMode
import com.antigravity.screensaver.model.WeatherInfo
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimRed
import com.antigravity.screensaver.ui.theme.NightRed
import com.antigravity.screensaver.ui.theme.OledBlack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SmartDisplayScreen(
    customSettings: DisplaySettings? = null,
    onExitScreensaver: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val batteryTracker = remember { BatteryStateTracker(context) }
    val weatherRepo = remember { WeatherRepository() }

    val liveSettings by prefsManager.settingsFlow().collectAsState(initial = prefsManager.getSettings())
    val settings = customSettings ?: liveSettings
    val nightColor = settings.activeNightColor
    var nightMode by remember { mutableStateOf(settings.nightModeEnabled) }
    var nightBrightness by remember { mutableStateOf(settings.nightBrightness) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    var isFloodlightActive by remember { mutableStateOf(false) }
    var floodlightBrightness by remember { mutableFloatStateOf(settings.midnightPathBrightness) }
    var floodlightTimeoutJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(settings.midnightPathBrightness) {
        if (!isFloodlightActive) {
            floodlightBrightness = settings.midnightPathBrightness
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var hideIndicatorJob by remember { mutableStateOf<Job?>(null) }

    val lightTracker = remember { AmbientLightTracker(context) }
    val dndController = remember { DoNotDisturbController.getInstance(context) }
    val locationController = remember { LocationController.getInstance(context) }
    val brownNoisePlayer = remember { BrownNoisePlayer() }
    val isBrownNoisePlaying by brownNoisePlayer.isPlaying.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            dndController.reset()
            locationController.reset()
            brownNoisePlayer.release()
            floodlightTimeoutJob?.cancel()
        }
    }

    LaunchedEffect(nightMode, settings.muteLocationInNightMode) {
        if (settings.muteLocationInNightMode) {
            if (nightMode) {
                locationController.muteLocation()
            } else {
                locationController.restoreLocation()
            }
        } else {
            locationController.restoreLocation()
        }
    }

    LaunchedEffect(settings.enableDndInNightMode) {
        if (lightTracker.hasSensor) {
            lightTracker.ambientLightFlow().collect { isDark ->
                nightMode = isDark
                if (settings.enableDndInNightMode) {
                    if (isDark) {
                        dndController.activateDnd()
                    } else {
                        dndController.deactivateDnd()
                    }
                } else {
                    dndController.deactivateDnd()
                }
            }
        } else {
            if (settings.enableDndInNightMode && nightMode) {
                dndController.activateDnd()
            } else {
                dndController.deactivateDnd()
            }
        }
    }

    val batteryInfo by batteryTracker.batteryInfoFlow().collectAsState(
        initial = batteryTracker.getCurrentBatteryInfo()
    )

    var weatherInfo by remember { mutableStateOf(WeatherInfo()) }

    LaunchedEffect(settings.showWeather, settings.customCity, settings.useFahrenheit) {
        if (settings.showWeather) {
            weatherInfo = weatherRepo.getWeather(settings.customCity, settings.useFahrenheit)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val view = LocalView.current
    var lastHapticPercent by remember { mutableStateOf((nightBrightness * 100).roundToInt()) }
    var zeroResistanceAccumulator by remember { mutableFloatStateOf(0f) }

    val brightnessGestureModifier = Modifier.pointerInput(nightMode, isFloodlightActive) {
        detectVerticalDragGestures(
            onDragStart = {
                hideIndicatorJob?.cancel()
                showBrightnessIndicator = true
                zeroResistanceAccumulator = 0f
                lastHapticPercent = if (isFloodlightActive) {
                    (floodlightBrightness * 100).roundToInt()
                } else {
                    (nightBrightness * 100).roundToInt()
                }
            },
            onDragEnd = {
                if (!isFloodlightActive) {
                    prefsManager.setNightBrightness(nightBrightness)
                }
                hideIndicatorJob = coroutineScope.launch {
                    delay(if (isFloodlightActive) 1400 else if (nightMode) 1800 else 2500)
                    showBrightnessIndicator = false
                }
            },
            onDragCancel = {
                hideIndicatorJob = coroutineScope.launch {
                    delay(1200)
                    showBrightnessIndicator = false
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                if (isFloodlightActive) {
                    val delta = -dragAmount / 450f
                    floodlightBrightness = (floodlightBrightness + delta).coerceIn(0.15f, 1.0f)
                } else if (nightMode) {
                    // Fine-tuned progressive curve for Night Mode:
                    // Low brightness (0% - 15%) is significantly less sensitive (~2200f divisor)
                    // so dialing 1%, 2%, 3% is steady and precise.
                    val divisor = if (nightBrightness <= 0.15f) {
                        2200f
                    } else {
                        val progress = ((nightBrightness - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        2200f - progress * 1400f // scales from 2200f down to 800f
                    }
                    val delta = -dragAmount / divisor
                    val candidate = nightBrightness + delta

                    // 1% Snap Shelf & Floor:
                    // When dragging down towards 0%, hold steadily on 1% (0.01f) until an extra deliberate drag is applied
                    val newBrightness = if (candidate <= 0.012f && delta < 0) {
                        zeroResistanceAccumulator += delta
                        if (zeroResistanceAccumulator < -0.012f) {
                            0.0f
                        } else {
                            0.01f
                        }
                    } else if (candidate <= 0.005f) {
                        0.0f
                    } else {
                        zeroResistanceAccumulator = 0f
                        candidate.coerceIn(0.01f, 1.0f)
                    }

                    nightBrightness = newBrightness

                    // Haptic click when hitting 1% or 0%
                    val currentPercent = (nightBrightness * 100).roundToInt()
                    if (currentPercent != lastHapticPercent) {
                        if (currentPercent == 1 || currentPercent == 0) {
                            try {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            } catch (_: Exception) {}
                        }
                        lastHapticPercent = currentPercent
                    }
                } else {
                    // Daytime mode standard gesture
                    val delta = -dragAmount / 350f
                    nightBrightness = (nightBrightness + delta).coerceIn(0.0f, 1.0f)
                }
                showBrightnessIndicator = true
                hideIndicatorJob?.cancel()
            }
        )
    }

    val tapGestureModifier = Modifier.pointerInput(settings.tapToToggleNight, nightMode, isFloodlightActive, settings.enableMidnightPath) {
        detectTapGestures(
            onTap = {
                if (isFloodlightActive) {
                    isFloodlightActive = false
                    floodlightTimeoutJob?.cancel()
                    showBrightnessIndicator = false
                } else if (settings.tapToToggleNight) {
                    nightMode = !nightMode
                    showBrightnessIndicator = false
                }
            },
            onDoubleTap = {
                if (isFloodlightActive) {
                    isFloodlightActive = false
                    floodlightTimeoutJob?.cancel()
                    showBrightnessIndicator = false
                } else {
                    brownNoisePlayer.stop()
                    onExitScreensaver()
                }
            },
            onLongPress = {
                if (nightMode && settings.enableMidnightPath && !isFloodlightActive) {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } catch (_: Exception) {}
                    isFloodlightActive = true
                    floodlightBrightness = settings.midnightPathBrightness
                    floodlightTimeoutJob?.cancel()
                    floodlightTimeoutJob = coroutineScope.launch {
                        delay(300_000L) // 5 minutes safety shutoff
                        isFloodlightActive = false
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .then(brightnessGestureModifier)
            .then(tapGestureModifier)
            .safeDrawingPadding()
            .padding(
                horizontal = if (nightMode) 16.dp else 24.dp,
                vertical = if (isLandscape) 12.dp else 24.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        BurnInProtectedContainer(
            enabled = settings.burnInProtection,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (nightMode) nightBrightness else 1.0f
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (nightMode) {
                // NIGHT CLOCK MODE (Clocks only, in chosen night color)
                if (isLandscape) {
                    // Landscape: Extra large centered digital clock filling screen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ClockWidget(
                            showSeconds = false,
                            nightMode = true,
                            nightColor = nightColor,
                            isLandscape = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Portrait: Dual clock layout (top half digital, bottom half analog)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            ClockWidget(
                                showSeconds = false,
                                nightMode = true,
                                nightColor = nightColor,
                                isLandscape = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnalogClockWidget(
                                nightColor = nightColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                // FULL COLOR MODE (2-column non-scrolling grid layout)
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxSize()
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            ClockWidget(
                                showSeconds = settings.showSeconds,
                                nightMode = false
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            TilesDashboardGrid(
                                settings = settings,
                                batteryInfo = batteryInfo,
                                weatherInfo = weatherInfo,
                                isBrownNoisePlaying = isBrownNoisePlaying,
                                onToggleBrownNoise = { brownNoisePlayer.toggle() },
                                verticalSpacing = 6.dp,
                                horizontalSpacing = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.35f),
                            contentAlignment = Alignment.Center
                        ) {
                            ClockWidget(
                                showSeconds = settings.showSeconds,
                                nightMode = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.65f),
                            contentAlignment = Alignment.Center
                        ) {
                            TilesDashboardGrid(
                                settings = settings,
                                batteryInfo = batteryInfo,
                                weatherInfo = weatherInfo,
                                isBrownNoisePlaying = isBrownNoisePlaying,
                                onToggleBrownNoise = { brownNoisePlayer.toggle() },
                                verticalSpacing = 8.dp,
                                horizontalSpacing = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Subtle Settings Cog: Top-Left corner, only in normal (daytime) mode
            if (!nightMode) {
                IconButton(
                    onClick = {
                        try {
                            ScreensaverActivity.launch(context, ScreensaverActivity.TARGET_SETTINGS)
                        } catch (e: Exception) {
                            android.util.Log.e("SmartDisplay", "Failed to launch Settings: ${e.message}")
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.32f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

        // Floating Brightness HUD Pill during drag / adjustment (only in full-color mode)
        AnimatedVisibility(
            visible = showBrightnessIndicator && !nightMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xEE1E1E1E),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            if (!nightMode) nightMode = true
                            nightBrightness = (nightBrightness - 0.05f).coerceIn(0.0f, 1.0f)
                            prefsManager.setNightBrightness(nightBrightness)
                            hideIndicatorJob?.cancel()
                            hideIndicatorJob = coroutineScope.launch {
                                delay(2500)
                                showBrightnessIndicator = false
                            }
                        },
                        shape = CircleShape,
                        color = Color(0x33FFA726),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-", color = NightAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Icon(
                        imageVector = Icons.Rounded.BrightnessMedium,
                        contentDescription = null,
                        tint = NightAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dimmer: ${(nightBrightness * 100).roundToInt()}%",
                        color = NightAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        onClick = {
                            if (!nightMode) nightMode = true
                            nightBrightness = (nightBrightness + 0.05f).coerceIn(0.0f, 1.0f)
                            prefsManager.setNightBrightness(nightBrightness)
                            hideIndicatorJob?.cancel()
                            hideIndicatorJob = coroutineScope.launch {
                                delay(2500)
                                showBrightnessIndicator = false
                            }
                        },
                        shape = CircleShape,
                        color = Color(0x33FFA726),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", color = NightAmber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Midnight Path Floodlight Full-Screen Illumination Layer
        AnimatedVisibility(
            visible = isFloodlightActive,
            enter = fadeIn(animationSpec = tween(350)),
            exit = fadeOut(animationSpec = tween(350)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(nightColor.primaryColor.copy(alpha = floodlightBrightness.coerceIn(0.15f, 1.0f))),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Nightlight,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.midnight_path_dismiss_hint),
                        color = Color.Black.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Extra-Large Night / Floodlight Brightness HUD (Centered for easy reading without glasses)
        AnimatedVisibility(
            visible = showBrightnessIndicator && (nightMode || isFloodlightActive),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val currentPercent = if (isFloodlightActive) {
                (floodlightBrightness * 100).roundToInt()
            } else {
                (nightBrightness * 100).roundToInt()
            }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xEE141414),
                border = BorderStroke(2.dp, nightColor.primaryColor.copy(alpha = 0.55f)),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BrightnessMedium,
                        contentDescription = "Brightness",
                        tint = nightColor.primaryColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "$currentPercent%",
                        color = nightColor.primaryColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TilesDashboardGrid(
    settings: DisplaySettings,
    batteryInfo: BatteryInfo,
    weatherInfo: WeatherInfo,
    isBrownNoisePlaying: Boolean,
    onToggleBrownNoise: () -> Unit,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 8.dp,
    horizontalSpacing: Dp = 8.dp
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Prominent Full-Width Alarm Dismiss Card on Top
        if (settings.showAlarm) {
            AlarmWidget(
                nightMode = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2x2 Grid for Remaining Utility Tiles (Weather, Battery, Google Home, Brown Noise)
        val utilityTiles = buildList<@Composable (Modifier) -> Unit> {
            if (settings.showWeather) {
                add { mod ->
                    WeatherWidget(
                        weatherInfo = weatherInfo,
                        useFahrenheit = settings.useFahrenheit,
                        nightMode = false,
                        showTomorrow = settings.showTomorrowWeather,
                        modifier = mod
                    )
                }
            }
            if (settings.showBattery) {
                add { mod ->
                    BatteryWidget(
                        batteryInfo = batteryInfo,
                        nightMode = false,
                        showWatts = settings.showChargingWatts,
                        modifier = mod
                    )
                }
            }
            if (settings.showGoogleHomePill) {
                add { mod ->
                    SmartHomeTile(
                        provider = settings.smartHomeProvider,
                        nightMode = false,
                        modifier = mod
                    )
                }
            }
            if (settings.showBrownNoise) {
                add { mod ->
                    BrownNoiseTile(
                        isPlaying = isBrownNoisePlaying,
                        onToggle = onToggleBrownNoise,
                        nightMode = false,
                        modifier = mod
                    )
                }
            }
        }

        val rows = utilityTiles.chunked(2)
        rows.forEach { rowTiles ->
            if (rowTiles.size == 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowTiles[0](Modifier.weight(1f).fillMaxHeight())
                    rowTiles[1](Modifier.weight(1f).fillMaxHeight())
                }
            } else if (rowTiles.size == 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowTiles[0](Modifier.fillMaxWidth())
                }
            }
        }
    }
}
