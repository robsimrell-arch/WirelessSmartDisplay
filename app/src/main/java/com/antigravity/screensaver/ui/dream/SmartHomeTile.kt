package com.antigravity.screensaver.ui.dream

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.SmartHomeAlexaBorderGlow
import com.antigravity.screensaver.ui.theme.SmartHomeAlexaGlassBottom
import com.antigravity.screensaver.ui.theme.SmartHomeAlexaGlassTop
import com.antigravity.screensaver.ui.theme.SmartHomeGoogleAccent
import com.antigravity.screensaver.ui.theme.SmartHomeGoogleBorderGlow
import com.antigravity.screensaver.ui.theme.SmartHomeGoogleGlassBottom
import com.antigravity.screensaver.ui.theme.SmartHomeGoogleGlassTop
import com.antigravity.screensaver.ui.theme.SmartHomeSubtleText
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite

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

fun triggerSmartHomeAutomation(context: Context, provider: String = "google") {
    try {
        ScreensaverActivity.launch(context, ScreensaverActivity.TARGET_SMART_HOME_AUTOMATION, provider)
    } catch (e: Exception) {
        android.util.Log.e("SmartHomeAutomation", "ScreensaverActivity launch failed: ${e.message}")
    }
}

val AlexaCyan = Color(0xFF00CAFF)

/**
 * Standalone Smart Home launcher tile supporting Google Home or Amazon Alexa,
 * plus an optional bottom pill button to trigger an automation (e.g. "Lights Out").
 *
 * - Portrait mode: Uses a stacked header (Icon badge on left, OpenInNew on right, full-width title below)
 *   so "Google Home" / "Amazon Alexa" never wraps or truncates in narrow portrait columns.
 *   When the automation pill is enabled, "Smart Controls" is replaced by the full-width "Lights Out" pill
 *   so neither title nor button ever truncates.
 * - Landscape mode: Places "Google Home" / "Amazon Alexa" on the top row right next to the icon badge
 *   with the "Lights Out" pill (or "Smart Controls") cleanly below.
 *
 * Tap card/header: launches selected smart home app
 * Tap automation pill: triggers automation / opens Automations tab
 * Long press: opens Smart Home quick settings sheet
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartHomeTile(
    provider: String = "google",
    showAutomation: Boolean = true,
    automationLabel: String = "Lights Out",
    nightMode: Boolean = false,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isAlexa = provider.equals("alexa", ignoreCase = true)

    val topBg = when {
        nightMode -> Color(0x28FFA726)
        isAlexa -> SmartHomeAlexaGlassTop
        else -> SmartHomeGoogleGlassTop
    }
    val bottomBg = when {
        nightMode -> Color(0x14FFA726)
        isAlexa -> SmartHomeAlexaGlassBottom
        else -> SmartHomeGoogleGlassBottom
    }
    val borderColor = when {
        nightMode -> NightAmber.copy(alpha = 0.45f)
        isAlexa -> SmartHomeAlexaBorderGlow
        else -> SmartHomeGoogleBorderGlow
    }
    val primaryColor = if (nightMode) NightAmber else if (isAlexa) AlexaCyan else SmartHomeGoogleAccent
    val secondaryColor = if (nightMode) NightDimAmber else SmartHomeSubtleText
    val textColor = if (nightMode) NightAmber else TextPrimaryWhite

    val title = if (isAlexa) "Amazon Alexa" else "Google Home"
    val icon = if (isAlexa) Icons.Rounded.GraphicEq else Icons.Rounded.Home
    val openDescription = if (isAlexa) "Open Amazon Alexa" else "Open Google Home"
    val displayAutomationLabel = automationLabel.trim().ifEmpty { "Lights Out" }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(listOf(topBg, bottomBg)),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(borderColor, borderColor.copy(alpha = 0.25f))),
                shape = shape
            )
            .combinedClickable(
                onClick = {
                    if (isAlexa) {
                        openAlexa(context)
                    } else {
                        openGoogleHome(context)
                    }
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = if (isLandscape) 8.dp else 10.dp)
    ) {
        if (isLandscape) {
            // Landscape: Title next to icon badge on top row; Automation pill (or "Smart Controls") below
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                if (nightMode) Color(0x33FFA726) else primaryColor.copy(alpha = 0.20f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = primaryColor.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = openDescription,
                        tint = primaryColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                if (showAutomation) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(primaryColor.copy(alpha = 0.18f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AutomationPillButton(
                        label = displayAutomationLabel,
                        primaryColor = primaryColor,
                        textColor = textColor,
                        nightMode = nightMode,
                        onClick = { triggerSmartHomeAutomation(context, provider) },
                        onLongClick = onLongClick
                    )
                } else {
                    Text(
                        text = "Smart Controls",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // Portrait: Stacked layout so "Google Home" / "Amazon Alexa" and "Lights Out" each get full column width
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (nightMode) Color(0x33FFA726) else primaryColor.copy(alpha = 0.20f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = primaryColor.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = openDescription,
                        tint = primaryColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(5.dp))

                if (showAutomation) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(primaryColor.copy(alpha = 0.18f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AutomationPillButton(
                        label = displayAutomationLabel,
                        primaryColor = primaryColor,
                        textColor = textColor,
                        nightMode = nightMode,
                        onClick = { triggerSmartHomeAutomation(context, provider) },
                        onLongClick = onLongClick
                    )
                } else {
                    Text(
                        text = "Smart Controls",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = secondaryColor,
                        softWrap = true,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutomationPillButton(
    label: String,
    primaryColor: Color,
    textColor: Color,
    nightMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(7.dp)
    val pillBg = if (nightMode) Color(0x33FFA726) else primaryColor.copy(alpha = 0.22f)
    val pillBorder = if (nightMode) NightAmber.copy(alpha = 0.55f) else primaryColor.copy(alpha = 0.50f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(pillShape)
            .background(pillBg, pillShape)
            .border(width = 1.dp, color = pillBorder, shape = pillShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(11.5.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
