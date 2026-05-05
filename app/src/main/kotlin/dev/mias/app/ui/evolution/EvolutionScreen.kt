package dev.mias.app.ui.evolution

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.evolution.model.EvolutionSession
import dev.mias.core.evolution.model.EvolutionTaskType
import dev.mias.core.ui.glass.GlassCard
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasShapes
import dev.mias.core.ui.theme.MiasTypography

/**
 * EvolutionScreen — Mias's self-improvement dashboard.
 *
 * Shows the background learning toggle, current evolution status,
 * a "Run Now" button for immediate cycles, and a history of
 * completed evolution sessions with their outcomes.
 */
@Composable
fun EvolutionScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EvolutionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = 80.dp,
            ),
        ) {
            // ── Top bar ───────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MiasColors.TextPrimary,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Self-Evolution",
                            style = MiasTypography.TitleMedium,
                            color = MiasColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (state.isEvolutionRunning) "Evolving…" else "Dormant",
                            style = MiasTypography.LabelSmall,
                            color = if (state.isEvolutionRunning) MiasColors.NeonCyan else MiasColors.TextSecondary,
                        )
                    }

                    AnimatedVisibility(visible = state.isEvolutionRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MiasColors.NeonCyan,
                            strokeWidth = 2.dp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }

            // ── Background toggle card ─────────────────────────────────
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = MiasColors.NeonCyan,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Background Learning",
                                style = MiasTypography.BodyMedium,
                                color = MiasColors.TextPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Mias evolves silently while idle — every 6 hours",
                                style = MiasTypography.LabelSmall,
                                color = MiasColors.TextSecondary,
                            )
                        }
                        Switch(
                            checked = state.isBackgroundEnabled,
                            onCheckedChange = viewModel::toggleBackgroundEvolution,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MiasColors.Background,
                                checkedTrackColor = MiasColors.NeonCyan,
                                uncheckedThumbColor = MiasColors.TextSecondary,
                                uncheckedTrackColor = MiasColors.SurfaceGlass,
                            ),
                        )
                    }
                }
            }

            // ── Evolve Now button ─────────────────────────────────────────
            item {
                Button(
                    onClick = viewModel::runNow,
                    enabled = !state.isEvolutionRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MiasColors.NeonCyan.copy(alpha = 0.15f),
                        contentColor = MiasColors.NeonCyan,
                        disabledContainerColor = MiasColors.SurfaceGlass,
                        disabledContentColor = MiasColors.TextSecondary,
                    ),
                    shape = MiasShapes.Card,
                ) {
                    if (state.isEvolutionRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MiasColors.TextSecondary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Evolving…", style = MiasTypography.LabelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Evolve Now", style = MiasTypography.LabelMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Current session ───────────────────────────────────────────
            state.currentSession?.let { session ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Current Session",
                        style = MiasTypography.LabelMedium,
                        color = MiasColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    EvolutionSessionCard(session = session)
                }
            }

            // ── Empty state ───────────────────────────────────────────────
            if (state.currentSession == null && !state.isEvolutionRunning) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = MiasColors.TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No evolution cycles yet",
                                style = MiasTypography.TitleSmall,
                                color = MiasColors.TextSecondary.copy(alpha = 0.5f),
                            )
                            Text(
                                "Tap \"Evolve Now\" to start your first cycle",
                                style = MiasTypography.BodySmall,
                                color = MiasColors.TextSecondary.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MiasColors.SurfaceGlass,
                contentColor = MiasColors.TextPrimary,
                shape = MiasShapes.Card,
            )
        }
    }
}

@Composable
private fun EvolutionSessionCard(session: EvolutionSession) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (session.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                    contentDescription = null,
                    tint = if (session.isSuccess) MiasColors.NeonCyan else Color(0xFFFF6B6B),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (session.isSuccess) "Completed Successfully" else "Completed with errors",
                    style = MiasTypography.BodyMedium,
                    color = MiasColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(10.dp))

            session.completedTasks.forEach { taskType ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MiasColors.NeonCyan.copy(alpha = 0.6f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = taskType.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MiasTypography.LabelSmall,
                        color = MiasColors.TextSecondary,
                    )
                }
            }

            if (session.errors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                session.errors.forEach { error ->
                    Text(
                        text = "⚠ $error",
                        style = MiasTypography.LabelSmall,
                        color = Color(0xFFFF6B6B),
                    )
                }
            }
        }
    }
}
