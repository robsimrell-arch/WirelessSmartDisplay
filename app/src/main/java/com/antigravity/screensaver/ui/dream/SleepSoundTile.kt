package com.antigravity.screensaver.ui.dream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.screensaver.audio.AudioOutputState
import com.antigravity.screensaver.model.SLEEP_TIMER_OPTIONS_MINUTES
import com.antigravity.screensaver.model.SleepSound
import com.antigravity.screensaver.ui.theme.NightAmber
import com.antigravity.screensaver.ui.theme.NightDimAmber
import com.antigravity.screensaver.ui.theme.OledBlack
import com.antigravity.screensaver.ui.theme.PixelAmber
import com.antigravity.screensaver.ui.theme.PixelBlue
import com.antigravity.screensaver.ui.theme.SleepIdleAccent
import com.antigravity.screensaver.ui.theme.SleepIdleBorderGlow
import com.antigravity.screensaver.ui.theme.SleepIdleGlassBottom
import com.antigravity.screensaver.ui.theme.SleepIdleGlassTop
import com.antigravity.screensaver.ui.theme.SleepPlayingAccent
import com.antigravity.screensaver.ui.theme.SleepPlayingBorderGlow
import com.antigravity.screensaver.ui.theme.SleepPlayingGlassBottom
import com.antigravity.screensaver.ui.theme.SleepPlayingGlassTop
import com.antigravity.screensaver.ui.theme.SleepSubtleText
import com.antigravity.screensaver.ui.theme.SurfaceCard
import com.antigravity.screensaver.ui.theme.SurfaceDark
import com.antigravity.screensaver.ui.theme.TextPrimaryWhite
import com.antigravity.screensaver.ui.theme.TextSecondaryMuted

