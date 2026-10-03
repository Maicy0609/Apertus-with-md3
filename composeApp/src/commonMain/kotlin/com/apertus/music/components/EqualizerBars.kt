package com.apertus.music.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's own loading / now-playing mark: bars rising and falling out of
 * phase. One component, two jobs — it replaces the generic spinner while
 * buffering, and marks the currently playing row in a list.
 */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    barCount: Int = 3,
    barWidth: Dp = 5.dp,
    maxHeight: Dp = 22.dp,
    periodMillis: Int = 520,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barWidth * 0.8f),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val phase by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = periodMillis,
                        delayMillis = index * (periodMillis / 4),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .size(width = barWidth, height = maxHeight * phase)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color)
            )
        }
    }
}
