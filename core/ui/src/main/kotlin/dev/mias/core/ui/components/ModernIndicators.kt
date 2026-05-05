package dev.mias.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern animated dots for streaming/loading states.
 *
 * Features:
 * - Three bouncing dots with staggered animation
 * - Customizable color and size
 * - Smooth spring-based animation
 */
@Composable
fun ModernThinkingDots(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00D4FF),
    dotSize: Dp = 6.dp,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 150),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "scale_$index",
            )
            
            Spacer(
                modifier = Modifier
                    .width(dotSize)
                    .height(dotSize)
                    .scale(scale)
                    .background(
                        color = color,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            )
        }
    }
}

/**
 * Pulse animation for active states.
 */
@Composable
fun PulseIndicator(
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.ui.graphics.Color(0xFF00D4FF),
    content: @Composable () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    
    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = alpha),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
    ) {
        content()
    }
}
