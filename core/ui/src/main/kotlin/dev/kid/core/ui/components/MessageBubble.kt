package dev.kid.core.ui.components

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
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes
import dev.kid.core.ui.theme.KidTypography

/**
 * Chat message bubble for the conversation interface.
 *
 * - User messages align right with blue gradient
 * - Kid messages align left with dark surface
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
        BubbleType.USER -> KidShapes.BubbleUser
        else -> KidShapes.BubbleKid
    }

    val (bgBrush, textStyle, textColor) = when (type) {
        BubbleType.USER -> Triple(
            Brush.linearGradient(
                colors = listOf(KidColors.BubbleUser, KidColors.BubbleUser.copy(alpha = 0.8f)),
                start = Offset.Zero,
                end = Offset(300f, 300f),
            ),
            KidTypography.BodyLarge,
            KidColors.TextPrimary,
        )
        BubbleType.KID -> Triple(
            Brush.linearGradient(
                colors = listOf(KidColors.BubbleKid, KidColors.Surface),
            ),
            KidTypography.BodyLarge,
            KidColors.TextPrimary,
        )
        BubbleType.THOUGHT -> Triple(
            Brush.linearGradient(
                colors = listOf(KidColors.BubbleThought, KidColors.BubbleThought.copy(alpha = 0.6f)),
            ),
            KidTypography.Thought,
            KidColors.TextTertiary,
        )
        BubbleType.ACTION -> Triple(
            Brush.linearGradient(
                colors = listOf(KidColors.BubbleAction, KidColors.BubbleAction.copy(alpha = 0.6f)),
            ),
            KidTypography.Code,
            KidColors.CognitionActing,
        )
        BubbleType.ERROR -> Triple(
            Brush.linearGradient(
                colors = listOf(KidColors.BubbleError, KidColors.BubbleError.copy(alpha = 0.6f)),
            ),
            KidTypography.BodyMedium,
            KidColors.Error,
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
                        color = KidColors.GlassBorder.copy(alpha = 0.4f),
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
                            .clip(KidShapes.Full)
                            .background(KidColors.CognitionThinking),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "thinking",
                        style = KidTypography.LabelSmall,
                        color = KidColors.CognitionThinking.copy(alpha = 0.7f),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (type == BubbleType.ACTION) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(KidShapes.Full)
                            .background(KidColors.CognitionActing),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "action",
                        style = KidTypography.LabelSmall,
                        color = KidColors.CognitionActing.copy(alpha = 0.7f),
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
                ThinkingDots(color = KidColors.TextTertiary)
            }

            if (timestamp != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timestamp,
                    style = KidTypography.LabelSmall,
                    color = KidColors.TextTertiary,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

enum class BubbleType {
    USER,
    KID,
    THOUGHT,
    ACTION,
    ERROR,
}
