package dev.mias.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.mias.core.common.model.BrainState
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun BrainStatusBar(
    brainState: BrainState,
    thermalTemp: Float,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    val thermalColor by animateColorAsState(
        targetValue = when {
            thermalTemp < 38f -> MiasColors.ThermalCool
            thermalTemp < 42f -> MiasColors.ThermalWarm
            thermalTemp < 50f -> MiasColors.ThermalHot
            else -> MiasColors.ThermalCritical
        },
        animationSpec = tween(600),
        label = "thermal",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MiasShapes.Medium)
            .background(MiasColors.SurfaceElevated.copy(alpha = 0.5f))
            .border(0.5.dp, MiasColors.GlassBorder, MiasShapes.Medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Brain info
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = null,
                tint = brainState.displayColor(),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = brainState.displayName(),
                    style = MiasTypography.LabelMedium,
                    color = MiasColors.TextPrimary,
                )
                Text(
                    text = "Active Brain",
                    style = MiasTypography.LabelSmall,
                    color = MiasColors.TextTertiary,
                )
            }
        }

        // Thermal + Battery
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Thermostat,
                contentDescription = null,
                tint = thermalColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${thermalTemp.toInt()}°C",
                style = MiasTypography.LabelMedium,
                color = thermalColor,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$batteryLevel%",
                    style = MiasTypography.LabelSmall,
                    color = MiasColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { batteryLevel / 100f },
                    modifier = Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .clip(MiasShapes.Full),
                    color = when {
                        batteryLevel > 30 -> MiasColors.ThermalCool
                        batteryLevel > 15 -> MiasColors.ThermalWarm
                        else -> MiasColors.ThermalCritical
                    },
                    trackColor = MiasColors.SurfaceGlass,
                )
            }
        }
    }
}
