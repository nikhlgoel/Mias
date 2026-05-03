package dev.kid.app.ui.modelhub

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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.kid.core.modelhub.model.DownloadStatus
import dev.kid.core.modelhub.model.ModelRole
import dev.kid.core.ui.components.ModelCard
import dev.kid.core.ui.components.formatSize
import dev.kid.core.ui.glass.GlassCard
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes
import dev.kid.core.ui.theme.KidTypography

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
            .background(KidColors.Background),
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = KidColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Brain Market", style = KidTypography.TitleMedium, color = KidColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.installedModels.size} installed · ${formatSize(state.storageUsedBytes)} used",
                        style = KidTypography.LabelSmall, color = KidColors.TextSecondary,
                    )
                }
                IconButton(onClick = viewModel::autoAssignRoles) {
                    Icon(Icons.Rounded.AutoFixHigh, "Auto-assign roles", tint = KidColors.NeonCyan)
                }
            }

            // Search bar
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = KidColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = state.activeSearchQuery,
                        onValueChange = viewModel::onSearchQuery,
                        textStyle = KidTypography.BodyMedium.copy(color = KidColors.TextPrimary),
                        cursorBrush = SolidColor(KidColors.NeonCyan),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (state.activeSearchQuery.isEmpty()) Text("Search models...", style = KidTypography.BodyMedium, color = KidColors.TextSecondary)
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
            AnimatedVisibility(visible = state.installedModels.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Column {
                    SectionLabel("Installed Brains")
                    state.installedModels.forEach { model ->
                        ModelCard(
                            modelCard = model.card,
                            isActive = true,
                            downloadState = state.downloadStates[model.id],
                            onAction = { viewModel.deleteModel(model.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionLabel("Available Brains")

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
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        ) { data ->
            Snackbar(data, containerColor = KidColors.SurfaceGlass, contentColor = KidColors.TextPrimary, shape = KidShapes.Card)
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = KidTypography.LabelMedium, color = KidColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) KidColors.NeonCyan.copy(alpha = 0.15f) else KidColors.SurfaceGlass)
            .border(if (selected) 1.dp else 0.5.dp, if (selected) KidColors.NeonCyan else KidColors.TextSecondary.copy(0.2f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = KidTypography.LabelSmall,
            color = if (selected) KidColors.NeonCyan else KidColors.TextSecondary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
