package dev.mias.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

/**
 * Chat message bubble.
 *
 * Palette-aligned per Color System v1:
 *  - User bubble: Heather (`primary`), HeatherInk text.
 *  - Assistant bubble: Surface3 (`surfaceVariant`), TextHi text.
 *  - Thought / Action: muted surface tints, only shown when the user has
 *    enabled "Show thinking steps".
 *
 * 18dp corners with a 6dp tail on the source side. Streaming responses
 * render an animated cursor (▍) at the end of text instead of a separate
 * dot row, matching modern chat conventions.
 */
@Composable
fun MessageBubble(
    text: String,
    type: BubbleType,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    isStreaming: Boolean = false,
    assistantLabel: String? = null,
) {
    val alignment = if (type == BubbleType.USER) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleShape = when (type) {
        BubbleType.USER -> MiasShapes.BubbleUser
        else -> MiasShapes.BubbleKid
    }

    val (background, textColor) = when (type) {
        BubbleType.USER -> MiasColors.Heather to MiasColors.HeatherInk
        BubbleType.Mias -> MiasColors.Surface3 to MiasColors.TextHi
        BubbleType.THOUGHT -> MiasColors.BubbleThought to MiasColors.TextLo
        BubbleType.ACTION -> MiasColors.BubbleAction to MiasColors.SuccessTone
        BubbleType.ERROR -> MiasColors.BubbleError to MiasColors.ErrorTone
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = alignment,
    ) {
        Column(
            horizontalAlignment = if (type == BubbleType.USER) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (type != BubbleType.USER && assistantLabel != null) {
                Text(
                    text = assistantLabel,
                    style = MiasTypography.LabelSmall,
                    color = MiasColors.TextLo,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            Column(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(background)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize(),
            ) {
                if (type == BubbleType.THOUGHT || type == BubbleType.ACTION) {
                    StepLabel(type)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (isStreaming && type == BubbleType.Mias) {
                    StreamingText(text = text, textColor = textColor)
                } else {
                    Text(
                        text = text,
                        style = MiasTypography.BodyLarge,
                        color = textColor,
                    )
                }
            }

            if (timestamp != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timestamp,
                    style = MiasTypography.LabelSmall,
                    color = MiasColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StepLabel(type: BubbleType) {
    val (dotColor, label) = when (type) {
        BubbleType.THOUGHT -> MiasColors.HeatherDim to "thinking"
        BubbleType.ACTION -> MiasColors.SuccessTone to "action"
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(MiasShapes.Full)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MiasTypography.LabelSmall,
            color = MiasColors.TextLo,
        )
    }
}

@Composable
private fun StreamingText(text: String, textColor: Color) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            style = MiasTypography.BodyLarge,
            color = textColor,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .alpha(cursorAlpha)
                .size(width = 8.dp, height = 18.dp)
                .background(textColor.copy(alpha = 0.85f)),
        )
    }
}

enum class BubbleType {
    USER,
    Mias,
    THOUGHT,
    ACTION,
    ERROR,
}
