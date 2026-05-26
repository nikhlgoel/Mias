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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.speech.SpeechLanguage
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit = {},
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
                text = "Settings",
                style = MiasTypography.HeadlineMedium,
                color = MiasColors.TextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // ── Device status ────────────────────────────────────
            SectionHeader("Device")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = MiasColors.SurfaceGlass) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow(
                        label = "Temperature",
                        value = state.thermalTempC?.let { "${"%.0f".format(it)} °C" }
                            ?: "Awaiting first reading",
                    )
                    StatusRow(
                        label = "Battery",
                        value = state.batteryLevel?.let { "$it%" } ?: "Awaiting first reading",
                    )
                    StatusRow(
                        label = "Desktop offload",
                        value = if (state.isDesktopReachable) "Connected" else "Not configured",
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Installed models ─────────────────────────────────
            SectionHeader("Models")
            Spacer(modifier = Modifier.height(8.dp))

            if (state.installedModels.isEmpty()) {
                GlassCard(accentColor = MiasColors.SurfaceGlass) {
                    Column {
                        Text(
                            text = "No models are installed yet.",
                            style = MiasTypography.BodyMedium,
                            color = MiasColors.TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Visit Models to choose one that suits your device. " +
                                "Qwen2.5 0.5B is a balanced first choice — compact and " +
                                "supported on most phones.",
                            style = MiasTypography.BodySmall,
                            color = MiasColors.TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(MiasShapes.Card)
                                .background(MiasColors.Primary.copy(alpha = 0.2f))
                                .clickable(onClick = onNavigateToModels)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = "Browse models",
                                style = MiasTypography.LabelMedium,
                                color = MiasColors.TextPrimary,
                            )
                        }
                    }
                }
            } else {
                state.installedModels.forEach { model ->
                    val assignedRoles = ModelRole.entries
                        .filter { state.roleAssignments[it] == model.id }
                    InstalledModelRow(
                        model = model,
                        assignedRoles = assignedRoles,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Speech ───────────────────────────────────────────
            SectionHeader("Voice")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = MiasColors.Primary) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Recognition language: ${state.speechLanguage.displayName}",
                        style = MiasTypography.LabelLarge,
                        color = MiasColors.TextPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Detect language automatically",
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
                        LangChip(
                            modifier = Modifier.weight(1f),
                            label = "English (US)",
                            selected = state.speechLanguage == SpeechLanguage.ENGLISH_US,
                            onClick = { viewModel.setSpeechLanguage(SpeechLanguage.ENGLISH_US) },
                        )
                        LangChip(
                            modifier = Modifier.weight(1f),
                            label = "English (UK)",
                            selected = state.speechLanguage == SpeechLanguage.ENGLISH_GB,
                            onClick = { viewModel.setSpeechLanguage(SpeechLanguage.ENGLISH_GB) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Privacy ──────────────────────────────────────────
            SectionHeader("Privacy")
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
                            text = "Private by design",
                            style = MiasTypography.LabelLarge,
                            color = MiasColors.TextPrimary,
                        )
                        Text(
                            text = "Every reply is generated on this device. " +
                                "The only network requests Mias makes are for downloading " +
                                "the models you choose from huggingface.co.",
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
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MiasTypography.BodyMedium, color = MiasColors.TextSecondary)
        Text(value, style = MiasTypography.LabelMedium, color = MiasColors.TextPrimary)
    }
}

@Composable
private fun InstalledModelRow(
    model: InstalledModel,
    assignedRoles: List<ModelRole>,
) {
    GlassCard(
        accentColor = if (assignedRoles.isNotEmpty()) MiasColors.CognitionActing else MiasColors.SurfaceGlass,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = null,
                tint = if (assignedRoles.isNotEmpty()) MiasColors.CognitionActing else MiasColors.TextTertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.card.name,
                    style = MiasTypography.LabelLarge,
                    color = MiasColors.TextPrimary,
                )
                Text(
                    text = "${model.card.parameterCount} · ${model.card.quantization} · " +
                        formatMb(model.sizeOnDisk),
                    style = MiasTypography.BodySmall,
                    color = MiasColors.TextSecondary,
                )
                if (assignedRoles.isNotEmpty()) {
                    Text(
                        text = "Assigned: " + assignedRoles.joinToString(", ") {
                            it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                        },
                        style = MiasTypography.LabelSmall,
                        color = MiasColors.CognitionActing,
                    )
                }
            }
            if (assignedRoles.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Active",
                    tint = MiasColors.CognitionActing,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LangChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MiasShapes.Large)
            .background(if (selected) MiasColors.Primary.copy(alpha = 0.22f) else MiasColors.SurfaceGlass)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
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

private fun formatMb(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) "${"%.1f".format(mb / 1000)} GB" else "${mb.toInt()} MB"
}
