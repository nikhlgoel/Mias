package dev.mias.core.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import dev.mias.core.common.model.CognitionState
import dev.mias.core.ui.theme.MiasColors

/**
 * Cognition Glow — ambient glow that reflects Mias's current cognitive state.
 *
 * Wraps content with a radial glow whose color smoothly transitions
 * as the cognition state changes. Used behind the main orb and
 * as a subtle status indicator.
 */
@Composable
fun CognitionGlow(
    cognitionState: CognitionState,
    modifier: Modifier = Modifier,
    intensity: Float = 0.3f,
    content: @Composable BoxScope.() -> Unit,
) {
    val targetColor = cognitionState.toGlowColor()
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 800),
        label = "cognition_glow",
    )

    Box(
        modifier = modifier.drawBehind {
            drawCircle(
                color = animatedColor.copy(alpha = intensity * 0.4f),
                radius = size.maxDimension * 0.6f,
            )
            drawCircle(
                color = animatedColor.copy(alpha = intensity * 0.15f),
                radius = size.maxDimension * 0.9f,
            )
        },
        content = content,
    )
}

fun CognitionState.toGlowColor(): Color = when (this) {
    CognitionState.IDLE -> MiasColors.CognitionIdle
    CognitionState.THINKING -> MiasColors.CognitionThinking
    CognitionState.ACTING -> MiasColors.CognitionActing
    CognitionState.WAITING -> MiasColors.CognitionIdle
    CognitionState.OFFLOADING -> MiasColors.CognitionOffloading
    CognitionState.STRESSED -> MiasColors.CognitionStressed
    CognitionState.LISTENING -> MiasColors.CognitionListening
}
