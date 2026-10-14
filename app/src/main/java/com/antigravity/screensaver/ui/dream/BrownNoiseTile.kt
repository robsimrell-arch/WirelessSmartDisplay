package com.antigravity.screensaver.ui.dream

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.audio.AudioOutputState
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.OledBlack
import com.antigravity.screensaver.ui.theme.PixelAmber
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted

/**
 * Smart display widget tile for controlling soothing Brown Noise sleep audio
 * with real-time Bluetooth speaker routing and clear status indicator.
 *
 * Uses a clean 2-row layout:
 * - Top row: Sound icon badge, "Brown Noise" title, and Play/Stop button.
 * - Bottom row: Full-width audio output pill displaying speaker name and status.
 */
@Composable
fun BrownNoiseTile(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    audioOutputState: AudioOutputState = AudioOutputState(),
    onOpenAudioOutput: () -> Unit = {},
    nightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = when {
            nightMode && isPlaying -> Color(0x44FFA726)
            nightMode -> Color(0x22FFA726)
            isPlaying -> Color(0x33FDD663)
            else -> SurfaceCard
        },
        label = "tileBg"
    )

    val primaryColor = when {
        nightMode -> NightAmber
        isPlaying -> PixelAmber
        else -> PixelBlue
    }

    val textColor = when {
        nightMode -> NightAmber
        else -> TextPrimaryWhite
    }

    val buttonBg = when {
        isPlaying -> primaryColor
        nightMode -> Color(0x33FFA726)
        else -> Color(0x22FFFFFF)
    }

    val buttonIconTint = when {
        isPlaying -> OledBlack
        nightMode -> NightAmber
        else -> TextPrimaryWhite
    }

    val chipBg = when {
        nightMode -> Color(0x33FFA726)
        audioOutputState.isBluetooth -> Color(0x2E4285F4)
        isPlaying -> Color(0x22FFA726)
        else -> Color(0x18FFFFFF)
    }

    val chipTint = when {
        nightMode -> NightAmber
        audioOutputState.isBluetooth -> PixelBlue
        isPlaying -> PixelAmber
        else -> TextSecondaryMuted
    }

    val chipTextColor = when {
        nightMode -> NightAmber
        audioOutputState.isBluetooth -> PixelBlue
        else -> TextPrimaryWhite.copy(alpha = 0.9f)
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = animatedBg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Row 1: Sound icon badge + Title + Play/Stop action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (nightMode) Color(0x33FFA726) else primaryColor.copy(alpha = 0.16f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Brown Noise",
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Brown Noise",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    onClick = onToggle,
                    shape = CircleShape,
                    color = buttonBg,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Stop Brown Noise" else "Play Brown Noise",
                            tint = buttonIconTint,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Row 2: Full-width audio output pill (taps to open Bluetooth/output switcher)
            Surface(
                onClick = onOpenAudioOutput,
                shape = RoundedCornerShape(10.dp),
                color = chipBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (audioOutputState.isBluetooth) Icons.Rounded.BluetoothConnected else Icons.Rounded.Speaker,
                        contentDescription = "Audio Output Device",
                        tint = chipTint,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val displayText = if (isPlaying) {
                        "Playing • ${audioOutputState.deviceName}"
                    } else {
                        audioOutputState.deviceName
                    }

                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = chipTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Switch Audio Device",
                        tint = chipTint.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
