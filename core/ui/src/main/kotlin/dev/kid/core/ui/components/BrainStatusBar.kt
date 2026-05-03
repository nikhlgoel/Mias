package dev.kid.core.ui.components

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
import dev.kid.core.common.model.BrainState
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes
import dev.kid.core.ui.theme.KidTypography

@Composable
fun BrainStatusBar(
    brainState: BrainState,
    thermalTemp: Float,
    batteryLevel: Int,
    modifier: Modifier = Modifier,
) {
    val thermalColor by animateColorAsState(
        targetValue = when {
            thermalTemp < 38f -> KidColors.ThermalCool
            thermalTemp < 42f -> KidColors.ThermalWarm
            thermalTemp < 50f -> KidColors.ThermalHot
            else -> KidColors.ThermalCritical
        },
        animationSpec = tween(600),
        label = "thermal",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KidShapes.Medium)
            .background(KidColors.SurfaceElevated.copy(alpha = 0.5f))
            .border(0.5.dp, KidColors.GlassBorder, KidShapes.Medium)
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
                    style = KidTypography.LabelMedium,
                    color = KidColors.TextPrimary,
                )
                Text(
                    text = "Active Brain",
                    style = KidTypography.LabelSmall,
                    color = KidColors.TextTertiary,
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
                style = KidTypography.LabelMedium,
                color = thermalColor,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$batteryLevel%",
                    style = KidTypography.LabelSmall,
                    color = KidColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { batteryLevel / 100f },
                    modifier = Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .clip(KidShapes.Full),
                    color = when {
                        batteryLevel > 30 -> KidColors.ThermalCool
                        batteryLevel > 15 -> KidColors.ThermalWarm
                        else -> KidColors.ThermalCritical
                    },
                    trackColor = KidColors.SurfaceGlass,
                )
            }
        }
    }
}
