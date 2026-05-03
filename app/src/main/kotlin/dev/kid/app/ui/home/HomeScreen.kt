package dev.kid.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kid.core.ui.components.AnimatedOrb
import dev.kid.core.ui.components.NudgeCard
import dev.kid.core.ui.components.StatusPill
import dev.kid.core.ui.glass.CognitionGlow
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidTypography

@Composable
fun HomeScreen(
    onNavigateToChat: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToModelHub: () -> Unit = {},
    onNavigateToAgent: () -> Unit = {},
    onNavigateToEvolution: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KidColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp),
        ) {
            // ── Top Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    brainState = state.brainState,
                    cognitionState = state.cognitionState,
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = KidColors.TextSecondary,
                    )
                }
                IconButton(onClick = onNavigateToModelHub) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = "Model Hub",
                        tint = KidColors.TextSecondary,
                    )
                }
                IconButton(onClick = onNavigateToAgent) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = "Agent",
                        tint = KidColors.TextSecondary,
                    )
                }
                IconButton(onClick = onNavigateToEvolution) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = "Evolution",
                        tint = KidColors.TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Central Orb Area ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Greeting
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                    ),
                ) {
                    Text(
                        text = state.greeting,
                        style = KidTypography.DisplayMedium,
                        color = KidColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.subtitle,
                    style = KidTypography.BodyLarge,
                    color = KidColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(40.dp))

                // The Orb — tap to start conversation
                CognitionGlow(
                    cognitionState = state.cognitionState,
                    intensity = 0.5f,
                ) {
                    AnimatedOrb(
                        cognitionState = state.cognitionState,
                        size = 160.dp,
                        modifier = Modifier
                            .size(160.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onNavigateToChat(null)
                            },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // Multimodal Suggestion Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = onNavigateToVoice,
                        label = { Text("Voice") },
                        leadingIcon = { Icon(Icons.Rounded.Mic, contentDescription = "Voice") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = KidColors.SurfaceGlassStroke.copy(alpha = 0.2f),
                            labelColor = KidColors.TextPrimary,
                            leadingIconContentColor = KidColors.TextSecondary
                        ),
                        border = BorderStroke(1.dp, KidColors.GlassBorder)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    AssistChip(
                        onClick = { onNavigateToChat(null) },
                        label = { Text("Video") },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, contentDescription = "Video") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = KidColors.SurfaceGlassStroke.copy(alpha = 0.2f),
                            labelColor = KidColors.TextPrimary,
                            leadingIconContentColor = KidColors.TextSecondary
                        ),
                        border = BorderStroke(1.dp, KidColors.GlassBorder)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    AssistChip(
                        onClick = { onNavigateToChat(null) },
                        label = { Text("Chat") },
                        leadingIcon = { Icon(Icons.Rounded.Keyboard, contentDescription = "Chat") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = KidColors.SurfaceGlassStroke.copy(alpha = 0.2f),
                            labelColor = KidColors.TextPrimary,
                            leadingIconContentColor = KidColors.TextSecondary
                        ),
                        border = BorderStroke(1.dp, KidColors.GlassBorder)
                    )
                }
            }

            // ── Nudges Area ──
            if (state.nudges.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = state.nudges,
                        key = { it.id },
                    ) { nudge ->
                        NudgeCard(
                            nudge = nudge,
                            onClick = { viewModel.dismissNudge(nudge.id) },
                        )
                    }
                }
            }

        }
    }
}
