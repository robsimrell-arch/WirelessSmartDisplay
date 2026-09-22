package com.antigravity.screensaver.ui.dream

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.OledBlack
import com.antigravity.screensaver.ui.theme.PixelAmber
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted

/**
 * Smart display widget tile for controlling soothing Brown Noise sleep audio.
 * Styled as a full-width card matching BatteryWidget, AlarmWidget, and GoogleHomeTile.
 */
@Composable
fun BrownNoiseTile(
    isPlaying: Boolean,
    onToggle: () -> Unit,
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

    val secondaryColor = when {
        nightMode -> NightDimAmber
        isPlaying -> PixelAmber.copy(alpha = 0.85f)
        else -> TextSecondaryMuted
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

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = animatedBg,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Brown Noise Icon",
                tint = primaryColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Brown Noise",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    softWrap = true
                )
                Text(
                    text = if (isPlaying) "Playing" else "Sleep Sound",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = secondaryColor,
                    softWrap = true
                )
            }

            Surface(
                shape = CircleShape,
                color = buttonBg,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Stop Brown Noise" else "Play Brown Noise",
                        tint = buttonIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
