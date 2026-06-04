package dev.mias.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.ui.theme.MiasShapes
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val shareContext = LocalContext.current

    val shareConversation: () -> Unit = {
        val transcript = state.messages
            .filter { it.type == BubbleType.USER || it.type == BubbleType.Mias }
            .joinToString("\n\n") { m ->
                val who = if (m.type == BubbleType.USER) "You" else "Mias"
                "$who: ${m.text}"
            }
        if (transcript.isNotBlank()) {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, transcript)
                putExtra(android.content.Intent.EXTRA_SUBJECT, state.conversationTitle)
            }
            runCatching {
                shareContext.startActivity(
                    android.content.Intent.createChooser(send, "Share conversation"),
                )
            }
        }
    }

    val copyMessage: (String) -> Unit = remember(clipboard, haptics) {
        { text ->
            if (text.isNotBlank()) {
                clipboard.setText(AnnotatedString(text))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    // Show a jump-to-latest button only when the user has scrolled up away
    // from the newest message.
    val showScrollToBottom by remember {
        derivedStateOf { listState.canScrollForward }
    }

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
            onSelectPersona = viewModel::selectPersona,
            onShare = shareConversation,
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
                                image = message.image,
                                reasoning = message.reasoning,
                                onLongPress = { copyMessage(message.text) },
                            )
                        }
                    }

                    // Pre-stream loader only — once a streaming bubble exists,
                    // its in-bubble "Thinking…" box carries the activity, so we
                    // don't leave a second loader hanging below the message.
                    val hasStreamingBubble = state.messages.lastOrNull()?.isStreaming == true
                    if (state.isProcessing && !hasStreamingBubble) {
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

            // Jump-to-latest button, bottom-right of the message list.
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToBottom,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MiasColors.Surface3)
                        .border(1.dp, MiasColors.OutlineSoft, CircleShape)
                        .clickable {
                            scope.launch {
                                val size = state.messages.size
                                if (size > 0) listState.animateScrollToItem(size - 1)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Jump to latest",
                        tint = MiasColors.TextLo,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // ── Attachment launchers ──
        val ctx = LocalContext.current
        val pickImage = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> viewModel.attachImage(uri) }
        val takePhoto = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview(),
        ) { bitmap -> viewModel.attachBitmap(bitmap) }
        val requestCameraPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> if (granted) takePhoto.launch(null) }
        val pickDocument = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) viewModel.attachDocument(uri) }
        var attachSheetOpen by remember { mutableStateOf(false) }

        if (attachSheetOpen) {
            ComposerSheet(
                selectedSkill = state.forcedSkill,
                onDismiss = { attachSheetOpen = false },
                onPickGallery = {
                    attachSheetOpen = false
                    pickImage.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onCapture = {
                    attachSheetOpen = false
                    val granted = ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) takePhoto.launch(null)
                    else requestCameraPermission.launch(Manifest.permission.CAMERA)
                },
                onPickDocument = {
                    attachSheetOpen = false
                    pickDocument.launch(
                        arrayOf("application/pdf", "text/plain", "text/markdown", "text/*"),
                    )
                },
                onSelectSkill = { skill ->
                    viewModel.setForcedSkill(skill)
                    attachSheetOpen = false
                },
            )
        }

        // ── Input Bar ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Single inset = max(ime, navbar) per side. Stacking them as two
                // separate paddings added the keyboard height twice, floating the
                // bar far above the keyboard. union() takes whichever is larger:
                // ime while typing, navbar otherwise.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Contextual "Regenerate" — only when idle and the last turn was an
            // assistant reply the user might want re-rolled.
            val canRegenerate = !state.isProcessing &&
                state.messages.lastOrNull()?.type == BubbleType.Mias
            androidx.compose.animation.AnimatedVisibility(visible = canRegenerate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(MiasShapes.Full)
                            .background(MiasColors.Surface3)
                            .border(1.dp, MiasColors.OutlineSoft, MiasShapes.Full)
                            .clickable { viewModel.regenerate() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = MiasColors.TextLo,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Regenerate",
                            style = MiasTypography.LabelMedium,
                            color = MiasColors.TextLo,
                        )
                    }
                }
            }
            // Transient banner after attaching a document from the composer.
            state.attachNotice?.let { notice ->
                LaunchedEffect(notice) {
                    kotlinx.coroutines.delay(2600)
                    viewModel.clearAttachNotice()
                }
                Text(
                    text = notice,
                    style = MiasTypography.LabelMedium,
                    color = MiasColors.Heather,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                )
            }
            // Active skill chip (set from the "+" menu). Tap to clear → Auto.
            if (state.forcedSkill != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(MiasShapes.Full)
                        .background(MiasColors.HeatherContainer)
                        .clickable { viewModel.setForcedSkill(null) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MiasColors.Heather,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = skillLabel(state.forcedSkill!!),
                        style = MiasTypography.LabelMedium,
                        color = MiasColors.TextHi,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear skill",
                        tint = MiasColors.TextLo,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (state.attachedImage != null) {
                AttachedImageStrip(
                    image = state.attachedImage!!,
                    onRemove = viewModel::clearAttachment,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
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
                        onStop = viewModel::stopGeneration,
                        // Always available — photos, files, and skills.
                        onAttach = { attachSheetOpen = true },
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SpeechButton(
                    state = if (speechState) SpeechState.LISTENING else SpeechState.IDLE,
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
    onSelectPersona: (String) -> Unit = {},
    onShare: () -> Unit = {},
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var personaSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val personaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val activeModel = state.chatModels.firstOrNull { it.id == state.activeChatModelId }
        ?: state.chatModels.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
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

            if (state.ragActive) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = "Answering from your documents",
                    tint = MiasColors.Heather,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(18.dp),
                )
            }

            IconButton(onClick = { personaSheetOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.Face,
                    contentDescription = "Choose persona",
                    tint = MiasColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (state.messages.isNotEmpty()) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share conversation",
                        tint = MiasColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

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

        // Conversation title caption. Only shown once the chat has content and
        // carries a real title — the "New Conversation" placeholder stays hidden
        // so the empty state isn't labelled.
        if (state.messages.isNotEmpty() && state.conversationTitle != "New Conversation") {
            Text(
                text = state.conversationTitle,
                style = MiasTypography.LabelLarge,
                color = MiasColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
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

    if (personaSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { personaSheetOpen = false },
            sheetState = personaSheetState,
            containerColor = MiasColors.Background,
        ) {
            PersonaPicker(
                personas = state.personas,
                selectedId = state.selectedPersona.id,
                onPick = { id ->
                    onSelectPersona(id)
                    scope.launch { personaSheetState.hide() }.invokeOnCompletion {
                        personaSheetOpen = false
                    }
                },
            )
        }
    }
}

@Composable
private fun PersonaPicker(
    personas: List<dev.mias.core.common.model.Persona>,
    selectedId: String,
    onPick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Persona",
            style = MiasTypography.LabelMedium,
            color = MiasColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        personas.forEach { persona ->
            val selected = persona.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(MiasShapes.Card)
                    .background(if (selected) MiasColors.SurfaceGlass else MiasColors.Surface2)
                    .border(
                        1.dp,
                        if (selected) MiasColors.Heather else MiasColors.OutlineSoft,
                        MiasShapes.Card,
                    )
                    .clickable { onPick(persona.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        style = MiasTypography.LabelLarge,
                        color = MiasColors.TextPrimary,
                    )
                    Text(
                        text = persona.tagline,
                        style = MiasTypography.BodySmall,
                        color = MiasColors.TextSecondary,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MiasColors.Heather,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedOrb(
            cognitionState = cognitionState,
            size = 64.dp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "How can I help today?",
            style = MiasTypography.HeadlineMedium,
            color = MiasColors.TextHi,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Everything stays on your device.",
            style = MiasTypography.BodyMedium,
            color = MiasColors.TextLo,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))

        // 2x2 grid of suggestion cards.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SUGGESTIONS.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            onClick = { onSuggestion(suggestion.prompt) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class Suggestion(
    val label: String,
    val hint: String,
    val prompt: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val SUGGESTIONS = listOf(
    Suggestion(
        label = "Summarize",
        hint = "long notes, transcripts",
        prompt = "Summarize this for me: ",
        icon = Icons.Rounded.Description,
    ),
    Suggestion(
        label = "Plan my day",
        hint = "task list from a few notes",
        prompt = "Help me plan a productive day. Ask me what I'm working on first.",
        icon = Icons.Rounded.Today,
    ),
    Suggestion(
        label = "Explain",
        hint = "a concept in plain words",
        prompt = "Explain ",
        icon = Icons.AutoMirrored.Rounded.MenuBook,
    ),
    Suggestion(
        label = "Brainstorm",
        hint = "ideas, names, angles",
        prompt = "Help me brainstorm ideas for ",
        icon = Icons.Rounded.AutoAwesome,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(
    selectedSkill: String?,
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onCapture: () -> Unit,
    onPickDocument: () -> Unit,
    onSelectSkill: (String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MiasColors.Surface4,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Text("Add", style = MiasTypography.LabelLarge, color = MiasColors.TextLo)
            Spacer(modifier = Modifier.height(8.dp))
            AttachmentOption(
                icon = Icons.Rounded.PhotoLibrary,
                label = "Add photo",
                hint = "Pick an image from this device",
                onClick = onPickGallery,
            )
            AttachmentOption(
                icon = Icons.Rounded.PhotoCamera,
                label = "Take a photo",
                hint = "Capture with the camera now",
                onClick = onCapture,
            )
            AttachmentOption(
                icon = Icons.Rounded.Description,
                label = "Add document",
                hint = "PDF, .txt or .md — Mias can answer from it",
                onClick = onPickDocument,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Skills", style = MiasTypography.LabelLarge, color = MiasColors.TextLo)
            Spacer(modifier = Modifier.height(8.dp))
            // Bias the model toward a specific tool, or Auto (let it decide).
            val skills = listOf(
                null to "Auto",
                "web_search" to "Web search",
                "calculator" to "Calculator",
                "datetime" to "Date & time",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                skills.forEach { (id, label) ->
                    SkillChip(
                        label = label,
                        selected = selectedSkill == id,
                        onClick = { onSelectSkill(id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SkillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MiasShapes.Full)
            .background(if (selected) MiasColors.Heather.copy(alpha = 0.22f) else MiasColors.Surface2)
            .border(
                1.dp,
                if (selected) MiasColors.Heather else MiasColors.OutlineSoft,
                MiasShapes.Full,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiasTypography.LabelMedium,
            color = if (selected) MiasColors.TextHi else MiasColors.TextLo,
        )
    }
}

private fun skillLabel(skill: String): String = when (skill) {
    "web_search" -> "Web search"
    "calculator" -> "Calculator"
    "datetime" -> "Date & time"
    else -> skill
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hint: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MiasShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MiasShapes.Full)
                .background(MiasColors.HeatherContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiasColors.Heather,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = label,
                style = MiasTypography.LabelLarge,
                color = MiasColors.TextHi,
            )
            Text(
                text = hint,
                style = MiasTypography.LabelSmall,
                color = MiasColors.TextLo,
            )
        }
    }
}

@Composable
private fun AttachedImageStrip(
    image: android.graphics.Bitmap,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MiasShapes.Medium)
                .background(MiasColors.Surface3),
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Image attached · sent to vision model",
            style = MiasTypography.LabelMedium,
            color = MiasColors.TextLo,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove image",
                tint = MiasColors.TextLo,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MiasShapes.Card)
            .background(MiasColors.Surface2)
            .border(1.dp, MiasColors.OutlineSoft, MiasShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = suggestion.icon,
            contentDescription = null,
            tint = MiasColors.Heather,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = suggestion.label,
            style = MiasTypography.LabelLarge,
            color = MiasColors.TextHi,
        )
        Text(
            text = suggestion.hint,
            style = MiasTypography.LabelSmall,
            color = MiasColors.TextLo,
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
