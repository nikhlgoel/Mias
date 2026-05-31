package dev.mias.app.ui.modelhub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.modelhub.model.DownloadStatus
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.ui.components.ModelCard
import dev.mias.core.ui.components.formatSize
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun ModelHubScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MiasColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Models", style = MiasTypography.TitleMedium, color = MiasColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.installedModels.size} installed · ${formatSize(state.storageUsedBytes)} used",
                        style = MiasTypography.LabelSmall, color = MiasColors.TextSecondary,
                    )
                }
                IconButton(onClick = viewModel::autoAssignRoles) {
                    Icon(Icons.Rounded.AutoFixHigh, "Auto-assign roles", tint = MiasColors.NeonCyan)
                }
            }

            // Search bar
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = MiasColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = state.activeSearchQuery,
                        onValueChange = viewModel::onSearchQuery,
                        textStyle = MiasTypography.BodyMedium.copy(color = MiasColors.TextPrimary),
                        cursorBrush = SolidColor(MiasColors.NeonCyan),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (state.activeSearchQuery.isEmpty()) Text("Search for a model", style = MiasTypography.BodyMedium, color = MiasColors.TextSecondary)
                            inner()
                        },
                    )
                }
            }

            // Role filter chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoleChip("All", state.selectedRole == null) { viewModel.onRoleFilter(null) }
                ModelRole.entries.forEach { role ->
                    RoleChip(
                        role.name.lowercase().replaceFirstChar { it.uppercase() },
                        state.selectedRole == role,
                    ) { viewModel.onRoleFilter(role) }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Installed models
            var pendingDeleteId by remember { mutableStateOf<String?>(null) }
            var pendingDeleteName by remember { mutableStateOf("") }

            AnimatedVisibility(visible = state.installedModels.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Column {
                    SectionLabel("Installed")
                    state.installedModels.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModelCard(
                                modelCard = model.card,
                                isActive = true,
                                downloadState = state.downloadStates[model.id],
                                onAction = { /* tap on installed = no-op; delete via the trash icon */ },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    pendingDeleteId = model.id
                                    pendingDeleteName = model.card.name
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete ${model.card.name}",
                                    tint = MiasColors.TextSecondary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (pendingDeleteId != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteId = null },
                    title = { Text("Remove $pendingDeleteName?") },
                    text = { Text("This model will be removed from your device. You can download it again at any time.") },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingDeleteId?.let(viewModel::deleteModel)
                            pendingDeleteId = null
                        }) { Text("Remove") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
                    },
                )
            }

            SectionLabel("Available")

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                items(state.catalogItems, key = { it.card.id }) { item ->
                    val dlState = state.downloadStates[item.card.id]
                    ModelCard(
                        modelCard = item.card,
                        downloadState = dlState,
                        isActive = item.isInstalled,
                        onAction = {
                            when {
                                item.isInstalled -> {}
                                dlState?.status == DownloadStatus.DOWNLOADING -> viewModel.pauseDownload(item.card.id)
                                dlState?.status == DownloadStatus.PAUSED -> viewModel.resumeDownload(item.card.id)
                                else -> viewModel.downloadModel(item.card)
                            }
                        },
                    )
                }

                if (state.isSearchingRemote) {
                    item(key = "remote-loading") {
                        Text(
                            text = "Searching Hugging Face…",
                            style = MiasTypography.LabelSmall,
                            color = MiasColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }

                item(key = "kind-filter") {
                    HuggingFaceKindFilter(
                        active = state.searchKind,
                        onPick = viewModel::onSearchKind,
                    )
                }

                if (state.remoteResults.isNotEmpty()) {
                    item(key = "remote-header") {
                        Text(
                            text = "From Hugging Face",
                            style = MiasTypography.LabelMedium,
                            color = MiasColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    items(state.remoteResults, key = { "hf-${it.id}" }) { card ->
                        val dlState = state.downloadStates[card.id]
                        ModelCard(
                            modelCard = card,
                            downloadState = dlState,
                            isActive = false,
                            onAction = {
                                when {
                                    dlState?.status == DownloadStatus.DOWNLOADING -> viewModel.pauseDownload(card.id)
                                    dlState?.status == DownloadStatus.PAUSED -> viewModel.resumeDownload(card.id)
                                    else -> viewModel.downloadModel(card)
                                }
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        ) { data ->
            Snackbar(data, containerColor = MiasColors.SurfaceGlass, contentColor = MiasColors.TextPrimary, shape = MiasShapes.Card)
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = MiasTypography.LabelMedium, color = MiasColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun HuggingFaceKindFilter(
    active: dev.mias.core.modelhub.registry.HuggingFaceRegistry.Kind,
    onPick: (dev.mias.core.modelhub.registry.HuggingFaceRegistry.Kind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KindChip(
            label = "Text (GGUF)",
            selected = active == dev.mias.core.modelhub.registry.HuggingFaceRegistry.Kind.GGUF,
            onClick = { onPick(dev.mias.core.modelhub.registry.HuggingFaceRegistry.Kind.GGUF) },
        )
        KindChip(
            label = "Vision (.task)",
            selected = active == dev.mias.core.modelhub.registry.HuggingFaceRegistry.Kind.TASK,
            onClick = { onPick(dev.mias.core.modelhub.registry.HuggingFaceRegistry.Kind.TASK) },
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MiasColors.Heather.copy(alpha = 0.22f) else MiasColors.Surface2)
            .border(
                1.dp,
                if (selected) MiasColors.Heather else MiasColors.OutlineSoft,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MiasTypography.LabelMedium,
            color = if (selected) MiasColors.TextHi else MiasColors.TextLo,
        )
    }
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MiasColors.NeonCyan.copy(alpha = 0.15f) else MiasColors.SurfaceGlass)
            .border(if (selected) 1.dp else 0.5.dp, if (selected) MiasColors.NeonCyan else MiasColors.TextSecondary.copy(0.2f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MiasTypography.LabelSmall,
            color = if (selected) MiasColors.NeonCyan else MiasColors.TextSecondary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
