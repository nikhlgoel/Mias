package dev.mias.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.app.ui.home.ModernActionChip
import dev.mias.core.common.model.CognitionState
import dev.mias.core.ui.components.BubbleType
import dev.mias.core.ui.components.MessageBubble
import dev.mias.core.ui.components.ModernThinkingDots
import dev.mias.core.ui.components.SpeechButton
import dev.mias.core.ui.components.StatusPill
import dev.mias.app.ui.chat.ChatEvent
import dev.mias.app.ui.chat.ChatUiState
import dev.mias.core.speech.SpeechState
import dev.mias.core.speech.SpeechViewModel
import dev.mias.core.ui.components.ModernInputBar
import dev.mias.core.ui.components.ModernSendButton
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasTypography

/**
 * Modern Chat Screen with upgraded UI.
 *
 * Features:
 * - Glassmorphism message bubbles
 * - Animated orb with cognition glow
 * - Modern input bar with voice integration
 * - Smooth animations for message appearance
 * - Real-time processing indicators
 */
@Composable
fun ModernChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
    speechViewModel: SpeechViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val speechState by speechViewModel.isListening.collectAsStateWithLifecycle()
    val transcription by speechViewModel.transcription.collectAsStateWithLifecycle()
    val confidence by speechViewModel.confidence.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(transcription) {
        if (transcription.isNotBlank()) {
            viewModel.applyTranscription(transcription)
        }
    }

    // Auto-scroll on new messages
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.ScrollToBottom -> {
                    val size = state.messages.size
                    if (size > 0) {
                        listState.animateScrollToItem(size - 1)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiasColors.Background),
    ) {
        // ── Top Bar ──
        ModernChatTopBar(
            state = state,
            onBack = onNavigateBack,
            onToggleReAct = viewModel::toggleReActSteps,
        )

        // ── Messages Area ──
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                ModernEmptyConversation(
                    cognitionState = state.cognitionState,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(
                        items = state.messages,
                        key = { it.id },
                    ) { message ->
                        MessageBubble(
                            text = message.text,
                            type = message.type,
                            timestamp = message.timestamp,
                            isStreaming = message.isStreaming,
                        )
                    }

                    // Thinking indicator
                    if (state.isProcessing) {
                        item {
                            ModernProcessingIndicator(
                                cognitionState = state.cognitionState,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }

        // ── Input Bar ──
        ModernChatInputBar(
            text = state.inputText,
            onTextChange = viewModel::onInputChange,
            onSend = viewModel::onSend,
            isProcessing = state.isProcessing,
            speechState = if (speechState) SpeechState.LISTENING else SpeechState.IDLE,
            confidence = confidence,
            transcription = transcription,
            onStartListening = { speechViewModel.startListening() },
            onStopListening = { speechViewModel.stopListening() },
        )
    }
}

@Composable
private fun ModernChatTopBar(
    state: ChatUiState,
    onBack: () -> Unit,
    onToggleReAct: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MiasColors.TextPrimary,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        StatusPill(
            brainState = state.brainState,
            cognitionState = state.cognitionState,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Toggle ReAct step visibility
        IconButton(onClick = onToggleReAct) {
            Icon(
                imageVector = if (state.showReActSteps) {
                    Icons.Rounded.Visibility
                } else {
                    Icons.Rounded.VisibilityOff
                },
                contentDescription = "Toggle thinking steps",
                tint = MiasColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ModernChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isProcessing: Boolean,
    speechState: SpeechState,
    confidence: Float,
    transcription: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Input field
        Box(modifier = Modifier.weight(1f)) {
            ModernInputBar(
                text = text,
                onTextChange = onTextChange,
                onSend = onSend,
                enabled = !isProcessing,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Voice button
        SpeechButton(
            state = speechState,
            confidence = confidence,
            transcription = transcription,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Send button
        ModernSendButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isProcessing,
        )
    }
}

@Composable
private fun ModernEmptyConversation(
    cognitionState: CognitionState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "What's on your mind?",
            style = MiasTypography.HeadlineMedium,
            color = MiasColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Everything stays on this device.\nPrivate. Always.",
            style = MiasTypography.BodyMedium,
            color = MiasColors.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ModernProcessingIndicator(
    cognitionState: CognitionState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModernThinkingDots(
            color = when (cognitionState) {
                CognitionState.THINKING -> MiasColors.CognitionThinking
                CognitionState.ACTING -> MiasColors.CognitionActing
                CognitionState.OFFLOADING -> MiasColors.CognitionOffloading
                else -> MiasColors.CognitionIdle
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when (cognitionState) {
                CognitionState.THINKING -> "Thinking..."
                CognitionState.ACTING -> "Taking action..."
                CognitionState.WAITING -> "Waiting for result..."
                CognitionState.OFFLOADING -> "Asking desktop..."
                else -> "Processing..."
            },
            style = MiasTypography.LabelMedium,
            color = MiasColors.TextSecondary,
        )
    }
}
