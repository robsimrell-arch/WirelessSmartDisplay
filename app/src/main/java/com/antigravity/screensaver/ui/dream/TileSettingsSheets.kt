package com.antigravity.screensaver.ui.dream

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.model.DisplaySettings
import com.antigravity.screensaver.model.NightColor
import com.antigravity.screensaver.ui.theme.AlarmAccent
import com.antigravity.screensaver.ui.theme.AlarmBorderGlow
import com.antigravity.screensaver.ui.theme.BatteryAccent
import com.antigravity.screensaver.ui.theme.BatteryBorderGlow
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.OledBlack
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.SleepIdleAccent
import com.antigravity.screensaver.ui.theme.SmartHomeGoogleAccent
import com.antigravity.screensaver.ui.theme.SmartHomeGoogleBorderGlow
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.SurfaceDark
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted
import com.antigravity.screensaver.ui.theme.WeatherAccent
import com.antigravity.screensaver.ui.theme.WeatherBorderGlow
import kotlin.math.roundToInt

enum class ActiveTileSheet {
    NONE,
    WEATHER,
    BATTERY,
    SMART_HOME,
    ALARM,
    CLOCK_AND_TILES
}

@Composable
fun TileSettingsBottomSheet(
    activeSheet: ActiveTileSheet,
    settings: DisplaySettings,
    onUpdateSettings: (DisplaySettings) -> Unit,
    onDismiss: () -> Unit,
    onOpenFullSettings: () -> Unit
) {
    AnimatedVisibility(
        visible = activeSheet != ActiveTileSheet.NONE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val borderColor = when (activeSheet) {
            ActiveTileSheet.WEATHER -> WeatherBorderGlow
            ActiveTileSheet.BATTERY -> BatteryBorderGlow
            ActiveTileSheet.SMART_HOME -> SmartHomeGoogleBorderGlow
            ActiveTileSheet.ALARM -> AlarmBorderGlow
            ActiveTileSheet.CLOCK_AND_TILES -> PixelBlue.copy(alpha = 0.55f)
            ActiveTileSheet.NONE -> Color.Transparent
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // block clicks from bubbling to scrim
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (activeSheet) {
                        ActiveTileSheet.WEATHER -> WeatherQuickSheetContent(
                            settings = settings,
                            onUpdateSettings = onUpdateSettings,
                            onDismiss = onDismiss
                        )
                        ActiveTileSheet.BATTERY -> BatteryQuickSheetContent(
                            settings = settings,
                            onUpdateSettings = onUpdateSettings,
                            onDismiss = onDismiss
                        )
                        ActiveTileSheet.SMART_HOME -> SmartHomeQuickSheetContent(
                            settings = settings,
                            onUpdateSettings = onUpdateSettings,
                            onDismiss = onDismiss
                        )
                        ActiveTileSheet.ALARM -> AlarmQuickSheetContent(
                            settings = settings,
                            onUpdateSettings = onUpdateSettings,
                            onDismiss = onDismiss
                        )
                        ActiveTileSheet.CLOCK_AND_TILES -> ClockAndTilesQuickSheetContent(
                            settings = settings,
                            onUpdateSettings = onUpdateSettings,
                            onDismiss = onDismiss,
                            onOpenFullSettings = onOpenFullSettings
                        )
                        ActiveTileSheet.NONE -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryWhite,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = TextSecondaryMuted
            )
        }
    }
}

@Composable
private fun HideTileButton(
    tileName: String,
    onHide: () -> Unit
) {
    Surface(
        onClick = onHide,
        shape = RoundedCornerShape(12.dp),
        color = Color(0x22FF5252),
        border = BorderStroke(1.dp, Color(0x55FF5252)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = Color(0xFFFF8A80),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hide $tileName Tile (Hold Clock to restore)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF8A80)
            )
        }
    }
}

@Composable
private fun QuickToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = TextSecondaryMuted
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OledBlack,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = TextSecondaryMuted,
                    uncheckedTrackColor = Color(0x33888888)
                )
            )
        }
    }
}

