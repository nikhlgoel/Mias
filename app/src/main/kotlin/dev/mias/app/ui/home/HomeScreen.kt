package dev.mias.app.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.ui.components.AnimatedOrb
import dev.mias.core.ui.components.StatusPill
import dev.mias.core.ui.glass.CognitionGlow
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasTypography

@Composable
fun HomeScreen(
    onNavigateToChat: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToModelHub: () -> Unit = {},
    onNavigateToChats: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Background),
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
                Row {
                    IconButton(onClick = onNavigateToChats) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Chats",
                            tint = MiasColors.TextSecondary,
                        )
                    }
                    IconButton(onClick = onNavigateToModelHub) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = "Models",
                            tint = MiasColors.TextSecondary,
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MiasColors.TextSecondary,
                        )
                    }
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
                        style = MiasTypography.DisplayMedium,
                        color = MiasColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.subtitle,
                    style = MiasTypography.BodyLarge,
                    color = MiasColors.TextSecondary,
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

                // Quick entry points — keep the surface focused on what
                // actually works today (voice and text). Vision/video will
                // join here once that pipeline is wired end-to-end.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onNavigateToVoice,
                        label = { Text("Speak") },
                        leadingIcon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MiasColors.SurfaceGlassStroke.copy(alpha = 0.2f),
                            labelColor = MiasColors.TextPrimary,
                            leadingIconContentColor = MiasColors.TextSecondary,
                        ),
                        border = BorderStroke(1.dp, MiasColors.GlassBorder),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    AssistChip(
                        onClick = { onNavigateToChat(null) },
                        label = { Text("Type") },
                        leadingIcon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MiasColors.SurfaceGlassStroke.copy(alpha = 0.2f),
                            labelColor = MiasColors.TextPrimary,
                            leadingIconContentColor = MiasColors.TextSecondary,
                        ),
                        border = BorderStroke(1.dp, MiasColors.GlassBorder),
                    )
                }
            }

        }
    }
}
