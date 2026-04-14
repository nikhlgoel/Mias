package dev.kid.app.ui.chat

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import dev.kid.core.common.model.CognitionState
import dev.kid.core.ui.components.AnimatedOrb
import dev.kid.core.ui.components.BubbleType
import dev.kid.core.ui.components.KidInputBar
import dev.kid.core.ui.components.MessageBubble
import dev.kid.core.ui.components.SpeechButton
import dev.kid.core.ui.components.StatusPill
import dev.kid.core.ui.components.ThinkingDots
import dev.kid.core.ui.theme.KidColors
import dev.kid.core.ui.theme.KidTypography
import dev.kid.core.speech.SpeechState
import dev.kid.core.speech.SpeechViewModel

@Composable
fun ChatScreen(
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
            .background(KidColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ── Top Bar ──
        ChatTopBar(
            state = state,
            onBack = onNavigateBack,
            onToggleReAct = viewModel::toggleReActSteps,
        )

        // ── Messages Area ──
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                EmptyConversation(
                    cognitionState = state.cognitionState,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(
                        items = state.messages,
                        key = { it.id },
                    ) { message ->
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                initialOffsetY = { it / 2 },
                            ) + fadeIn(),
                        ) {
                            MessageBubble(
                                text = message.text,
                                type = message.type,
                                timestamp = message.timestamp,
                                isStreaming = message.isStreaming,
                            )
                        }
                    }

                    // Thinking indicator
                    if (state.isProcessing) {
                        item {
                            ProcessingIndicator(
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    KidInputBar(
                        value = state.inputText,
                        onValueChange = viewModel::onInputChange,
                        onSend = viewModel::onSend,
                        isProcessing = state.isProcessing,
                        enabled = !state.isProcessing,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SpeechButton(
                    state = if (speechState) SpeechState.LISTENING else SpeechState.IDLE,
                    confidence = confidence,
                    transcription = transcription,
                    onStartListening = { speechViewModel.startListening() },
                    onStopListening = { speechViewModel.stopListening() },
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(
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
                tint = KidColors.TextPrimary,
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
                tint = KidColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyConversation(
    cognitionState: CognitionState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedOrb(
            cognitionState = cognitionState,
            size = 80.dp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "What's on your mind?",
            style = KidTypography.HeadlineMedium,
            color = KidColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Everything stays on this device.\nPrivate. Always.",
            style = KidTypography.BodyMedium,
            color = KidColors.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ProcessingIndicator(
    cognitionState: CognitionState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedOrb(
            cognitionState = cognitionState,
            size = 24.dp,
            breathingEnabled = true,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = when (cognitionState) {
                    CognitionState.THINKING -> "Thinking..."
                    CognitionState.ACTING -> "Taking action..."
                    CognitionState.WAITING -> "Waiting for result..."
                    CognitionState.OFFLOADING -> "Asking desktop..."
                    else -> "Processing..."
                },
                style = KidTypography.LabelMedium,
                color = KidColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            ThinkingDots(color = KidColors.Primary, dotSize = 4.dp)
        }
    }
}
