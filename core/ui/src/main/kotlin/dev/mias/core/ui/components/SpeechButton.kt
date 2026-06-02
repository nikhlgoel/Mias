package dev.mias.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.mias.core.speech.SpeechState
import dev.mias.core.ui.theme.MiasColors

/**
 * Circular mic button for the chat input row.
 *
 * Fixed 40×40 dp — matches [MiasInputBar]'s minimum height so the
 * surrounding Row(verticalAlignment = Alignment.Bottom) is always stable.
 *
 * State is expressed purely through animated color + a graphicsLayer pulse.
 * graphicsLayer transforms are graphics-only and do not trigger a layout
 * remeasurement pass, so transcription updates and state changes cannot
 * cause infinite measure loops or layout jitter.
 *
 * Transcription text is intentionally absent here. It writes exclusively to
 * the input field via ChatScreen's LaunchedEffect → applyTranscription.
 * Rendering it here too was the source of the duplicate-text layout bug.
 */
@Composable
fun SpeechButton(
    state: SpeechState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = state == SpeechState.LISTENING || state == SpeechState.PROCESSING

    val bgColor by animateColorAsState(
        targetValue = when (state) {
            SpeechState.LISTENING         -> MiasColors.ErrorTone       // warm red — clearly recording
            SpeechState.PROCESSING        -> MiasColors.HeatherDim      // muted mauve — working
            SpeechState.SUCCESS           -> MiasColors.SuccessTone     // brief green flash
            SpeechState.ERROR,
            SpeechState.PERMISSION_DENIED -> MiasColors.ErrorContainer
            else                          -> MiasColors.Surface3        // IDLE
        },
        label = "micBg",
    )

    val iconTint by animateColorAsState(
        targetValue = if (isActive) MiasColors.Surface0 else MiasColors.TextLo,
        label = "micIcon",
    )

    // Pulse is applied via graphicsLayer so it never changes the measured size,
    // preventing the layout thrashing the old Column approach caused.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            while (true) {
                pulse.animateTo(1.14f, tween(550))
                pulse.animateTo(1f, tween(550))
            }
        } else {
            pulse.animateTo(1f, tween(180))
        }
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (isActive) onStopListening() else onStartListening() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = if (isActive) "Stop recording" else "Start recording",
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}
