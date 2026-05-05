package dev.mias.core.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes

/**
 * Modern glassmorphism panel with blur effect.
 *
 * Features:
 * - Translucent background with gradient
 * - Subtle border for depth
 * - Optional blur effect (if supported by API)
 * - Rounded corners for modern look
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MiasColors.GlassFill,
                        MiasColors.GlassHighlight.copy(alpha = 0.1f),
                    ),
                ),
                shape = MiasShapes.GlassPanel,
            )
            .border(
                width = 1.dp,
                color = MiasColors.GlassBorder,
                shape = MiasShapes.GlassPanel,
            ),
    ) {
        content()
    }
}

/**
 * Glass card with elevated appearance.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MiasColors.SurfaceGlass,
                        MiasColors.GlassHighlight.copy(alpha = 0.05f),
                    ),
                ),
                shape = MiasShapes.GlassCard,
            )
            .border(
                width = 1.dp,
                color = MiasColors.SurfaceGlassStroke,
                shape = MiasShapes.GlassCard,
            ),
    ) {
        content()
    }
}

/**
 * Floating glass action button.
 */
@Composable
fun GlassActionButton(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MiasColors.Primary.copy(alpha = 0.2f),
                        MiasColors.Primary.copy(alpha = 0.05f),
                    ),
                ),
                shape = MiasShapes.IconButton,
            )
            .border(
                width = 1.dp,
                color = MiasColors.Primary.copy(alpha = 0.3f),
                shape = MiasShapes.IconButton,
            ),
    ) {
        content()
    }
}
