package dev.kid.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.kid.core.ui.glass.GlassCard
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidTypography

enum class NudgeType {
    SUGGESTION,
    REMINDER,
    INSIGHT,
    GREETING,
}

data class Nudge(
    val id: String,
    val type: NudgeType,
    val title: String,
    val body: String,
    val priority: Float = 0.5f,
)

@Composable
fun NudgeCard(
    nudge: Nudge,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val (icon, accent) = nudge.type.toIconAndColor()

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            initialOffsetY = { it },
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        GlassCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            accentColor = accent,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nudge.title,
                        style = KidTypography.LabelMedium,
                        color = KidColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = nudge.body,
                        style = KidTypography.BodySmall,
                        color = KidColors.TextSecondary,
                    )
                }
            }
        }
    }
}

private fun NudgeType.toIconAndColor(): Pair<ImageVector, Color> = when (this) {
    NudgeType.SUGGESTION -> Icons.Rounded.AutoAwesome to KidColors.Primary
    NudgeType.REMINDER -> Icons.Rounded.Schedule to KidColors.CognitionOffloading
    NudgeType.INSIGHT -> Icons.Rounded.TipsAndUpdates to KidColors.CognitionActing
    NudgeType.GREETING -> Icons.Rounded.AutoAwesome to KidColors.SentimentHappy
}