@Composable
private fun WeatherQuickSheetContent(
    settings: DisplaySettings,
    onUpdateSettings: (DisplaySettings) -> Unit,
    onDismiss: () -> Unit
) {
    var cityInput by remember(settings.customCity) { mutableStateOf(settings.customCity) }

    SheetHeader(
        icon = Icons.Rounded.Cloud,
        title = "Weather Tile Settings",
        accentColor = WeatherAccent,
        onDismiss = onDismiss
    )

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "LOCATION",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = WeatherAccent,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = cityInput,
            onValueChange = {
                cityInput = it
                onUpdateSettings(settings.copy(customCity = it))
            },
            label = { Text("Custom City (blank = auto local)") },
            placeholder = { Text("e.g. Huntsville, Chicago", color = TextSecondaryMuted) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimaryWhite,
                unfocusedTextColor = TextPrimaryWhite,
                focusedBorderColor = WeatherAccent,
                unfocusedBorderColor = Color(0x44FFFFFF)
            )
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "TEMPERATURE UNIT",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = WeatherAccent,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isF = settings.useFahrenheit
        Surface(
            onClick = { onUpdateSettings(settings.copy(useFahrenheit = true)) },
            shape = RoundedCornerShape(12.dp),
            color = if (isF) WeatherAccent.copy(alpha = 0.22f) else SurfaceCard,
            border = if (isF) BorderStroke(1.5.dp, WeatherAccent) else null,
            modifier = Modifier.weight(1f).height(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Fahrenheit (°F)",
                    color = if (isF) WeatherAccent else TextPrimaryWhite,
                    fontSize = 13.sp,
                    fontWeight = if (isF) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        Surface(
            onClick = { onUpdateSettings(settings.copy(useFahrenheit = false)) },
            shape = RoundedCornerShape(12.dp),
            color = if (!isF) WeatherAccent.copy(alpha = 0.22f) else SurfaceCard,
            border = if (!isF) BorderStroke(1.5.dp, WeatherAccent) else null,
            modifier = Modifier.weight(1f).height(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Celsius (°C)",
                    color = if (!isF) WeatherAccent else TextPrimaryWhite,
                    fontSize = 13.sp,
                    fontWeight = if (!isF) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    QuickToggleRow(
        title = "Show Tomorrow's Forecast",
        subtitle = "Display tomorrow's high/low pill at the bottom of the weather card",
        checked = settings.showTomorrowWeather,
        accentColor = WeatherAccent,
        onCheckedChange = { onUpdateSettings(settings.copy(showTomorrowWeather = it)) }
    )

    Spacer(modifier = Modifier.height(16.dp))

    HideTileButton(
        tileName = "Weather",
        onHide = {
            onUpdateSettings(settings.copy(showWeather = false))
            onDismiss()
        }
    )

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun BatteryQuickSheetContent(
    settings: DisplaySettings,
    onUpdateSettings: (DisplaySettings) -> Unit,
    onDismiss: () -> Unit
) {
    SheetHeader(
        icon = Icons.Rounded.BatteryChargingFull,
        title = "Battery Tile Settings",
        accentColor = BatteryAccent,
        onDismiss = onDismiss
    )

    Spacer(modifier = Modifier.height(14.dp))

    QuickToggleRow(
        title = "Show Charging Wattage",
        subtitle = "Display live charging power in watts (e.g. 1.4W or 15W) next to charging status",
        checked = settings.showChargingWatts,
        accentColor = BatteryAccent,
        onCheckedChange = { onUpdateSettings(settings.copy(showChargingWatts = it)) }
    )

    Spacer(modifier = Modifier.height(16.dp))

    HideTileButton(
        tileName = "Battery",
        onHide = {
            onUpdateSettings(settings.copy(showBattery = false))
            onDismiss()
        }
    )

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SmartHomeQuickSheetContent(
    settings: DisplaySettings,
    onUpdateSettings: (DisplaySettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SheetHeader(
        icon = Icons.Rounded.Home,
        title = "Smart Home Tile Settings",
        accentColor = SmartHomeGoogleAccent,
        onDismiss = onDismiss
    )

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "SMART HOME ECOSYSTEM",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = SmartHomeGoogleAccent,
        letterSpacing = 1.sp
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
            val selected = settings.smartHomeProvider.equals(key, ignoreCase = true)
            val activeColor = if (key == "alexa") AlexaCyan else SmartHomeGoogleAccent
            Surface(
                onClick = { onUpdateSettings(settings.copy(smartHomeProvider = key)) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) activeColor.copy(alpha = 0.22f) else SurfaceCard,
                border = if (selected) BorderStroke(1.5.dp, activeColor) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (key == "alexa") Icons.Rounded.GraphicEq else Icons.Rounded.Home,
                        contentDescription = null,
                        tint = if (selected) activeColor else TextPrimaryWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = if (selected) activeColor else TextPrimaryWhite,
                        fontSize = 13.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    val isAlexa = settings.smartHomeProvider.equals("alexa", ignoreCase = true)
    Surface(
        onClick = {
            onDismiss()
            if (isAlexa) openAlexa(context) else openGoogleHome(context)
        },
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isAlexa) "Launch Amazon Alexa Now" else "Launch Google Home Now",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = SmartHomeGoogleAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    HideTileButton(
        tileName = "Smart Home",
        onHide = {
            onUpdateSettings(settings.copy(showGoogleHomePill = false))
            onDismiss()
        }
    )

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun AlarmQuickSheetContent(
    settings: DisplaySettings,
    onUpdateSettings: (DisplaySettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SheetHeader(
        icon = Icons.Rounded.Alarm,
        title = "Alarm Tile Settings",
        accentColor = AlarmAccent,
        onDismiss = onDismiss
    )

    Spacer(modifier = Modifier.height(14.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = {
                onDismiss()
                try {
                    ScreensaverActivity.launch(context, ScreensaverActivity.TARGET_CLOCK)
                } catch (_: Exception) {}
            },
            shape = RoundedCornerShape(14.dp),
            color = AlarmAccent.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, AlarmAccent),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AlarmAdd,
                    contentDescription = null,
                    tint = AlarmAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open Alarms",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlarmAccent
                )
            }
        }

        Surface(
            onClick = {
                onDismiss()
                dismissNextAlarm(context)
            },
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AlarmOff,
                    contentDescription = null,
                    tint = TextPrimaryWhite,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Dismiss Next",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryWhite
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    HideTileButton(
        tileName = "Alarm",
        onHide = {
            onUpdateSettings(settings.copy(showAlarm = false))
            onDismiss()
        }
    )

    Spacer(modifier = Modifier.height(8.dp))
}

private fun dismissNextAlarm(context: Context) {
    try {
        val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(dismissIntent)
    } catch (e: Exception) {
        try {
            val showIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(showIntent)
        } catch (_: Exception) {}
    }
}

@Composable
private fun ClockAndTilesQuickSheetContent(
    settings: DisplaySettings,
    onUpdateSettings: (DisplaySettings) -> Unit,
    onDismiss: () -> Unit,
    onOpenFullSettings: () -> Unit
) {
    SheetHeader(
        icon = Icons.Rounded.Schedule,
        title = "Clock & Visible Tiles",
        accentColor = PixelBlue,
        onDismiss = onDismiss
    )

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "CLOCK & DISPLAY",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = PixelBlue,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    QuickToggleRow(
        title = "Show Seconds on Clock",
        subtitle = "Display live seconds counter next to the main time",
        checked = settings.showSeconds,
        accentColor = PixelBlue,
        onCheckedChange = { onUpdateSettings(settings.copy(showSeconds = it)) }
    )

    Spacer(modifier = Modifier.height(8.dp))

    QuickToggleRow(
        title = "Auto Night Clock Mode",
        subtitle = "Automatically switch to dim night clock in dark rooms (or tap background)",
        checked = settings.nightModeEnabled,
        accentColor = NightAmber,
        onCheckedChange = { onUpdateSettings(settings.copy(nightModeEnabled = it)) }
    )

    if (settings.nightModeEnabled) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Night Clock Color",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryWhite
                )
                Spacer(modifier = Modifier.height(10.dp))
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
                                    onUpdateSettings(settings.copy(nightColor = colorOption.id))
                                }
                                .padding(horizontal = 2.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .padding(3.dp)
                                    .background(colorOption.primaryColor, CircleShape)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = if (colorOption == NightColor.WARM_WHITE) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = colorOption.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimaryWhite else TextSecondaryMuted
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "SCREEN ORIENTATION",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = PixelBlue,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    val orientationOptions = listOf(
        "sensor" to "Auto",
        "portrait" to "Portrait",
        "landscape" to "Landscape",
        "system" to "System"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        orientationOptions.forEach { (mode, label) ->
            val selected = settings.orientationMode == mode
            Surface(
                onClick = { onUpdateSettings(settings.copy(orientationMode = mode)) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) PixelBlue.copy(alpha = 0.22f) else SurfaceCard,
                border = if (selected) BorderStroke(1.5.dp, PixelBlue) else null,
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        color = if (selected) PixelBlue else TextPrimaryWhite,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "VISIBLE DASHBOARD TILES",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = PixelBlue,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickToggleRow(
            title = "Alarm Tile",
            subtitle = "Upcoming alarm & quick dismiss card",
            checked = settings.showAlarm,
            accentColor = AlarmAccent,
            onCheckedChange = { onUpdateSettings(settings.copy(showAlarm = it)) }
        )
        QuickToggleRow(
            title = "Weather Tile",
            subtitle = "Current temperature, condition & tomorrow forecast",
            checked = settings.showWeather,
            accentColor = WeatherAccent,
            onCheckedChange = { onUpdateSettings(settings.copy(showWeather = it)) }
        )
        QuickToggleRow(
            title = "Sleep Sounds Tile",
            subtitle = "Brown, Pink, White noise, Rain, Waves & Timer",
            checked = settings.showBrownNoise,
            accentColor = SleepIdleAccent,
            onCheckedChange = { onUpdateSettings(settings.copy(showBrownNoise = it)) }
        )
        QuickToggleRow(
            title = "Battery Tile",
            subtitle = "Charging percentage, wattage & source ring",
            checked = settings.showBattery,
            accentColor = BatteryAccent,
            onCheckedChange = { onUpdateSettings(settings.copy(showBattery = it)) }
        )
        QuickToggleRow(
            title = "Smart Home Tile",
            subtitle = "Quick launcher for Google Home or Amazon Alexa",
            checked = settings.showGoogleHomePill,
            accentColor = SmartHomeGoogleAccent,
            onCheckedChange = { onUpdateSettings(settings.copy(showGoogleHomePill = it)) }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            onDismiss()
            onOpenFullSettings()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = null,
            tint = PixelBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Open Full App Settings",
            color = PixelBlue,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}
