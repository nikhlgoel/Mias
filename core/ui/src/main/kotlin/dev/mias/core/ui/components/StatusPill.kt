package dev.mias.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mias.core.common.model.BrainState
import dev.mias.core.common.model.CognitionState
import dev.mias.core.ui.glass.toGlowColor
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun StatusPill(
    brainState: BrainState,
    cognitionState: CognitionState,
    modifier: Modifier = Modifier,
) {
    val dotColor by animateColorAsState(
        targetValue = cognitionState.toGlowColor(),
        animationSpec = tween(600),
        label = "status_dot",
    )

    Row(
        modifier = modifier
            .clip(MiasShapes.Full)
            .background(MiasColors.SurfaceElevated.copy(alpha = 0.6f))
            .border(0.5.dp, MiasColors.GlassBorder, MiasShapes.Full)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(MiasShapes.Full)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = brainState.displayName(),
            style = MiasTypography.LabelSmall,
            color = MiasColors.TextSecondary,
        )
    }
}

fun BrainState.displayName(): String = when (this) {
    BrainState.GEMMA_NPU -> "Gemma NPU"
    BrainState.MOBILELLM_SURVIVAL -> "Survival"
    BrainState.QWEN_DESKTOP -> "Desktop"
    BrainState.QWEN_WAKING -> "Waking PC"
    BrainState.DEGRADED -> "Degraded"
}

fun BrainState.displayColor(): Color = when (this) {
    BrainState.GEMMA_NPU -> MiasColors.CognitionActing
    BrainState.MOBILELLM_SURVIVAL -> MiasColors.ThermalWarm
    BrainState.QWEN_DESKTOP -> MiasColors.CognitionOffloading
    BrainState.QWEN_WAKING -> MiasColors.CognitionOffloading
    BrainState.DEGRADED -> MiasColors.CognitionStressed
}
