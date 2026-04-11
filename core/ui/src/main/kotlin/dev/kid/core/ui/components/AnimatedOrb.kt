package dev.kid.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kid.core.common.model.CognitionState
import dev.kid.core.ui.glass.toGlowColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Intelligence Orb — the visual heartbeat of {Kid}.
 *
 * A pulsing, glowing orb that breathes and shifts color based on
 * Kid's cognitive state. The orb has 3 concentric layers:
 * 1. Core: bright, pulsing center
 * 2. Inner ring: softer glow with rotation
 * 3. Outer aura: large, faint ambient glow
 *
 * @param cognitionState Current AI cognitive state
 * @param size Diameter of the orb
 * @param breathingEnabled Whether the orb pulses
 */
@Composable
fun AnimatedOrb(
    cognitionState: CognitionState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    breathingEnabled: Boolean = true,
) {
    val baseColor = cognitionState.toGlowColor()

    val transition = rememberInfiniteTransition(label = "orb")

    val breathScale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (cognitionState == CognitionState.THINKING) 600 else 2000,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val pulseAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val scale = if (breathingEnabled) breathScale else 0.9f

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val baseRadius = this.size.minDimension / 2

        // Layer 3: Outer aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = center,
                radius = baseRadius * 1.4f,
            ),
            radius = baseRadius * 1.4f,
            center = center,
        )

        // Layer 2: Inner ring with rotation offset
        val ringOffsetX = cos(Math.toRadians(rotationAngle.toDouble())).toFloat() * baseRadius * 0.1f
        val ringOffsetY = sin(Math.toRadians(rotationAngle.toDouble())).toFloat() * baseRadius * 0.1f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = pulseAlpha * 0.5f),
                    baseColor.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
                center = center.copy(
                    x = center.x + ringOffsetX,
                    y = center.y + ringOffsetY,
                ),
                radius = baseRadius * scale,
            ),
            radius = baseRadius * scale,
            center = center,
        )

        // Layer 1: Core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.9f),
                    baseColor.copy(alpha = 0.8f),
                    baseColor.copy(alpha = 0.3f),
                    Color.Transparent,
                ),
                center = center,
                radius = baseRadius * scale * 0.5f,
            ),
            radius = baseRadius * scale * 0.5f,
            center = center,
        )
    }
}
