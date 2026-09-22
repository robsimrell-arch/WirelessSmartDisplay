package com.antigravity.screensaver.ui.dream

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.model.NightColor
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ClockWidget(
    showSeconds: Boolean,
    nightMode: Boolean,
    nightColor: NightColor = NightColor.RED,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(showSeconds) {
        val interval = if (showSeconds) 1000L else 15000L
        while (true) {
            currentTime = Calendar.getInstance()
            delay(interval)
        }
    }

    val is24Hour = DateFormat.is24HourFormat(context)
    val mainTimePattern = if (is24Hour) "HH:mm" else "h:mm"
    val mainTimeFormat = remember(is24Hour) { SimpleDateFormat(mainTimePattern, Locale.getDefault()) }
    val secondsFormat = remember { SimpleDateFormat(":ss", Locale.getDefault()) }
    val amPmFormat = remember { SimpleDateFormat("a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    val formattedMainTime = mainTimeFormat.format(currentTime.time)
    val formattedSeconds = if (showSeconds) secondsFormat.format(currentTime.time) else ""
    val formattedAmPm = if (!is24Hour) amPmFormat.format(currentTime.time) else ""
    val formattedDate = dateFormat.format(currentTime.time)

    if (nightMode) {
        if (isLandscape) {
            // Thinner font (FontWeight.Light), numbers taller and wider filling the screen
            BoxWithConstraints(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Responsively scale to available screen dimensions
                val calcFontSize = (maxHeight.value * 0.76f).coerceIn(160f, 260f).sp
                val calcLineHeight = (calcFontSize.value * 1.04f).sp
                val amPmSize = (calcFontSize.value * 0.18f).coerceIn(24f, 38f).sp

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formattedMainTime,
                        fontSize = calcFontSize,
                        lineHeight = calcLineHeight,
                        fontWeight = FontWeight.Light,
                        color = nightColor.primaryColor,
                        letterSpacing = (-2).sp,
                        softWrap = false,
                        maxLines = 1
                    )

                    if (formattedAmPm.isNotBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formattedAmPm,
                            fontSize = amPmSize,
                            fontWeight = FontWeight.Normal,
                            color = nightColor.dimColor,
                            modifier = Modifier.alignByBaseline(),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            // Portrait mode: top half of screen
            val timeFontSize = 92.sp
            val timeLineHeight = 96.sp
            val amPmFontSize = 20.sp

            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formattedMainTime,
                    fontSize = timeFontSize,
                    lineHeight = timeLineHeight,
                    fontWeight = FontWeight.Bold,
                    color = nightColor.primaryColor,
                    letterSpacing = (-1).sp,
                    softWrap = false,
                    maxLines = 1
                )

                if (formattedAmPm.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedAmPm,
                        fontSize = amPmFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = nightColor.dimColor,
                        modifier = Modifier.alignByBaseline(),
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }
        return
    }

    val timeColor = TextPrimaryWhite
    val secondaryColor = TextSecondaryMuted
    val accentColor = PixelBlue

    Column(
        modifier = modifier.padding(start = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (showSeconds) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedMainTime,
                    fontSize = 74.sp,
                    lineHeight = 78.sp,
                    fontWeight = FontWeight.Bold,
                    color = timeColor,
                    letterSpacing = 0.sp,
                    softWrap = false,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = formattedSeconds,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = timeColor,
                        softWrap = false,
                        maxLines = 1
                    )

                    if (formattedAmPm.isNotBlank()) {
                        Text(
                            text = formattedAmPm,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formattedMainTime,
                    fontSize = 78.sp,
                    lineHeight = 82.sp,
                    fontWeight = FontWeight.Bold,
                    color = timeColor,
                    letterSpacing = 0.sp,
                    softWrap = false,
                    maxLines = 1
                )

                if (formattedAmPm.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedAmPm,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.alignByBaseline(),
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = formattedDate,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 22.sp,
                letterSpacing = 0.sp
            ),
            color = secondaryColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
