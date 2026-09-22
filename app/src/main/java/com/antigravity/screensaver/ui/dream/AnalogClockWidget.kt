package com.antigravity.screensaver.ui.dream

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.antigravity.screensaver.model.NightColor
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun AnalogClockWidget(
    nightColor: NightColor = NightColor.RED,
    modifier: Modifier = Modifier
) {
    var hoursFloat by remember { mutableFloatStateOf(0f) }
    var minutesFloat by remember { mutableFloatStateOf(0f) }
    var secondsFloat by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                val cal = Calendar.getInstance()
                val millis = cal.get(Calendar.MILLISECOND)
                val sec = cal.get(Calendar.SECOND) + millis / 1000f
                val min = cal.get(Calendar.MINUTE) + sec / 60f
                val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f

                secondsFloat = sec
                minutesFloat = min
                hoursFloat = hr
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .aspectRatio(1f)
                .padding(12.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f

            // 1. Draw 12 Hour Ticks
            for (i in 0 until 12) {
                val angleRad = (i * 30.0 * PI / 180.0) - (PI / 2.0)
                val isCardinal = (i % 3 == 0) // 12, 3, 6, 9

                val tickLength = if (isCardinal) radius * 0.15f else radius * 0.08f
                val tickWidth = if (isCardinal) 4.5.dp.toPx() else 2.5.dp.toPx()
                val tickColor = if (isCardinal) nightColor.primaryColor else nightColor.dimColor

                val innerRadius = radius - tickLength
                val startX = (center.x + innerRadius * cos(angleRad)).toFloat()
                val startY = (center.y + innerRadius * sin(angleRad)).toFloat()
                val endX = (center.x + (radius - 2.dp.toPx()) * cos(angleRad)).toFloat()
                val endY = (center.y + (radius - 2.dp.toPx()) * sin(angleRad)).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw Hour Hand
            val hourAngleRad = (hoursFloat * 30.0 * PI / 180.0) - (PI / 2.0)
            val hourLength = radius * 0.52f
            val hourEndX = (center.x + hourLength * cos(hourAngleRad)).toFloat()
            val hourEndY = (center.y + hourLength * sin(hourAngleRad)).toFloat()
            val hourTailX = (center.x - (radius * 0.1f) * cos(hourAngleRad)).toFloat()
            val hourTailY = (center.y - (radius * 0.1f) * sin(hourAngleRad)).toFloat()

            drawLine(
                color = nightColor.primaryColor,
                start = Offset(hourTailX, hourTailY),
                end = Offset(hourEndX, hourEndY),
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 3. Draw Minute Hand
            val minAngleRad = (minutesFloat * 6.0 * PI / 180.0) - (PI / 2.0)
            val minLength = radius * 0.78f
            val minEndX = (center.x + minLength * cos(minAngleRad)).toFloat()
            val minEndY = (center.y + minLength * sin(minAngleRad)).toFloat()
            val minTailX = (center.x - (radius * 0.12f) * cos(minAngleRad)).toFloat()
            val minTailY = (center.y - (radius * 0.12f) * sin(minAngleRad)).toFloat()

            drawLine(
                color = nightColor.primaryColor,
                start = Offset(minTailX, minTailY),
                end = Offset(minEndX, minEndY),
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 4. Draw Smooth Sweeping Seconds Hand
            val secAngleRad = (secondsFloat * 6.0 * PI / 180.0) - (PI / 2.0)
            val secLength = radius * 0.88f
            val secEndX = (center.x + secLength * cos(secAngleRad)).toFloat()
            val secEndY = (center.y + secLength * sin(secAngleRad)).toFloat()
            val secTailX = (center.x - (radius * 0.18f) * cos(secAngleRad)).toFloat()
            val secTailY = (center.y - (radius * 0.18f) * sin(secAngleRad)).toFloat()

            drawLine(
                color = nightColor.primaryColor,
                start = Offset(secTailX, secTailY),
                end = Offset(secEndX, secEndY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 5. Center Pivot Dot
            drawCircle(
                color = nightColor.primaryColor,
                radius = 6.dp.toPx(),
                center = center
            )
        }
    }
}
