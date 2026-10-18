package com.antigravity.screensaver.ui.dream

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.ui.theme.AlarmAccent
import com.antigravity.screensaver.ui.theme.AlarmBorderGlow
import com.antigravity.screensaver.ui.theme.AlarmGlassBottom
import com.antigravity.screensaver.ui.theme.AlarmGlassTop
import com.antigravity.screensaver.ui.theme.AlarmSubtleText
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmWidget(
    nightMode: Boolean,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var alarmTriggerTime by remember { mutableLongStateOf(0L) }
    var hasAlarm by remember { mutableStateOf(false) }
    var canDismiss by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }
    var alarmTitle by remember { mutableStateOf("No Upcoming Alarm") }
    var alarmCountdown by remember { mutableStateOf("Tap to set an alarm") }

    // Periodically update alarm state and live countdown
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val nextAlarm = alarmManager?.nextAlarmClock
                if (nextAlarm != null) {
                    alarmTriggerTime = nextAlarm.triggerTime
                    hasAlarm = true
                    canDismiss = true
                    val (dayText, countdown) = formatAlarmDetails(nextAlarm.triggerTime)
                    alarmTitle = dayText
                    alarmCountdown = countdown
                } else {
                    hasAlarm = false
                    canDismiss = false
                    alarmTitle = "No Upcoming Alarm"
                    alarmCountdown = "Tap to set an alarm"
                }
            } catch (e: Exception) {
                hasAlarm = false
                canDismiss = false
                alarmTitle = "No Upcoming Alarm"
                alarmCountdown = "Tap to set an alarm"
            }
            delay(15000L) // Refresh countdown every 15 seconds
        }
    }

    fun dismissAlarm() {
        try {
            val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dismissIntent)
            isDismissed = true
        } catch (e: Exception) {
            try {
                val showIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(showIntent)
                isDismissed = true
            } catch (_: Exception) {}
        }
    }

    fun openClockApp() {
        try {
            ScreensaverActivity.launch(context, ScreensaverActivity.TARGET_CLOCK)
        } catch (e: Exception) {
            android.util.Log.e("AlarmWidget", "ScreensaverActivity launch failed: ${e.message}")
        }
    }

    val topBg = if (nightMode) Color(0x28FFA726) else AlarmGlassTop
    val bottomBg = if (nightMode) Color(0x14FFA726) else AlarmGlassBottom
    val borderColor = if (nightMode) NightAmber.copy(alpha = 0.45f) else AlarmBorderGlow
    val primaryColor = if (nightMode) NightAmber else AlarmAccent
    val secondaryColor = if (nightMode) NightDimAmber else AlarmSubtleText
    val textColor = if (nightMode) NightAmber else TextPrimaryWhite
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
                onClick = { openClockApp() },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Alarm icon in vibrant rounded badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (nightMode) Color(0x33FFA726) else primaryColor.copy(alpha = 0.20f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = primaryColor.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = "Next Alarm",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarmTitle,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    softWrap = true
                )
                Text(
                    text = if (hasAlarm && !isDismissed) "Upcoming Alarm • $alarmCountdown" else alarmCountdown,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = secondaryColor,
                    softWrap = true
                )
            }

            if (hasAlarm && canDismiss && !isDismissed) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = { dismissAlarm() },
                    shape = RoundedCornerShape(12.dp),
                    color = if (nightMode) NightAmber.copy(alpha = 0.25f) else primaryColor.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                    contentColor = primaryColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AlarmOff,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(14.dp),
                            tint = primaryColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Dismiss",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            softWrap = false
                        )
                    }
                }
            } else if (hasAlarm && isDismissed) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dismissed",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryColor
                )
            } else if (!hasAlarm) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = { openClockApp() },
                    shape = RoundedCornerShape(12.dp),
                    color = if (nightMode) NightAmber.copy(alpha = 0.2f) else primaryColor.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                    contentColor = primaryColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AlarmAdd,
                            contentDescription = "Set Alarm",
                            modifier = Modifier.size(14.dp),
                            tint = primaryColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Set Alarm",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}


private fun formatAlarmDetails(triggerTime: Long): Pair<String, String> {
    val now = Calendar.getInstance()
    val alarmCal = Calendar.getInstance().apply { timeInMillis = triggerTime }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(alarmCal.time)

    val isToday = now.get(Calendar.YEAR) == alarmCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == alarmCal.get(Calendar.DAY_OF_YEAR)

    val isTomorrow = run {
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        tomorrow.get(Calendar.YEAR) == alarmCal.get(Calendar.YEAR) &&
                tomorrow.get(Calendar.DAY_OF_YEAR) == alarmCal.get(Calendar.DAY_OF_YEAR)
    }

    val dayText = when {
        isToday -> "Today at $formattedTime"
        isTomorrow -> "Tomorrow at $formattedTime"
        else -> SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault()).format(alarmCal.time)
    }

    val diffMillis = triggerTime - System.currentTimeMillis()
    val countdown = if (diffMillis > 0) {
        val totalMinutes = diffMillis / (1000 * 60)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        if (hours > 0) "in ${hours}h ${mins}m" else "in ${mins}m"
    } else {
        "Ringing soon"
    }

    return Pair(dayText, countdown)
}
