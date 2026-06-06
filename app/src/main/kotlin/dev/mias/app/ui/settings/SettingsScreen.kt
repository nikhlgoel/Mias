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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.data.preferences.MiasPrefs
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
    val ctxForStorage = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            // The activity uses adjustNothing (Compose owns insets), so the form
            // must pad for the keyboard itself or the lower fields get covered.
            .imePadding(),
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

            // ── Hugging Face ─────────────────────────────────────
            SectionHeader("Hugging Face")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = MiasColors.SurfaceGlass) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Optional. A personal access token is required to " +
                            "download gated models (most official Google and Meta " +
                            "releases). Public models work without it.",
                        style = MiasTypography.BodySmall,
                        color = MiasColors.TextSecondary,
                    )
                    SecretField(
                        label = "Access token",
                        value = state.huggingFaceToken,
                        onValueChange = viewModel::setHuggingFaceToken,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Desktop offload ──────────────────────────────────
            SectionHeader("Desktop offload")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(accentColor = MiasColors.SurfaceGlass) {
                DesktopOffloadEditor(
                    host = state.desktopHost,
                    port = state.desktopPort,
                    token = state.desktopToken,
                    onSave = viewModel::setDesktopEndpoint,
                )
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

            // ── Storage access ───────────────────────────────────
            SectionHeader("Storage access")
            Spacer(modifier = Modifier.height(8.dp))
            StorageAccessCard(
                summary = viewModel.storageAccessSummary(),
                hasAllFiles = viewModel.hasAllFilesAccess(),
                onEnable = {
                    viewModel.allFilesAccessIntent()?.let { intent ->
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctxForStorage.startActivity(intent) }
                    }
                },
            )

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
private fun StorageAccessCard(
    summary: String,
    hasAllFiles: Boolean,
    onEnable: () -> Unit,
) {
    GlassCard(accentColor = MiasColors.SurfaceGlass) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = summary,
                style = MiasTypography.BodySmall,
                color = MiasColors.TextSecondary,
            )
            Text(
                text = "System files and other apps' data are always off-limits, " +
                    "even with full access enabled.",
                style = MiasTypography.LabelSmall,
                color = MiasColors.TextTertiary,
            )
            if (!hasAllFiles) {
                Box(
                    modifier = Modifier
                        .clip(MiasShapes.Card)
                        .background(MiasColors.Primary.copy(alpha = 0.22f))
                        .clickable(onClick = onEnable)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Enable full storage access",
                        style = MiasTypography.LabelMedium,
                        color = MiasColors.TextPrimary,
                    )
                }
            }
        }
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

@Composable
private fun SecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }

    OutlinedTextField(
        value = draft,
        onValueChange = {
            draft = it
            onValueChange(it)
        },
        label = { Text(label, color = MiasColors.TextSecondary) },
        singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            Text(
                text = if (revealed) "Hide" else "Show",
                style = MiasTypography.LabelSmall,
                color = MiasColors.Primary,
                modifier = Modifier
                    .clickable { revealed = !revealed }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MiasColors.TextPrimary,
            unfocusedTextColor = MiasColors.TextPrimary,
        ),
    )
}

@Composable
private fun DesktopOffloadEditor(
    host: String,
    port: Int,
    token: String,
    onSave: (host: String, port: Int, token: String) -> Unit,
) {
    var hostDraft by remember { mutableStateOf(host) }
    var portDraft by remember { mutableStateOf(port.toString()) }
    var tokenDraft by remember { mutableStateOf(token) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Route long-context or coding tasks to a desktop running the " +
                "Mias MCP server on your local network. Leave blank to keep " +
                "everything on this device.",
            style = MiasTypography.BodySmall,
            color = MiasColors.TextSecondary,
        )
        OutlinedTextField(
            value = hostDraft,
            onValueChange = { hostDraft = it },
            label = { Text("Host or IP", color = MiasColors.TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MiasColors.TextPrimary,
                unfocusedTextColor = MiasColors.TextPrimary,
            ),
        )
        OutlinedTextField(
            value = portDraft,
            onValueChange = { portDraft = it.filter(Char::isDigit).take(5) },
            label = { Text("Port", color = MiasColors.TextSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MiasColors.TextPrimary,
                unfocusedTextColor = MiasColors.TextPrimary,
            ),
        )
        SecretField(
            label = "Shared secret",
            value = tokenDraft,
            onValueChange = { tokenDraft = it },
        )
        Box(
            modifier = Modifier
                .clip(MiasShapes.Card)
                .background(MiasColors.Primary.copy(alpha = 0.22f))
                .clickable {
                    val portNum = portDraft.toIntOrNull() ?: MiasPrefs.DEFAULT_DESKTOP_PORT
                    onSave(hostDraft, portNum, tokenDraft)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Save",
                style = MiasTypography.LabelMedium,
                color = MiasColors.TextPrimary,
            )
        }
    }
}
