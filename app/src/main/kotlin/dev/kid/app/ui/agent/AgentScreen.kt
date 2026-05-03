package dev.kid.app.ui.agent

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Psychology
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
import dev.kid.core.ui.components.AgentFeed
import dev.kid.core.ui.glass.GlassCard
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidShapes
import dev.kid.core.ui.theme.KidTypography

/**
 * AgentScreen — Mias autonomous work feed.
 *
 * Shows live tool activity, lets the user manually trigger tools,
 * and displays a scrolling feed of completed agent tasks.
 */
@Composable
fun AgentScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
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
            // ── Top bar ───────────────────────────────────────────────────
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
                        tint = KidColors.TextPrimary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Agent Feed",
                        style = KidTypography.TitleMedium,
                        color = KidColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val status = state.status
                    Text(
                        text = if (status.isRunning) "Running: ${status.currentTool}" else "Idle · ${state.availableTools.size} tools ready",
                        style = KidTypography.LabelSmall,
                        color = if (status.isRunning) KidColors.NeonCyan else KidColors.TextSecondary,
                    )
                }

                // Status indicator dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.status.isRunning) KidColors.NeonCyan
                            else KidColors.TextSecondary.copy(alpha = 0.4f),
                        ),
                )
                Spacer(Modifier.width(12.dp))
            }

            // ── Tool selector chips ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.availableTools.forEach { tool ->
                    val selected = state.selectedTool == tool
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) KidColors.NeonCyan.copy(alpha = 0.15f)
                                else KidColors.SurfaceGlass,
                            )
                            .border(
                                width = if (selected) 1.dp else 0.5.dp,
                                color = if (selected) KidColors.NeonCyan else KidColors.TextSecondary.copy(0.2f),
                                shape = CircleShape,
                            )
                            .clickable { viewModel.onToolSelected(if (selected) null else tool) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = tool,
                            style = KidTypography.LabelSmall,
                            color = if (selected) KidColors.NeonCyan else KidColors.TextSecondary,
                        )
                    }
                }
            }

            // ── Manual input ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.selectedTool != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = KidColors.NeonCyan,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = state.manualInput,
                            onValueChange = viewModel::onManualInput,
                            textStyle = KidTypography.BodyMedium.copy(color = KidColors.TextPrimary),
                            cursorBrush = SolidColor(KidColors.NeonCyan),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (state.manualInput.isEmpty()) {
                                    Text(
                                        "Input for ${state.selectedTool}…",
                                        style = KidTypography.BodyMedium,
                                        color = KidColors.TextSecondary,
                                    )
                                }
                                inner()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = viewModel::executeManualTask,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Execute",
                                tint = KidColors.NeonCyan,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // ── Agent activity feed ───────────────────────────────────────
            if (state.recentResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = KidColors.TextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No tasks yet",
                            style = KidTypography.TitleSmall,
                            color = KidColors.TextSecondary.copy(alpha = 0.5f),
                        )
                        Text(
                            "Select a tool above and give me a task",
                            style = KidTypography.BodySmall,
                            color = KidColors.TextSecondary.copy(alpha = 0.35f),
                        )
                    }
                }
            } else {
                AgentFeed(
                    results = state.recentResults,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                )
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
                containerColor = KidColors.SurfaceGlass,
                contentColor = KidColors.TextPrimary,
                shape = KidShapes.Card,
            )
        }
    }
}
