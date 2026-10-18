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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.model.WeatherInfo
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.WeatherAccent
import com.antigravity.screensaver.ui.theme.WeatherBorderGlow
import com.antigravity.screensaver.ui.theme.WeatherGlassBottom
import com.antigravity.screensaver.ui.theme.WeatherGlassTop
import com.antigravity.screensaver.ui.theme.WeatherSubtleText
import com.antigravity.screensaver.ui.theme.WeatherSunGold
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherWidget(
    weatherInfo: WeatherInfo,
    useFahrenheit: Boolean,
    nightMode: Boolean,
    showTomorrow: Boolean = true,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayWeather = weatherInfo.forUnit(useFahrenheit)
    val topBg = if (nightMode) Color(0x28FFA726) else WeatherGlassTop
    val bottomBg = if (nightMode) Color(0x14FFA726) else WeatherGlassBottom
    val borderColor = if (nightMode) NightAmber.copy(alpha = 0.45f) else WeatherBorderGlow
    val primaryColor = if (nightMode) NightAmber else WeatherAccent
    val iconTint = if (nightMode) {
        NightAmber
    } else if (displayWeather.weatherCode == 0) {
        WeatherSunGold
    } else {
        WeatherAccent
    }
    val secondaryColor = if (nightMode) NightDimAmber else WeatherSubtleText
    val textColor = if (nightMode) NightAmber else TextPrimaryWhite

    val currentWeatherIcon = getWeatherIcon(displayWeather.weatherCode)
    val tomorrowWeatherIcon = getWeatherIcon(displayWeather.tomorrowWeatherCode)
    val unitSymbol = if (useFahrenheit) "°F" else "°C"
    val shape = RoundedCornerShape(18.dp)

    Column(
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
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 1. City Name placed at the very top, above temperature
            Text(
                text = displayWeather.cityName,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryColor,
                softWrap = true,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Weather Icon badge + bold Temperature + stacked High / Low readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            if (nightMode) Color(0x33FFA726) else iconTint.copy(alpha = 0.20f),
                            RoundedCornerShape(9.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = iconTint.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(9.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentWeatherIcon,
                        contentDescription = displayWeather.conditionDescription,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${displayWeather.temperature.roundToInt()}$unitSymbol",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    maxLines = 1
                )

                if (displayWeather.hasTodayHiLo) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "HI ${displayWeather.todayTempMax.roundToInt()}°",
                            fontSize = 10.sp,
                            lineHeight = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (nightMode) NightAmber else WeatherSunGold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "LO ${displayWeather.todayTempMin.roundToInt()}°",
                            fontSize = 10.sp,
                            lineHeight = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Condition name placed below temperature
            Text(
                text = displayWeather.conditionDescription,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.92f),
                softWrap = true,
                maxLines = 2
            )
        }

        // 4. Tomorrow's Forecast Strip at the bottom
        if (showTomorrow && displayWeather.hasTomorrowForecast) {
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (nightMode) Color(0x22FFA726) else WeatherAccent.copy(alpha = 0.16f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = primaryColor.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = tomorrowWeatherIcon,
                            contentDescription = null,
                            tint = if (nightMode) NightAmber else WeatherSunGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tomorrow",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }

                    Text(
                        text = "${displayWeather.tomorrowTempMax.roundToInt()}° / ${displayWeather.tomorrowTempMin.roundToInt()}°",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

private fun getWeatherIcon(code: Int): ImageVector = when {
    code == 0 -> Icons.Rounded.WbSunny
    code in 1..3 -> Icons.Rounded.Cloud
    code in 51..65 -> Icons.Rounded.WaterDrop
    code in 71..77 -> Icons.Rounded.AcUnit
    code >= 95 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

