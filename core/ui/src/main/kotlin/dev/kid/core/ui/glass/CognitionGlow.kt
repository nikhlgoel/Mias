package dev.kid.core.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import dev.kid.core.common.model.CognitionState
import dev.kid.core.ui.theme.KidColors

/**
 * Cognition Glow — ambient glow that reflects Kid's current cognitive state.
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
    CognitionState.IDLE -> KidColors.CognitionIdle
    CognitionState.THINKING -> KidColors.CognitionThinking
    CognitionState.ACTING -> KidColors.CognitionActing
    CognitionState.WAITING -> KidColors.CognitionIdle
    CognitionState.OFFLOADING -> KidColors.CognitionOffloading
    CognitionState.STRESSED -> KidColors.CognitionStressed
    CognitionState.LISTENING -> KidColors.CognitionListening
}
