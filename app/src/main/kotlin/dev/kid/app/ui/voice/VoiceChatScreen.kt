package dev.kid.app.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kid.core.common.model.CognitionState
import dev.kid.core.ui.components.AnimatedOrb
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidTypography

/**
 * VoiceChatScreen — Full-screen voice-only interface for Mias.
 *
 * Layout:
 * - Top: back arrow navigation
 * - Center: AnimatedOrb (pulsing when listening, brighter when processing)
 * - Bottom third: live transcript + AI response (scrollable)
 * - Bottom: large circular mic toggle button
 *
 * No keyboard, no text input bar — voice only.
 */
@Composable
fun VoiceChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceChatViewModel = hiltViewModel(),
) {
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val aiResponse by viewModel.aiResponse.collectAsStateWithLifecycle()

    val isProcessing = aiResponse == "Thinking..."

    // Pulsing animation for mic button when listening
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mic_pulse_scale",
    )

    // Determine orb cognition state based on voice state
    val orbCognitionState = when {
        isProcessing -> CognitionState.THINKING
        isListening -> CognitionState.ACTING
        else -> CognitionState.IDLE
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
            // ── Top bar: back arrow ──────────────────────────────────────
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = KidColors.TextPrimary,
                )
            }

            // ── Center: Orb + status ────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Status label
                Text(
                    text = when {
                        isProcessing -> "Processing…"
                        isListening -> "Listening…"
                        else -> "Tap the mic to speak"
                    },
                    style = KidTypography.LabelMedium,
                    color = when {
                        isProcessing -> KidColors.NeonCyan
                        isListening -> KidColors.CognitionActing
                        else -> KidColors.TextSecondary
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // The Orb — larger when listening, brighter when processing
                AnimatedOrb(
                    cognitionState = orbCognitionState,
                    size = if (isListening) 200.dp else 160.dp,
                    modifier = Modifier.size(if (isListening) 200.dp else 160.dp),
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Bottom third: transcript + response ─────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(horizontal = 24.dp),
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                ) {
                    // User transcript
                    AnimatedVisibility(
                        visible = transcript.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Column {
                            Text(
                                text = "You",
                                style = KidTypography.LabelSmall,
                                color = KidColors.TextTertiary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = transcript,
                                style = KidTypography.BodyLarge,
                                color = KidColors.TextPrimary,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // AI response
                    AnimatedVisibility(
                        visible = aiResponse.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Column {
                            Text(
                                text = "Mias",
                                style = KidTypography.LabelSmall,
                                color = KidColors.NeonCyan.copy(alpha = 0.7f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiResponse,
                                style = KidTypography.BodyLarge,
                                color = KidColors.TextSecondary,
                            )
                        }
                    }

                    // Empty state
                    if (transcript.isBlank() && aiResponse.isBlank()) {
                        Text(
                            text = "Start speaking and your conversation will appear here",
                            style = KidTypography.BodyMedium,
                            color = KidColors.TextTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        )
                    }
                }
            }

            // ── Bottom: Mic button ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(if (isListening) micPulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            brush = if (isListening) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        KidColors.NeonCyan,
                                        KidColors.NeonCyan.copy(alpha = 0.6f),
                                    ),
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        KidColors.SurfaceElevated,
                                        KidColors.SurfaceGlass,
                                    ),
                                )
                            },
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isListening) KidColors.NeonCyan else KidColors.GlassBorder,
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            viewModel.toggleDeafMute()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = if (isListening) "Stop listening" else "Start listening",
                        tint = if (isListening) Color.Black else KidColors.TextPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
