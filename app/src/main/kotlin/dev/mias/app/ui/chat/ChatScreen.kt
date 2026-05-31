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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.ui.theme.MiasShapes
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mias.core.common.model.CognitionState
import dev.mias.core.ui.components.AnimatedOrb
import dev.mias.core.ui.components.BubbleType
import dev.mias.core.ui.components.MiasInputBar
import dev.mias.core.ui.components.MessageBubble
import dev.mias.core.ui.components.SpeechButton
import dev.mias.core.ui.components.StatusPill
import dev.mias.core.ui.components.ThinkingDots
import dev.mias.core.ui.theme.MiasColors
import dev.mias.core.ui.theme.MiasTypography
import dev.mias.core.speech.SpeechState
import dev.mias.core.speech.SpeechViewModel

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
            .background(MiasColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ── Top Bar ──
        ChatTopBar(
            state = state,
            onBack = onNavigateBack,
            onToggleReAct = viewModel::toggleReActSteps,
            onSelectModel = viewModel::selectChatModel,
        )

        // ── Messages Area ──
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                EmptyConversation(
                    cognitionState = state.cognitionState,
                    onSuggestion = viewModel::useSuggestion,
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
                    MiasInputBar(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    state: ChatUiState,
    onBack: () -> Unit,
    onToggleReAct: () -> Unit,
    onSelectModel: (String) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val activeModel = state.chatModels.firstOrNull { it.id == state.activeChatModelId }
        ?: state.chatModels.firstOrNull()

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

        Spacer(modifier = Modifier.width(4.dp))

        StatusPill(
            brainState = state.brainState,
            cognitionState = state.cognitionState,
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (state.chatModels.isNotEmpty()) {
            ModelChip(
                label = activeModel?.card?.name ?: "Pick a model",
                onClick = { pickerOpen = true },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onToggleReAct) {
            Icon(
                imageVector = if (state.showReActSteps) Icons.Rounded.Visibility
                else Icons.Rounded.VisibilityOff,
                contentDescription = "Toggle thinking steps",
                tint = MiasColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            sheetState = sheetState,
            containerColor = MiasColors.Background,
        ) {
            ChatModelPicker(
                models = state.chatModels,
                activeId = state.activeChatModelId ?: activeModel?.id,
                onPick = { id ->
                    onSelectModel(id)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        pickerOpen = false
                    }
                },
            )
        }
    }
}

@Composable
private fun ModelChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MiasShapes.Full)
            .background(MiasColors.SurfaceGlass)
            .border(1.dp, MiasColors.GlassBorder, MiasShapes.Full)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Memory,
            contentDescription = null,
            tint = MiasColors.TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MiasTypography.LabelSmall,
            color = MiasColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MiasColors.TextSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ChatModelPicker(
    models: List<InstalledModel>,
    activeId: String?,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = "Use this model for chat",
            style = MiasTypography.LabelLarge,
            color = MiasColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        models.forEach { model ->
            val isActive = model.id == activeId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MiasShapes.Card)
                    .clickable { onPick(model.id) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.card.name,
                        style = MiasTypography.LabelLarge,
                        color = MiasColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${model.card.parameterCount} · ${model.card.quantization}",
                        style = MiasTypography.LabelSmall,
                        color = MiasColors.TextSecondary,
                    )
                }
                if (isActive) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Active",
                        tint = MiasColors.CognitionActing,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyConversation(
    cognitionState: CognitionState,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedOrb(
            cognitionState = cognitionState,
            size = 80.dp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "How can I help today?",
            style = MiasTypography.HeadlineMedium,
            color = MiasColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This conversation stays on your device.",
            style = MiasTypography.BodyMedium,
            color = MiasColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        ) {
            items(items = SUGGESTIONS, key = { it.label }) { suggestion ->
                SuggestionChip(
                    label = suggestion.label,
                    onClick = { onSuggestion(suggestion.prompt) },
                )
            }
        }
    }
}

private data class Suggestion(val label: String, val prompt: String)

private val SUGGESTIONS = listOf(
    Suggestion("Summarize a note", "Summarize this text for me: "),
    Suggestion("Plan my day", "Help me plan a productive day. Ask me what I'm working on first."),
    Suggestion("Explain something", "Explain "),
    Suggestion("Brainstorm ideas", "Help me brainstorm ideas for "),
)

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(MiasColors.SurfaceGlass)
            .border(1.dp, MiasColors.GlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MiasTypography.LabelMedium,
            color = MiasColors.TextPrimary,
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
                    CognitionState.THINKING -> "Thinking"
                    CognitionState.ACTING -> "Taking action"
                    CognitionState.WAITING -> "Reviewing the result"
                    CognitionState.OFFLOADING -> "Reaching the desktop"
                    else -> "Working on it"
                },
                style = MiasTypography.LabelMedium,
                color = MiasColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            ThinkingDots(color = MiasColors.Primary, dotSize = 4.dp)
        }
    }
}
