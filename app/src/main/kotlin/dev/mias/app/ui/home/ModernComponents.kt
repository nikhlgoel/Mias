package dev.mias.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mias.core.common.model.BrainState
import dev.mias.core.common.model.CognitionState
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun RowScope.ModernActionChip(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MiasTypography.LabelMedium,
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
            containerColor = MiasColors.PrimarySurface,
            labelColor = MiasColors.TextPrimary,
            leadingIconContentColor = MiasColors.Primary,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MiasColors.GlassBorder,
        ),
        modifier = modifier,
    )
}

@Composable
fun ModernStatusPill(
    brainState: BrainState,
    cognitionState: CognitionState,
    thermalTemp: Float,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when (brainState) {
                        BrainState.GEMMA_NPU -> MiasColors.Primary
                        BrainState.QWEN_DESKTOP -> MiasColors.CognitionOffloading
                        else -> MiasColors.TextTertiary
                    },
                    shape = CircleShape,
                ),
        )

        Text(
            text = brainState.name.replace("_", " "),
            style = MiasTypography.Caption,
            color = MiasColors.TextSecondary,
        )

        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = when {
                        thermalTemp > 45f -> MiasColors.ThermalHot
                        thermalTemp > 38f -> MiasColors.ThermalWarm
                        else -> MiasColors.ThermalCool
                    },
                    shape = CircleShape,
                ),
        )

        Text(
            text = "${thermalTemp.toInt()}°C",
            style = MiasTypography.Caption,
            color = MiasColors.TextTertiary,
        )

        Text(
            text = "$batteryLevel%",
            style = MiasTypography.Caption,
            color = MiasColors.TextTertiary,
        )
    }
}

@Composable
fun ModernBottomBar(
    recentConversationCount: Int,
    thermalTemp: Float,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(color = MiasColors.Surface.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$recentConversationCount conversations",
            style = MiasTypography.Caption,
            color = MiasColors.TextTertiary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = when {
                            thermalTemp > 45f -> MiasColors.ThermalHot
                            thermalTemp > 38f -> MiasColors.ThermalWarm
                            else -> MiasColors.ThermalCool
                        },
                        shape = CircleShape,
                    ),
            )

            Text(
                text = "${thermalTemp.toInt()}°C · $batteryLevel%",
                style = MiasTypography.Caption,
                color = MiasColors.TextTertiary,
            )
        }
    }
}
