package dev.mias.core.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    text: String,
    type: BubbleType,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    isStreaming: Boolean = false,
    assistantLabel: String? = null,
    image: Bitmap? = null,
    /** Parsed reasoning ("thought"); shown in a collapsible box above the reply. */
    reasoning: String? = null,
    /** Document names this answer drew on (RAG citations). */
    sources: List<String> = emptyList(),
    /** Long-press handler (e.g. copy the message). No-op tap; null disables it. */
    onLongPress: (() -> Unit)? = null,
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
                    .then(
                        if (onLongPress != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onLongPress,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = if (image != null) 6.dp else 14.dp,
                        end = if (image != null) 6.dp else 14.dp,
                        top = if (image != null) 6.dp else 10.dp,
                        bottom = 10.dp,
                    )
                    .animateContentSize(),
            ) {
                // Claude-style collapsible "Thinking Process" box at the top
                // of assistant bubbles whenever reasoning was produced.
                if (type == BubbleType.Mias && !reasoning.isNullOrBlank()) {
                    ThinkingProcessBox(reasoning = reasoning, isStreaming = isStreaming)
                    if (text.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                }

                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .clip(MiasShapes.Medium)
                            .heightIn(max = 220.dp)
                            .widthIn(max = 320.dp),
                    )
                    if (text.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                }

                if (type == BubbleType.THOUGHT || type == BubbleType.ACTION) {
                    StepLabel(type)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Show the reply body once there's visible text, or while
                // streaming when there's no thinking box carrying the activity.
                val showBody = text.isNotBlank() ||
                    (isStreaming && reasoning.isNullOrBlank())
                if (showBody) {
                    val effectiveStartPadding = if (image != null) 8.dp else 0.dp
                    Box(modifier = Modifier.padding(horizontal = effectiveStartPadding)) {
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
                }

                // RAG citations — which documents this answer drew on.
                if (type == BubbleType.Mias && sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MiasColors.Heather,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sources.joinToString(" · "),
                            style = MiasTypography.LabelSmall,
                            color = MiasColors.TextLo,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
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

/**
 * Collapsible "Thinking Process" panel, Claude-style: a dark, semi-transparent
 * card with a 2dp accent left border, a brain-icon header with a rotating
 * chevron, and the reasoning text revealed when expanded. Auto-expands while
 * the answer is still streaming, then the user can fold it away.
 */
@Composable
private fun ThinkingProcessBox(reasoning: String, isStreaming: Boolean) {
    // First composition decides the default: live (streaming) messages open
    // so the user watches it think; reloaded/finished ones start collapsed.
    var expanded by remember { mutableStateOf(isStreaming) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MiasShapes.Small)
            .background(MiasColors.Surface0.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = MiasColors.OutlineSoft,
                shape = MiasShapes.Small,
            ),
    ) {
        // Header — whole row toggles.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 2dp accent left edge.
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .clip(MiasShapes.Full)
                    .background(MiasColors.Heather),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = MiasColors.Heather,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isStreaming) "Thinking…" else "Thinking Process",
                style = MiasTypography.LabelMedium,
                color = MiasColors.TextLo,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MiasColors.TextLo,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Text(
                text = reasoning,
                style = MiasTypography.BodySmall,
                color = MiasColors.TextLo,
                modifier = Modifier.padding(start = 18.dp, end = 10.dp, bottom = 10.dp),
            )
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
