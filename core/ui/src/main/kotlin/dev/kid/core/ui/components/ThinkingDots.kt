package dev.kid.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kid.core.ui.theme.KidColors

@Composable
fun ThinkingDots(
    modifier: Modifier = Modifier,
    color: Color = KidColors.TextTertiary,
    dotSize: Dp = 6.dp,
    spacing: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "dots")

    val alpha1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(color = color.copy(alpha = alpha1), size = dotSize)
        Spacer(modifier = Modifier.width(spacing))
        Dot(color = color.copy(alpha = alpha2), size = dotSize)
        Spacer(modifier = Modifier.width(spacing))
        Dot(color = color.copy(alpha = alpha3), size = dotSize)
    }
}

@Composable
private fun Dot(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        drawCircle(color = color)
    }
}
