package dev.kid.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.kid.core.agent.model.AgentTaskResult
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidTypography

/**
 * AgentFeed — live feed of what the agent has been doing.
 *
 * Shown in the Agent screen and optionally in the Chat screen
 * as a collapsible panel when ReAct steps are visible.
 */
@Composable
fun AgentFeed(
    results: List<AgentTaskResult>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        results.reversed().forEach { result ->
            AgentFeedItem(result = result)
        }
    }
}

@Composable
fun AgentFeedItem(
    result: AgentTaskResult,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        label = "feed_alpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(
                color = if (result.success) KidColors.SurfaceGlass else KidColors.ErrorRed.copy(alpha = 0.08f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tool icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(toolColor(result.tool).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = toolIcon(result.tool),
                contentDescription = null,
                tint = toolColor(result.tool),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.tool.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = KidTypography.LabelMedium,
                color = KidColors.TextPrimary,
            )
            Text(
                text = result.output.take(60).replace("\n", " ").let {
                    if (result.output.length > 60) "$it…" else it
                },
                style = KidTypography.LabelSmall,
                color = KidColors.TextSecondary,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Status icon
        AnimatedContent(
            targetState = result.success,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "status",
        ) { success ->
            Icon(
                imageVector = if (success) Icons.Rounded.Check else Icons.Rounded.Error,
                contentDescription = if (success) "Success" else "Failed",
                tint = if (success) KidColors.NeonCyan else KidColors.ErrorRed,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Tool icon + color mapping ─────────────────────────────────────────────────

private fun toolIcon(tool: String): ImageVector = when {
    tool.contains("web") || tool.contains("fetch") || tool.contains("research") ->
        Icons.Rounded.Language
    tool.contains("file") || tool.contains("folder") -> Icons.Rounded.Folder
    tool.contains("search") -> Icons.Rounded.Search
    tool.contains("code") || tool.contains("exec") -> Icons.Rounded.Code
    tool.contains("memory") || tool.contains("model") -> Icons.Rounded.Memory
    else -> Icons.Rounded.Code
}

private fun toolColor(tool: String): Color = when {
    tool.contains("web") || tool.contains("research") -> Color(0xFFFFAA00)
    tool.contains("file") -> Color(0xFF00FF88)
    tool.contains("search") -> KidColors.NeonCyan
    tool.contains("code") || tool.contains("exec") -> Color(0xFF8844FF)
    else -> KidColors.NeonCyan
}
