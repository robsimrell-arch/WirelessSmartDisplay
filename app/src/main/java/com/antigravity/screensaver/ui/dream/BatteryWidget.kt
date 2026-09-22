package com.antigravity.screensaver.ui.dream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChargingStation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.model.BatteryInfo
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.PixelGreen
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted

@Composable
fun BatteryWidget(
    batteryInfo: BatteryInfo,
    nightMode: Boolean,
    showWatts: Boolean = true,
    modifier: Modifier = Modifier
) {
    val containerBg = if (nightMode) Color(0x22FFA726) else SurfaceCard
    val primaryColor = if (nightMode) NightAmber else PixelGreen
    val secondaryColor = if (nightMode) NightDimAmber else TextSecondaryMuted
    val textColor = if (nightMode) NightAmber else TextPrimaryWhite

    val displayStatus = if (showWatts || batteryInfo.chargingWatts == null) {
        batteryInfo.statusText
    } else {
        batteryInfo.statusText.replace(Regex("""^\d+(\.\d+)?W\s+"""), "")
    }

    Row(
        modifier = modifier
            .background(containerBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp)
        ) {
            CircularProgressIndicator(
                progress = { (batteryInfo.level / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.size(38.dp),
                color = primaryColor,
                trackColor = Color(0x33888888),
                strokeWidth = 3.5.dp
            )

            Icon(
                imageVector = if (batteryInfo.isWireless) Icons.Rounded.ChargingStation else Icons.Rounded.Bolt,
                contentDescription = "Charging icon",
                tint = primaryColor,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${batteryInfo.level}%",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayStatus,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = secondaryColor,
                softWrap = true
            )
        }
    }
}