/**
 * Smart display widget tile for sleep sounds.
 *
 * Stacked layout ensures that sound title ("Brown Noise") and status ("Sleep Sound" / timer)
 * have the full card width in portrait mode and never compete horizontally with the Play/Stop button.
 *
 * Tap: toggles playback
 * Long press: opens sound, timer, and audio output picker sheet
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SleepSoundTile(
    sound: SleepSound,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    timerMinutes: Int = 0,
    remainingTimerMs: Long? = null,
    audioOutputState: AudioOutputState = AudioOutputState(),
    nightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val topColor by animateColorAsState(
        targetValue = when {
            nightMode && isPlaying -> Color(0x44FFA726)
            nightMode -> Color(0x22FFA726)
            isPlaying -> SleepPlayingGlassTop
            else -> SleepIdleGlassTop
        },
        label = "sleepTopBg"
    )

    val bottomColor by animateColorAsState(
        targetValue = when {
            nightMode && isPlaying -> Color(0x28FFA726)
            nightMode -> Color(0x14FFA726)
            isPlaying -> SleepPlayingGlassBottom
            else -> SleepIdleGlassBottom
        },
        label = "sleepBottomBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            nightMode -> NightAmber.copy(alpha = 0.45f)
            isPlaying -> SleepPlayingBorderGlow
            else -> SleepIdleBorderGlow
        },
        label = "sleepBorder"
    )

    val primaryColor = when {
        nightMode -> NightAmber
        isPlaying -> SleepPlayingAccent
        else -> SleepIdleAccent
    }

    val textColor = when {
        nightMode -> NightAmber
        else -> TextPrimaryWhite
    }

    val secondaryColor = when {
        nightMode -> NightDimAmber
        isPlaying -> Color(0xFFFDE68A)
        else -> SleepSubtleText
    }

    val buttonBg = when {
        isPlaying -> primaryColor
        nightMode -> Color(0x33FFA726)
        else -> primaryColor.copy(alpha = 0.24f)
    }

    val buttonIconTint = when {
        isPlaying -> OledBlack
        nightMode -> NightAmber
        else -> TextPrimaryWhite
    }

    val chipBg = when {
        nightMode -> Color(0x33FFA726)
        audioOutputState.isBluetooth -> Color(0x3838BDF8)
        isPlaying -> Color(0x2EFFCA28)
        else -> Color(0x24C084FC)
    }

    val chipTint = when {
        nightMode -> NightAmber
        audioOutputState.isBluetooth -> PixelBlue
        isPlaying -> SleepPlayingAccent
        else -> SleepIdleAccent
    }

    val chipTextColor = when {
        nightMode -> NightAmber
        audioOutputState.isBluetooth -> Color(0xFFBAE6FD)
        else -> TextPrimaryWhite.copy(alpha = 0.95f)
    }

    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor)
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(borderColor, borderColor.copy(alpha = 0.25f))
                ),
                shape = shape
            )
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Row 1: Sound Equalizer Badge on Left + Play/Stop Action Button on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            if (nightMode) Color(0x33FFA726) else primaryColor.copy(alpha = 0.22f),
                            RoundedCornerShape(9.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = primaryColor.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(9.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = sound.label,
                        tint = primaryColor,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Surface(
                    onClick = onToggle,
                    shape = CircleShape,
                    color = buttonBg,
                    border = BorderStroke(
                        1.dp,
                        if (isPlaying) Color.Transparent else primaryColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            tint = buttonIconTint,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Full-width Title & Status (Never squeezed by the icon or Play button!)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = sound.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    softWrap = true,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(2.dp))

                val statusText = when {
                    isPlaying && remainingTimerMs != null && remainingTimerMs > 0L -> {
                        val mins = ((remainingTimerMs + 59999L) / 60000L).coerceAtLeast(1)
                        "Playing • ⏱ ${mins}m"
                    }
                    isPlaying -> "Playing Now"
                    timerMinutes > 0 -> "Sleep Sound • ⏱ ${timerMinutes}m"
                    else -> "Sleep Sound"
                }

                Text(
                    text = statusText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryColor,
                    softWrap = true,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Sleek Audio Output Pill Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = chipBg,
                border = BorderStroke(0.5.dp, chipTint.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (audioOutputState.isBluetooth) Icons.Rounded.BluetoothConnected else Icons.Rounded.Speaker,
                        contentDescription = null,
                        tint = chipTint,
                        modifier = Modifier.size(12.dp)
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    Text(
                        text = audioOutputState.deviceName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = chipTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Bottom picker sheet to select Sleep Sound, Sleep Timer, route Audio / Bluetooth, or hide tile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepSoundPickerSheet(
    visible: Boolean,
    currentSound: SleepSound,
    onSelectSound: (SleepSound) -> Unit,
    currentTimerMinutes: Int,
    onSelectTimerMinutes: (Int) -> Unit,
    audioOutputState: AudioOutputState,
    onOpenAudioOutput: () -> Unit,
    onHideTile: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    nightMode: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val activePrimary = if (nightMode) NightAmber else SleepIdleAccent
        val textColor = if (nightMode) NightAmber else TextPrimaryWhite
        val secondaryColor = if (nightMode) NightDimAmber else TextSecondaryMuted

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = if (nightMode) Color(0xFF140D04) else SurfaceDark,
                border = BorderStroke(
                    1.dp,
                    if (nightMode) NightAmber.copy(alpha = 0.35f) else SleepIdleBorderGlow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // block clicks from bubbling to scrim
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = activePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sleep Sounds & Timer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = secondaryColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION 1: SOUND SELECTION
                    Text(
                        text = "SOUND",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activePrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SleepSound.entries.forEach { sound ->
                            val selected = sound == currentSound
                            Surface(
                                onClick = { onSelectSound(sound) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) activePrimary.copy(alpha = 0.22f) else SurfaceCard,
                                border = if (selected) BorderStroke(1.5.dp, activePrimary) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = activePrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = sound.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) activePrimary else textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SECTION 2: SLEEP TIMER SELECTION
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = activePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SLEEP TIMER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = activePrimary,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fades out gently over the final 60 seconds before stopping.",
                        fontSize = 11.sp,
                        color = secondaryColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SLEEP_TIMER_OPTIONS_MINUTES.forEach { minutes ->
                            val selected = minutes == currentTimerMinutes
                            val label = if (minutes == 0) "Off" else "${minutes}m"
                            Surface(
                                onClick = { onSelectTimerMinutes(minutes) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) activePrimary.copy(alpha = 0.22f) else SurfaceCard,
                                border = if (selected) BorderStroke(1.5.dp, activePrimary) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) activePrimary else textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SECTION 3: AUDIO OUTPUT ROUTING (BLUETOOTH / SPEAKER)
                    Text(
                        text = "AUDIO OUTPUT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activePrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        onClick = onOpenAudioOutput,
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (audioOutputState.isBluetooth) Icons.Rounded.BluetoothConnected else Icons.Rounded.Speaker,
                                contentDescription = null,
                                tint = if (audioOutputState.isBluetooth) PixelBlue else activePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = audioOutputState.deviceName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Text(
                                    text = if (audioOutputState.isBluetooth) "Bluetooth device connected" else "Phone built-in speaker",
                                    fontSize = 11.sp,
                                    color = secondaryColor
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Switch Audio Device",
                                tint = secondaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (onHideTile != null) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Surface(
                            onClick = {
                                onHideTile()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x22FF5252),
                            border = BorderStroke(1.dp, Color(0x55FF5252)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFFFF8A80),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hide Sleep Sounds Tile (Hold Clock to restore)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFF8A80)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for any existing references to BrownNoiseTile.
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
    SleepSoundTile(
        sound = SleepSound.BROWN,
        isPlaying = isPlaying,
        onToggle = onToggle,
        onLongClick = onOpenAudioOutput,
        timerMinutes = 0,
        audioOutputState = audioOutputState,
        nightMode = nightMode,
        modifier = modifier
    )
}

