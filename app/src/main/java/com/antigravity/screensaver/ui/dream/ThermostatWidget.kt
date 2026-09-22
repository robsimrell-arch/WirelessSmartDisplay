package com.antigravity.screensaver.ui.dream

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.dreams.DreamService
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.model.ThermostatInfo
import com.antigravity.screensaver.model.ThermostatMode
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted

fun openGoogleHome(context: Context) {
    try {
        ScreensaverActivity.launch(context, ScreensaverActivity.TARGET_SMART_HOME, "google")
    } catch (e: Exception) {
        android.util.Log.e("GoogleHomeLauncher", "ScreensaverActivity launch failed: ${e.message}")
    }
}

fun openAlexa(context: Context) {
    try {
        ScreensaverActivity.launch(context, ScreensaverActivity.TARGET_SMART_HOME, "alexa")
    } catch (e: Exception) {
        android.util.Log.e("AlexaLauncher", "ScreensaverActivity launch failed: ${e.message}")
    }
}

val AlexaCyan = Color(0xFF00CAFF)

/**
 * Standalone Smart Home launcher tile supporting Google Home or Amazon Alexa.
 * Styled as a full-width card matching BatteryWidget and AlarmWidget.
 */
@Composable
fun SmartHomeTile(
    provider: String = "google",
    nightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAlexa = provider.equals("alexa", ignoreCase = true)

    val containerBg = if (nightMode) Color(0x22FFA726) else SurfaceCard
    val primaryColor = if (nightMode) NightAmber else if (isAlexa) AlexaCyan else PixelBlue
    val secondaryColor = if (nightMode) NightDimAmber else TextSecondaryMuted
    val textColor = if (nightMode) NightAmber else TextPrimaryWhite

    val title = if (isAlexa) "Amazon Alexa" else "Google Home"
    val icon = if (isAlexa) Icons.Rounded.GraphicEq else Icons.Rounded.Home
    val openDescription = if (isAlexa) "Open Amazon Alexa" else "Open Google Home"

    Surface(
        onClick = {
            if (isAlexa) {
                openAlexa(context)
            } else {
                openGoogleHome(context)
            }
        },
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    softWrap = true
                )
                Text(
                    text = "Smart Controls",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = secondaryColor,
                    softWrap = true
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = openDescription,
                tint = secondaryColor,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Backward compatibility alias for GoogleHomeTile.
 */
@Composable
fun GoogleHomeTile(
    nightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    SmartHomeTile(
        provider = "google",
        nightMode = nightMode,
        modifier = modifier
    )
}

/**
 * Backward compatibility alias for GoogleHomePill.
 */
@Composable
fun GoogleHomePill(
    nightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    GoogleHomeTile(
        nightMode = nightMode,
        modifier = modifier
    )
}

// Backward compatibility alias for existing references
@Composable
fun ClimateControlWidget(
    zone1Name: String = "Downstairs",
    zone2Name: String = "Upstairs",
    nightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    GoogleHomeTile(
        nightMode = nightMode,
        modifier = modifier
    )
}

// Backward compatibility alias for existing references
@Composable
fun ThermostatWidget(
    thermostatInfo: ThermostatInfo = ThermostatInfo(),
    useFahrenheit: Boolean = true,
    nightMode: Boolean = false,
    onTargetTempChange: (Float) -> Unit = {},
    onModeChange: (ThermostatMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    GoogleHomeTile(
        nightMode = nightMode,
        modifier = modifier
    )
}

