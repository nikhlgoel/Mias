package dev.mias.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.ui.components.BrainStatusBar
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography
import dev.mias.core.speech.SpeechLanguage

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
            .background(MiasColors.Background)
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
                    tint = MiasColors.TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "System Status",
                style = MiasTypography.HeadlineMedium,
                color = MiasColors.TextPrimary,
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
                isActive = state.brainState == dev.mias.core.common.model.BrainState.GEMMA_NPU,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModelCard(
                name = state.modelInfo.survivalModel,
                role = "Survival Brain (CPU Fallback)",
                quant = "INT4 ONNX",
                isActive = state.brainState == dev.mias.core.common.model.BrainState.MOBILELLM_SURVIVAL,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModelCard(
                name = state.modelInfo.desktopModel,
                role = "Desktop Brain (Via Tailscale Mesh)",
                quant = "Q4_K_M GGUF",
                isActive = state.brainState == dev.mias.core.common.model.BrainState.QWEN_DESKTOP,
                isReachable = state.isDesktopReachable,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Soul Personality Blend ──
            SectionHeader(title = "Soul Blend")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = MiasColors.SentimentCurious) {
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

            // ── Speech & Transcription ──
            SectionHeader(title = "Speech & Transcription")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = MiasColors.Primary) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Language: ${state.speechLanguage.displayName}",
                        style = MiasTypography.LabelLarge,
                        color = MiasColors.TextPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Auto Detect Language",
                            style = MiasTypography.BodyMedium,
                            color = MiasColors.TextSecondary,
                        )
                        Switch(
                            checked = state.speechAutoDetect,
                            onCheckedChange = viewModel::setSpeechAutoDetect,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SpeechChip(
                            modifier = Modifier.fillMaxWidth(0.48f),
                            label = "English (US)",
                            selected = state.speechLanguage == SpeechLanguage.ENGLISH_US,
                            onClick = { viewModel.setSpeechLanguage(SpeechLanguage.ENGLISH_US) },
                        )
                        SpeechChip(
                            modifier = Modifier.fillMaxWidth(0.48f),
                            label = "English (UK)",
                            selected = state.speechLanguage == SpeechLanguage.ENGLISH_GB,
                            onClick = { viewModel.setSpeechLanguage(SpeechLanguage.ENGLISH_GB) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Privacy Badge ──
            SectionHeader(title = "Privacy")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = MiasColors.Success) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MiasColors.Success,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Zero-Cloud Active",
                            style = MiasTypography.LabelLarge,
                            color = MiasColors.TextPrimary,
                        )
                        Text(
                            text = "All inference local. No data leaves this device.",
                            style = MiasTypography.BodySmall,
                            color = MiasColors.TextSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SpeechChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MiasShapes.Large)
            .background(if (selected) MiasColors.Primary.copy(alpha = 0.22f) else MiasColors.SurfaceGlass)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MiasTypography.LabelMedium,
            color = if (selected) MiasColors.TextPrimary else MiasColors.TextSecondary,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MiasTypography.LabelMedium,
        color = MiasColors.TextTertiary,
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
        accentColor = if (isActive) MiasColors.CognitionActing else MiasColors.SurfaceGlass,
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
                    tint = if (isActive) MiasColors.CognitionActing else MiasColors.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = name,
                        style = MiasTypography.LabelLarge,
                        color = MiasColors.TextPrimary,
                    )
                    Text(
                        text = role,
                        style = MiasTypography.BodySmall,
                        color = MiasColors.TextSecondary,
                    )
                    Text(
                        text = "Quantization: $quant",
                        style = MiasTypography.LabelSmall,
                        color = MiasColors.TextTertiary,
                    )
                }
            }

            if (!isReachable) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = "Offline",
                    tint = MiasColors.TextTertiary,
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
            style = MiasTypography.LabelMedium,
            color = MiasColors.TextSecondary,
            modifier = Modifier.width(80.dp),
        )
        LinearProgressIndicator(
            progress = { weight.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(MiasShapes.Full),
            color = MiasColors.Primary,
            trackColor = MiasColors.SurfaceGlass,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(weight * 100).toInt()}%",
            style = MiasTypography.LabelSmall,
            color = MiasColors.TextTertiary,
        )
    }
}
