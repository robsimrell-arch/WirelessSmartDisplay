package com.antigravity.screensaver.ui.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.R
import com.antigravity.screensaver.data.DoNotDisturbController
import com.antigravity.screensaver.data.DreamSystemSettingsController
import com.antigravity.screensaver.data.LocationController
import com.antigravity.screensaver.data.PreferencesManager
import com.antigravity.screensaver.data.TiltSensorTracker
import com.antigravity.screensaver.model.DisplaySettings
import com.antigravity.screensaver.model.NightColor
import com.antigravity.screensaver.ui.dream.PreviewActivity
import com.antigravity.screensaver.ui.theme.NightRed
import com.antigravity.screensaver.ui.theme.OledBlack
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.SurfaceDark
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val dndController = remember { DoNotDisturbController.getInstance(context) }
    val locationController = remember { LocationController.getInstance(context) }
    val dreamSystemController = remember { DreamSystemSettingsController.getInstance(context) }
    val tiltTracker = remember { TiltSensorTracker(context) }
    var hasDndPermission by remember { mutableStateOf(dndController.hasPermission()) }
    var hasLocationPermission by remember { mutableStateOf(locationController.hasPermission()) }
    var currentTiltAngle by remember { mutableStateOf(0f) }
    var settings by remember { mutableStateOf(prefsManager.getSettings()) }

    LaunchedEffect(Unit) {
        hasDndPermission = dndController.hasPermission()
        hasLocationPermission = locationController.hasPermission()
        dreamSystemController.syncSystemScreensaverPolicy(settings.wirelessOnly)
        tiltTracker.inclineAngleFlow().collect { angle ->
            currentTiltAngle = angle
        }
    }

    fun update(newSettings: DisplaySettings) {
        settings = newSettings
        prefsManager.updateSettings(newSettings)
        dreamSystemController.syncSystemScreensaverPolicy(newSettings.wirelessOnly)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                            ?: (context as? Activity)?.finish()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimaryWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        containerColor = OledBlack
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, PreviewActivity::class.java))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PixelBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = OledBlack)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview", color = OledBlack, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PixelBlue)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("System Setup")
                }
            }

            // System Instruction Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isWiredReady = remember(settings) { dreamSystemController.isWiredChargingAllowed() }
                    Icon(
                        imageVector = if (isWiredReady) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                        contentDescription = null,
                        tint = if (isWiredReady) Color(0xFF4CAF50) else PixelBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isWiredReady)
                            "System screensaver is ready to activate on timeout while charging (cable or wireless dock)."
                        else
                            stringResource(R.string.system_settings_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isWiredReady) TextPrimaryWhite else TextSecondaryMuted
                    )
                }
            }

            // Charging & Behavior Section
            SectionHeader(
                title = stringResource(R.string.section_behavior),
                icon = Icons.Rounded.BatteryChargingFull
            )

            SettingsToggleItem(
                title = stringResource(R.string.pref_require_propped_up_title),
                summary = stringResource(R.string.pref_require_propped_up_summary),
                checked = settings.requireProppedUp,
                onCheckedChange = { update(settings.copy(requireProppedUp = it)) }
            )

            if (settings.requireProppedUp) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Minimum Incline: ${settings.minTiltAngleDegrees}°",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryWhite
                            )
                            val isPropped = currentTiltAngle >= settings.minTiltAngleDegrees
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPropped) Color(0x334CAF50) else Color(0x33FFA726)
                            ) {
                                Text(
                                    text = if (isPropped) "Propped Up (${currentTiltAngle.roundToInt()}°)" else "Laying Flat (${currentTiltAngle.roundToInt()}°)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPropped) Color(0xFF4CAF50) else Color(0xFFFFA726)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = settings.minTiltAngleDegrees.toFloat(),
                            onValueChange = { update(settings.copy(minTiltAngleDegrees = it.roundToInt())) },
                            valueRange = 15f..60f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = PixelBlue,
                                activeTrackColor = PixelBlue
                            )
                        )
                        Text(
                            text = "Tilt your phone on your stand to see the live angle above. Screensaver will turn off if phone is laid flatter than ${settings.minTiltAngleDegrees}°.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }

            SettingsToggleItem(
                title = stringResource(R.string.pref_wireless_only_title),
                summary = stringResource(R.string.pref_wireless_only_summary),
                checked = settings.wirelessOnly,
                onCheckedChange = { update(settings.copy(wirelessOnly = it)) }
            )

            // OLED & Display Section
            SectionHeader(
                title = stringResource(R.string.section_oled),
                icon = Icons.Rounded.Security
            )

            SettingsToggleItem(
                title = stringResource(R.string.pref_burn_in_title),
                summary = stringResource(R.string.pref_burn_in_summary),
                checked = settings.burnInProtection,
                onCheckedChange = { update(settings.copy(burnInProtection = it)) }
            )

            SettingsToggleItem(
                title = stringResource(R.string.pref_night_mode_title),
                summary = stringResource(R.string.pref_night_mode_summary),
                checked = settings.nightModeEnabled,
                onCheckedChange = { update(settings.copy(nightModeEnabled = it)) }
            )

            // Night Display Color Palette Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.pref_night_color_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pref_night_color_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NightColor.entries.forEach { colorOption ->
                            val isSelected = settings.nightColor.equals(colorOption.id, ignoreCase = true)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        update(settings.copy(nightColor = colorOption.id))
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 2.5.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = 0.2f),
                                                    shape = CircleShape
                                                )
                                            }
                                        )
                                        .padding(4.dp)
                                        .background(colorOption.primaryColor, CircleShape)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = if (colorOption == NightColor.WARM_WHITE) Color.Black else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = colorOption.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TextPrimaryWhite else TextSecondaryMuted
                                )
                            }
                        }
                    }
                }
            }

            SettingsToggleItem(
                title = stringResource(R.string.pref_tap_to_toggle_title),
                summary = stringResource(R.string.pref_tap_to_toggle_summary),
                checked = settings.tapToToggleNight,
                onCheckedChange = { update(settings.copy(tapToToggleNight = it)) }
            )

            // Midnight Path Floodlight Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = stringResource(R.string.pref_midnight_path_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.pref_midnight_path_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = settings.enableMidnightPath,
                            onCheckedChange = { update(settings.copy(enableMidnightPath = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PixelBlue
                            )
                        )
                    }

                    if (settings.enableMidnightPath) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.pref_midnight_path_brightness),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimaryWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(settings.midnightPathBrightness * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                color = settings.activeNightColor.primaryColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = settings.midnightPathBrightness,
                            onValueChange = { update(settings.copy(midnightPathBrightness = it)) },
                            valueRange = 0.20f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = settings.activeNightColor.primaryColor,
                                activeTrackColor = settings.activeNightColor.primaryColor,
                                inactiveTrackColor = settings.activeNightColor.primaryColor.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }

            SettingsToggleItem(
                title = "Do Not Disturb in Night Mode",
                summary = "Automatically turn on Priority DND during night mode; restores previous state on wake or when room lights turn on.",
                checked = settings.enableDndInNightMode,
                onCheckedChange = { update(settings.copy(enableDndInNightMode = it)) }
            )

            if (settings.enableDndInNightMode && !hasDndPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x33FF9800)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dndController.openPermissionSettings(context) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DND Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFFFB74D),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap here to grant Do Not Disturb access in Android Settings so screensaver can manage DND.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimaryWhite
                            )
                        }
                    }
                }
            }

            SettingsToggleItem(
                title = "Mute Location in Night Mode",
                summary = "Temporarily turns off Location during night mode to eliminate the flashing blue privacy indicator dot; restores location when waking up or room lights turn on.",
                checked = settings.muteLocationInNightMode,
                onCheckedChange = { update(settings.copy(muteLocationInNightMode = it)) }
            )

            if (settings.muteLocationInNightMode && !hasLocationPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x332196F3)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = PixelBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                color = PixelBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant privileged setting access via ADB:\nadb shell pm grant com.antigravity.screensaver android.permission.WRITE_SECURE_SETTINGS",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimaryWhite
                            )
                        }
                    }
                }
            }

            // Night Mode Brightness Dimmer Card
            val activeNightColor = settings.activeNightColor.primaryColor
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Night Mode Dimmer",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(settings.nightBrightness * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = activeNightColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dims night clock elements down to complete black (0% to 100%). Can also swipe vertically on ambient display.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.nightBrightness,
                        onValueChange = { update(settings.copy(nightBrightness = it)) },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeNightColor,
                            activeTrackColor = activeNightColor,
                            inactiveTrackColor = activeNightColor.copy(alpha = 0.25f)
                        )
                    )
                }
            }

            // Screen Orientation Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Screen Orientation",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose screensaver posture. 'Auto' rotates with physical stand posture even when system auto-rotate is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val orientationOptions = listOf(
                        "sensor" to "Auto (Sensor)",
                        "landscape" to "Landscape",
                        "portrait" to "Portrait",
                        "system" to "System Default"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        orientationOptions.take(2).forEach { (mode, label) ->
                            val selected = settings.orientationMode == mode
                            Surface(
                                onClick = { update(settings.copy(orientationMode = mode)) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) PixelBlue else Color(0x22FFFFFF),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (selected) OledBlack else TextPrimaryWhite,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        orientationOptions.drop(2).forEach { (mode, label) ->
                            val selected = settings.orientationMode == mode
                            Surface(
                                onClick = { update(settings.copy(orientationMode = mode)) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) PixelBlue else Color(0x22FFFFFF),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (selected) OledBlack else TextPrimaryWhite,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Modular Widgets Section
            SectionHeader(
                title = stringResource(R.string.section_widgets),
                icon = Icons.Rounded.Widgets
            )

            SettingsToggleItem(
                title = stringResource(R.string.pref_show_battery_title),
                summary = stringResource(R.string.pref_show_battery_summary),
                checked = settings.showBattery,
                onCheckedChange = { update(settings.copy(showBattery = it)) }
            )

            if (settings.showBattery) {
                SettingsToggleItem(
                    title = "Show Charging Wattage",
                    summary = "Display live charging power in watts (e.g. 14.8W) next to battery percentage",
                    checked = settings.showChargingWatts,
                    onCheckedChange = { update(settings.copy(showChargingWatts = it)) }
                )
            }

            SettingsToggleItem(
                title = stringResource(R.string.pref_show_weather_title),
                summary = stringResource(R.string.pref_show_weather_summary),
                checked = settings.showWeather,
                onCheckedChange = { update(settings.copy(showWeather = it)) }
            )

            if (settings.showWeather) {
                SettingsToggleItem(
                    title = "Show Tomorrow's Forecast",
                    summary = "Display tomorrow's condition and high/low range on a second line",
                    checked = settings.showTomorrowWeather,
                    onCheckedChange = { update(settings.copy(showTomorrowWeather = it)) }
                )
            }

            SettingsToggleItem(
                title = stringResource(R.string.pref_show_alarm_title),
                summary = stringResource(R.string.pref_show_alarm_summary),
                checked = settings.showAlarm,
                onCheckedChange = { update(settings.copy(showAlarm = it)) }
            )

            SettingsToggleItem(
                title = stringResource(R.string.pref_show_seconds_title),
                summary = stringResource(R.string.pref_show_seconds_summary),
                checked = settings.showSeconds,
                onCheckedChange = { update(settings.copy(showSeconds = it)) }
            )

            SettingsToggleItem(
                title = "Show Smart Home Shortcut",
                summary = "Display quick launcher tile for smart home controls",
                checked = settings.showGoogleHomePill,
                onCheckedChange = { update(settings.copy(showGoogleHomePill = it)) }
            )

            if (settings.showGoogleHomePill) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Smart Home Provider",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimaryWhite
                    )
                    Text(
                        text = "Choose which ecosystem to launch when tapping the smart controls tile",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val providers = listOf(
                        "google" to "Google Home",
                        "alexa" to "Amazon Alexa"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        providers.forEach { (key, label) ->
                            val selected = settings.smartHomeProvider == key
                            val activeColor = if (key == "alexa") Color(0xFF00CAFF) else PixelBlue
                            Surface(
                                onClick = { update(settings.copy(smartHomeProvider = key)) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) activeColor else Color(0x22FFFFFF),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (key == "alexa") Icons.Rounded.GraphicEq else Icons.Rounded.Home,
                                        contentDescription = null,
                                        tint = if (selected) OledBlack else TextPrimaryWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        color = if (selected) OledBlack else TextPrimaryWhite,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            SettingsToggleItem(
                title = "Show Brown Noise Tile",
                summary = "Display quick sleep sound tile to play soothing brown noise",
                checked = settings.showBrownNoise,
                onCheckedChange = { update(settings.copy(showBrownNoise = it)) }
            )

            // Weather Customization Section
            if (settings.showWeather) {
                SectionHeader(
                    title = stringResource(R.string.section_weather),
                    icon = Icons.Rounded.Cloud
                )

                OutlinedTextField(
                    value = settings.customCity,
                    onValueChange = { update(settings.copy(customCity = it)) },
                    label = { Text("Custom City (e.g. Chicago, London)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        focusedBorderColor = PixelBlue,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    )
                )

                SettingsToggleItem(
                    title = stringResource(R.string.pref_weather_fahrenheit_title),
                    summary = stringResource(R.string.pref_weather_fahrenheit_summary),
                    checked = settings.useFahrenheit,
                    onCheckedChange = { update(settings.copy(useFahrenheit = it)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PixelBlue,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = PixelBlue
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimaryWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OledBlack,
                    checkedTrackColor = PixelBlue,
                    uncheckedThumbColor = TextSecondaryMuted,
                    uncheckedTrackColor = Color(0x33888888)
                )
            )
        }
    }
}
