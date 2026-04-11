package dev.kid.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kid.core.ui.components.BrainStatusBar
import dev.kid.core.ui.glass.GlassCard
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes
import dev.kid.core.ui.theme.KidTypography

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KidColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = KidColors.TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "System Status",
                style = KidTypography.HeadlineMedium,
                color = KidColors.TextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // ── Active Brain Status ──
            BrainStatusBar(
                brainState = state.brainState,
                thermalTemp = state.thermalTemp,
                batteryLevel = state.batteryLevel,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Model Registry ──
            SectionHeader(title = "Neural Registry")
            Spacer(modifier = Modifier.height(8.dp))
            ModelCard(
                name = state.modelInfo.primaryModel,
                role = "Primary Brain (On-Device NPU)",
                quant = state.modelInfo.primaryQuant,
                isActive = state.brainState == dev.kid.core.common.model.BrainState.GEMMA_NPU,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModelCard(
                name = state.modelInfo.survivalModel,
                role = "Survival Brain (CPU Fallback)",
                quant = "INT4 ONNX",
                isActive = state.brainState == dev.kid.core.common.model.BrainState.MOBILELLM_SURVIVAL,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModelCard(
                name = state.modelInfo.desktopModel,
                role = "Desktop Brain (Via Tailscale Mesh)",
                quant = "Q4_K_M GGUF",
                isActive = state.brainState == dev.kid.core.common.model.BrainState.QWEN_DESKTOP,
                isReachable = state.isDesktopReachable,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Soul Personality Blend ──
            SectionHeader(title = "Soul Blend")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = KidColors.SentimentCurious) {
                Column {
                    state.soulTraits.forEach { (trait, weight) ->
                        SoulTraitRow(
                            name = trait.name.lowercase().replaceFirstChar { it.uppercase() },
                            weight = weight,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Privacy Badge ──
            SectionHeader(title = "Privacy")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = KidColors.Success) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = KidColors.Success,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Zero-Cloud Active",
                            style = KidTypography.LabelLarge,
                            color = KidColors.TextPrimary,
                        )
                        Text(
                            text = "All inference local. No data leaves this device.",
                            style = KidTypography.BodySmall,
                            color = KidColors.TextSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = KidTypography.LabelMedium,
        color = KidColors.TextTertiary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ModelCard(
    name: String,
    role: String,
    quant: String,
    isActive: Boolean,
    isReachable: Boolean = true,
) {
    GlassCard(
        accentColor = if (isActive) KidColors.CognitionActing else KidColors.SurfaceGlass,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = if (isActive) KidColors.CognitionActing else KidColors.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = name,
                        style = KidTypography.LabelLarge,
                        color = KidColors.TextPrimary,
                    )
                    Text(
                        text = role,
                        style = KidTypography.BodySmall,
                        color = KidColors.TextSecondary,
                    )
                    Text(
                        text = "Quantization: $quant",
                        style = KidTypography.LabelSmall,
                        color = KidColors.TextTertiary,
                    )
                }
            }

            if (!isReachable) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = "Offline",
                    tint = KidColors.TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SoulTraitRow(
    name: String,
    weight: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = KidTypography.LabelMedium,
            color = KidColors.TextSecondary,
            modifier = Modifier.width(80.dp),
        )
        LinearProgressIndicator(
            progress = { weight.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(KidShapes.Full),
            color = KidColors.Primary,
            trackColor = KidColors.SurfaceGlass,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(weight * 100).toInt()}%",
            style = KidTypography.LabelSmall,
            color = KidColors.TextTertiary,
        )
    }
}
