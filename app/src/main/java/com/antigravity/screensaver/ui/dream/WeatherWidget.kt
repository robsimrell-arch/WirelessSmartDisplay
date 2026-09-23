package com.antigravity.screensaver.ui.dream

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.model.WeatherInfo
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted
import kotlin.math.roundToInt

@Composable
fun WeatherWidget(
    weatherInfo: WeatherInfo,
    useFahrenheit: Boolean,
    nightMode: Boolean,
    showTomorrow: Boolean = true,
    modifier: Modifier = Modifier
) {
    val displayWeather = weatherInfo.forUnit(useFahrenheit)
    val containerBg = if (nightMode) Color(0x22FFA726) else SurfaceCard
    val primaryColor = if (nightMode) NightAmber else PixelBlue
    val secondaryColor = if (nightMode) NightDimAmber else TextSecondaryMuted
    val textColor = if (nightMode) NightAmber else TextPrimaryWhite

    val currentWeatherIcon = getWeatherIcon(displayWeather.weatherCode)
    val tomorrowWeatherIcon = getWeatherIcon(displayWeather.tomorrowWeatherCode)
    val unitSymbol = if (useFahrenheit) "°F" else "°C"

    Column(
        modifier = modifier
            .background(containerBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 1. City Name placed at the very top, above temperature
            Text(
                text = displayWeather.cityName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryColor,
                softWrap = true
            )

            Spacer(modifier = Modifier.height(3.dp))

            // 2. Weather Icon badge + bold Temperature
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            if (nightMode) Color(0x33FFA726) else primaryColor.copy(alpha = 0.14f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentWeatherIcon,
                        contentDescription = displayWeather.conditionDescription,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${displayWeather.temperature.roundToInt()}$unitSymbol",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // 3. Condition name placed below temperature
            Text(
                text = displayWeather.conditionDescription,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                softWrap = true
            )
        }

        // 4. Tomorrow's Forecast Strip at the bottom
        if (showTomorrow && displayWeather.hasTomorrowForecast) {
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (nightMode) Color(0x1AFFA726) else Color(0x0EFFFFFF),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
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
                            tint = primaryColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
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
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryColor
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
