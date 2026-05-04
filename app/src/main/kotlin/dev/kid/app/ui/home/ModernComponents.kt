package dev.kid.app.ui.home

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidTypography

/**
 * Modern action chip with consistent styling.
 */
@Composable
fun RowScope.ModernActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = {
            androidx.compose.material3.Text(
                text = text,
                style = KidTypography.LabelMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = KidColors.PrimarySurface,
            labelColor = KidColors.TextPrimary,
            leadingIconContentColor = KidColors.Primary,
        ),
        border = androidx.compose.foundation.border.BorderStroke(
            width = 1.dp,
            color = KidColors.GlassBorder,
        ),
        modifier = modifier,
    )
}

/**
 * Modern status pill with thermal and battery info.
 */
@Composable
fun ModernStatusPill(
    brainState: dev.kid.core.common.model.BrainState,
    cognitionState: dev.kid.core.common.model.CognitionState,
    thermalTemp: Float,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // Brain state indicator
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when (brainState) {
                        dev.kid.core.common.model.BrainState.GEMMA_NPU -> KidColors.Primary
                        dev.kid.core.common.model.BrainState.DESKTOP_QWEN -> KidColors.CognitionOffloading
                        else -> KidColors.TextTertiary
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        
        androidx.compose.material3.Text(
            text = brainState.name.replace("_", " "),
            style = KidTypography.Caption,
            color = KidColors.TextSecondary,
        )
        
        // Thermal indicator
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = when {
                        thermalTemp > 45f -> KidColors.ThermalHot
                        thermalTemp > 38f -> KidColors.ThermalWarm
                        else -> KidColors.ThermalCool
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        
        androidx.compose.material3.Text(
            text = "${thermalTemp.toInt()}°C",
            style = KidTypography.Caption,
            color = KidColors.TextTertiary,
        )
        
        // Battery indicator
        androidx.compose.material3.Text(
            text = "$batteryLevel%",
            style = KidTypography.Caption,
            color = KidColors.TextTertiary,
        )
    }
}

/**
 * Modern bottom bar with stats.
 */
@Composable
fun ModernBottomBar(
    recentConversationCount: Int,
    thermalTemp: Float,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .background(
                color = KidColors.Surface.copy(alpha = 0.8f),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            text = "$recentConversationCount conversations",
            style = KidTypography.Caption,
            color = KidColors.TextTertiary,
        )
        
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = when {
                            thermalTemp > 45f -> KidColors.ThermalHot
                            thermalTemp > 38f -> KidColors.ThermalWarm
                            else -> KidColors.ThermalCool
                        },
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            )
            androidx.compose.material3.Text(
                text = "${thermalTemp.toInt()}°C",
                style = KidTypography.Caption,
                color = KidColors.TextTertiary,
            )
        }
        
        androidx.compose.material3.Text(
            text = "$batteryLevel%",
            style = KidTypography.Caption,
            color = KidColors.TextTertiary,
        )
    }
}
