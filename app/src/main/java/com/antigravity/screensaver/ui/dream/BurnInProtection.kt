package com.antigravity.screensaver.ui.dream

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun BurnInProtectedContainer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var rawOffsetX by remember { mutableStateOf(0.dp) }
    var rawOffsetY by remember { mutableStateOf(0.dp) }

    val animatedOffsetX by animateDpAsState(
        targetValue = if (enabled) rawOffsetX else 0.dp,
        animationSpec = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
        label = "BurnInShiftX"
    )

    val animatedOffsetY by animateDpAsState(
        targetValue = if (enabled) rawOffsetY else 0.dp,
        animationSpec = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
        label = "BurnInShiftY"
    )

    LaunchedEffect(enabled) {
        if (!enabled) {
            rawOffsetX = 0.dp
            rawOffsetY = 0.dp
            return@LaunchedEffect
        }

        while (true) {
            // Safe drift every 60 seconds by +/- 8 dp to protect OLED subpixels without edge clipping
            delay(60_000L)
            val shiftX = Random.nextInt(-8, 9).dp
            val shiftY = Random.nextInt(-8, 9).dp
            rawOffsetX = shiftX
            rawOffsetY = shiftY
        }
    }

    Box(
        modifier = modifier.offset(x = animatedOffsetX, y = animatedOffsetY)
    ) {
        content()
    }
}
