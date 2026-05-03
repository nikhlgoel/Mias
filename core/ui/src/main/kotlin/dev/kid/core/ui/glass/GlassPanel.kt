package dev.kid.core.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes

/**
 * Liquid Glass Panel — frosted glassmorphic container.
 *
 * Creates iOS/macOS-style glassmorphism effect using layered blur,
 * gradient backgrounds, and subtle border highlights. Optimized for
 * Android 15+ RenderEffect capabilities.
 *
 * @param glowColor Optional accent glow color (e.g., cognition state color)
 * @param glowIntensity 0.0-1.0 intensity of the accent glow
 * @param blurRadius Backdrop blur radius
 * @param animated Whether the internal gradient subtly shifts over time
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    glowIntensity: Float = 0f,
    blurRadius: Dp = 24.dp,
    animated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val gradientOffset = if (animated) {
        val transition = rememberInfiniteTransition(label = "glass_shimmer")
        val offset by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glass_offset",
        )
        offset
    } else {
        0f
    }

    Box(
        modifier = modifier
            .clip(KidShapes.Glass)
            .then(
                if (glowIntensity > 0f && glowColor != Color.Transparent) {
                    Modifier.drawBehind {
                        drawCircle(
                            color = glowColor.copy(alpha = glowIntensity * 0.15f),
                            radius = size.maxDimension * 0.7f,
                            center = Offset(size.width * 0.5f, size.height * 0.3f),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .blur(blurRadius)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        KidColors.GlassFill,
                        KidColors.GlassFill.copy(alpha = 0.05f),
                    ),
                    start = Offset(0f, gradientOffset * 200f),
                    end = Offset(500f, gradientOffset * 200f + 500f),
                ),
            )
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        KidColors.GlassBorder,
                        KidColors.GlassBorder.copy(alpha = 0.05f),
                    ),
                ),
                shape = KidShapes.Glass,
            )
            .padding(1.dp),
        content = content,
    )
}
