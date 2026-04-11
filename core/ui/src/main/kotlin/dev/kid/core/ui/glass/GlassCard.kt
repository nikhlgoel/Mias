package dev.kid.core.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes

/**
 * Compact glass card for inline content — nudges, status pills, etc.
 *
 * Lighter weight than [GlassPanel] — no blur, just gradient fill + border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = KidColors.Primary,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(KidShapes.Medium)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.05f),
                        KidColors.GlassFill,
                    ),
                    start = Offset.Zero,
                    end = Offset(500f, 500f),
                ),
            )
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f),
                        KidColors.GlassBorder,
                    ),
                ),
                shape = KidShapes.Medium,
            )
            .padding(16.dp),
        content = content,
    )
}
