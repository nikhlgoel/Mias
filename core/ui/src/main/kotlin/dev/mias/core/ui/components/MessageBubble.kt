package dev.mias.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

/**
 * Chat message bubble for the conversation interface.
 *
 * - User messages align right with blue gradient
 * - Mias messages align left with dark surface
 * - Thought steps show in muted purple (ReAct thinking)
 * - Action steps show in green (ReAct tool call)
 * - Error messages show in red-tinted container
 */
@Composable
fun MessageBubble(
    text: String,
    type: BubbleType,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    isStreaming: Boolean = false,
) {
    val alignment = when (type) {
        BubbleType.USER -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    val bubbleShape = when (type) {
        BubbleType.USER -> MiasShapes.BubbleUser
        else -> MiasShapes.BubbleKid
    }

    val (bgBrush, textStyle, textColor) = when (type) {
        BubbleType.USER -> Triple(
            Brush.linearGradient(
                colors = listOf(MiasColors.BubbleUser, MiasColors.BubbleUser.copy(alpha = 0.8f)),
                start = Offset.Zero,
                end = Offset(300f, 300f),
            ),
            MiasTypography.BodyLarge,
            MiasColors.TextPrimary,
        )
        BubbleType.Mias -> Triple(
            Brush.linearGradient(
                colors = listOf(MiasColors.BubbleKid, MiasColors.Surface),
            ),
            MiasTypography.BodyLarge,
            MiasColors.TextPrimary,
        )
        BubbleType.THOUGHT -> Triple(
            Brush.linearGradient(
                colors = listOf(MiasColors.BubbleThought, MiasColors.BubbleThought.copy(alpha = 0.6f)),
            ),
            MiasTypography.Thought,
            MiasColors.TextTertiary,
        )
        BubbleType.ACTION -> Triple(
            Brush.linearGradient(
                colors = listOf(MiasColors.BubbleAction, MiasColors.BubbleAction.copy(alpha = 0.6f)),
            ),
            MiasTypography.Code,
            MiasColors.CognitionActing,
        )
        BubbleType.ERROR -> Triple(
            Brush.linearGradient(
                colors = listOf(MiasColors.BubbleError, MiasColors.BubbleError.copy(alpha = 0.6f)),
            ),
            MiasTypography.BodyMedium,
            MiasColors.Error,
        )
    }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = alignment,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(bubbleShape)
                    .background(bgBrush)
                    .border(
                        width = 1.dp,
                        color = MiasColors.GlassBorder.copy(alpha = 0.4f),
                        shape = bubbleShape
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                ),
        ) {
            if (type == BubbleType.THOUGHT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(MiasShapes.Full)
                            .background(MiasColors.CognitionThinking),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "thinking",
                        style = MiasTypography.LabelSmall,
                        color = MiasColors.CognitionThinking.copy(alpha = 0.7f),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (type == BubbleType.ACTION) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(MiasShapes.Full)
                            .background(MiasColors.CognitionActing),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "action",
                        style = MiasTypography.LabelSmall,
                        color = MiasColors.CognitionActing.copy(alpha = 0.7f),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = text,
                style = textStyle,
                color = textColor,
            )

            if (isStreaming) {
                Spacer(modifier = Modifier.height(4.dp))
                ThinkingDots(color = MiasColors.TextTertiary)
            }

            if (timestamp != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timestamp,
                    style = MiasTypography.LabelSmall,
                    color = MiasColors.TextTertiary,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

enum class BubbleType {
    USER,
    Mias,
    THOUGHT,
    ACTION,
    ERROR,
}
