package dev.kid.core.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes

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
                        KidColors.GlassFill,
                        KidColors.GlassHighlight.copy(alpha = 0.1f),
                    ),
                ),
                shape = KidShapes.GlassPanel,
            )
            .border(
                width = 1.dp,
                color = KidColors.GlassBorder,
                shape = KidShapes.GlassPanel,
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
                        KidColors.SurfaceGlass,
                        KidColors.GlassHighlight.copy(alpha = 0.05f),
                    ),
                ),
                shape = KidShapes.GlassCard,
            )
            .border(
                width = 1.dp,
                color = KidColors.SurfaceGlassStroke,
                shape = KidShapes.GlassCard,
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
                        KidColors.Primary.copy(alpha = 0.2f),
                        KidColors.Primary.copy(alpha = 0.05f),
                    ),
                ),
                shape = KidShapes.IconButton,
            )
            .border(
                width = 1.dp,
                color = KidColors.Primary.copy(alpha = 0.3f),
                shape = KidShapes.IconButton,
            ),
    ) {
        content()
    }
}
