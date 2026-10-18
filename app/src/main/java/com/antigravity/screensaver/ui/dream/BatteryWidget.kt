package com.antigravity.screensaver.ui.dream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChargingStation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.model.BatteryInfo
import com.antigravity.screensaver.ui.theme.BatteryAccent
import com.antigravity.screensaver.ui.theme.BatteryBorderGlow
import com.antigravity.screensaver.ui.theme.BatteryGlassBottom
import com.antigravity.screensaver.ui.theme.BatteryGlassTop
import com.antigravity.screensaver.ui.theme.BatterySubtleText
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BatteryWidget(
    batteryInfo: BatteryInfo,
    nightMode: Boolean,
    showWatts: Boolean = true,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val topBg = if (nightMode) Color(0x28FFA726) else BatteryGlassTop
    val bottomBg = if (nightMode) Color(0x14FFA726) else BatteryGlassBottom
    val borderColor = if (nightMode) NightAmber.copy(alpha = 0.45f) else BatteryBorderGlow
    val primaryColor = if (nightMode) NightAmber else BatteryAccent
    val secondaryColor = if (nightMode) NightDimAmber else BatterySubtleText
    val textColor = if (nightMode) NightAmber else TextPrimaryWhite

    val displayStatus = if (showWatts || batteryInfo.chargingWatts == null) {
        batteryInfo.statusText
    } else {
        batteryInfo.statusText.replace(Regex("""^\d+(\.\d+)?W\s+"""), "")
    }

    val shape = RoundedCornerShape(18.dp)

    Row(
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
                onClick = onLongClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp)
        ) {
            CircularProgressIndicator(
                progress = { (batteryInfo.level / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.size(40.dp),
                color = primaryColor,
                trackColor = primaryColor.copy(alpha = 0.22f),
                strokeWidth = 3.5.dp
            )

            Icon(
                imageVector = if (batteryInfo.isWireless) Icons.Rounded.ChargingStation else Icons.Rounded.Bolt,
                contentDescription = "Charging icon",
                tint = primaryColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${batteryInfo.level}%",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayStatus,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = secondaryColor,
                softWrap = true
            )
        }
    }
}

