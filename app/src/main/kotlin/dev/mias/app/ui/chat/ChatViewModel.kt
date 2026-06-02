package dev.mias.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.common.getOrDefault
import dev.mias.core.common.getOrNull
import dev.mias.core.common.model.BrainState
import dev.mias.core.common.model.CognitionState
import dev.mias.core.common.model.Stimulus
import dev.mias.core.common.model.StimulusType
import dev.mias.core.data.Conversation
import dev.mias.core.data.ConversationRepository
import dev.mias.core.data.Message
import dev.mias.core.data.Role
import dev.mias.core.data.hindsight.HindsightMemory
import dev.mias.core.common.MiasResult
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import dev.mias.core.inference.react.ReActStep
import dev.mias.core.inference.react.ResponseSanitizer
import dev.mias.core.inference.react.StreamingReActParser
import dev.mias.core.inference.vision.MediaPipeVisionEngine
import dev.mias.core.language.IntentExtractor
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.ui.components.BubbleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String,
    val text: String,
    val type: BubbleType,
    /** Display string (HH:mm) used by the bubble UI. */
    val timestamp: String,
    /** Real epoch-millis of creation — the source of truth for ordering and persistence. */
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val image: Bitmap? = null,
    val imagePath: String? = null,
    /** Parsed reasoning for an assistant turn. Stored, not replayed to the model. */
    val reasoning: String? = null,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isProcessing: Boolean = false,
    val brainState: BrainState = BrainState.GEMMA_NPU,
    val cognitionState: CognitionState = CognitionState.IDLE,
    val conversationTitle: String = "New Conversation",
    val showReActSteps: Boolean = false,
    val chatModels: List<InstalledModel> = emptyList(),
    val activeChatModelId: String? = null,
    val attachedImage: Bitmap? = null,
    val hasVisionModel: Boolean = false,
)

sealed interface ChatEvent {
    data object ScrollToBottom : ChatEvent
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orchestrator: InferenceOrchestrator,
    private val hindsightMemory: HindsightMemory,
    private val intentExtractor: IntentExtractor,
    private val conversationRepository: ConversationRepository,
    private val modelManager: ModelManager,
    private val visionEngine: MediaPipeVisionEngine,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: String =
        savedStateHandle.get<String>("conversationId") ?: UUID.randomUUID().toString()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _inputText = MutableStateFlow("")
    private val _isProcessing = MutableStateFlow(false)
    private val _showReActSteps = MutableStateFlow(false)
    private val _attachedImage = MutableStateFlow<Bitmap?>(null)

    /**
     * Model-generated title for this conversation. Null until the first
     * user/assistant exchange completes and the title pass runs. Persisted
     * across loads via [loadExistingConversation].
     */
    private val _generatedTitle = MutableStateFlow<String?>(null)
    private var titleJob: Job? = null

    /**
     * Real creation time of this conversation. Captured once — from the loaded
     * conversation, or the first persisted message — and never reset, so
     * repeated saves don't keep stamping `createdAt` with "now".
     */
    private var conversationCreatedAt: Long? = null

    /** The active generation coroutine, so the Stop button can cancel it. */
    private var inferenceJob: Job? = null

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private data class ChatModelInfo(
        val chatModels: List<InstalledModel>,
        val activeChatModelId: String?,
        val hasVisionModel: Boolean,
    )

    private val chatModelSelection: kotlinx.coroutines.flow.Flow<ChatModelInfo> =
        combine(modelManager.installedModels, modelManager.roleAssignments) { installed, assignments ->
            ChatModelInfo(
                chatModels = installed.filter { ModelRole.CHAT in it.card.roles },
                activeChatModelId = assignments[ModelRole.CHAT],
                hasVisionModel = installed.any { ModelRole.VISION in it.card.roles },
            )
        }

    private data class MessagesSnapshot(
        val messages: List<ChatMessage>,
        val attachedImage: Bitmap?,
        val title: String?,
    )

    val uiState: StateFlow<ChatUiState> = combine(
        combine(_messages, _attachedImage, _generatedTitle) { msgs, img, title ->
            MessagesSnapshot(msgs, img, title)
        },
        _inputText,
        _isProcessing,
        combine(orchestrator.brainState, orchestrator.cognitionState) { b, c -> b to c },
        chatModelSelection,
    ) { snapshot, input, processing, brainCog, info ->
        ChatUiState(
            messages = snapshot.messages,
            inputText = input,
            isProcessing = processing,
            brainState = brainCog.first,
            cognitionState = brainCog.second,
            conversationTitle = snapshot.title
                ?: snapshot.messages.firstOrNull { it.type == BubbleType.USER }?.text?.take(40)
                ?: "New Conversation",
            showReActSteps = _showReActSteps.value,
            chatModels = info.chatModels,
            activeChatModelId = info.activeChatModelId,
            attachedImage = snapshot.attachedImage,
            hasVisionModel = info.hasVisionModel,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    init {
        loadExistingConversation()
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun applyTranscription(text: String) {
        _inputText.value = text
    }

    fun onSend() {
        val text = _inputText.value.trim()
        val attachedBitmap = _attachedImage.value
        if (attachedBitmap == null && text.isBlank()) return
        if (_isProcessing.value) return

        // Vision branch: if an image is attached, dispatch to the vision
        // engine directly. Falls back through the standard chat path when
        // no vision model is installed.
        if (attachedBitmap != null) {
            sendWithImage(attachedBitmap, text)
            return
        }

        // Intent extraction is best-effort enrichment — never let it crash a send.
        val structuredIntent = runCatching { intentExtractor.extract(text) }.getOrNull()
        val cleanedText = structuredIntent?.cleanedText?.ifBlank { text } ?: text

        _inputText.value = ""
        _isProcessing.value = true

        // Add user message
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = cleanedText,
            type = BubbleType.USER,
            timestamp = formatTime(System.currentTimeMillis()),
        )
        _messages.update { it + userMsg }
        _events.tryEmit(ChatEvent.ScrollToBottom)

        val pendingTitleJob = titleJob
        titleJob = null
        inferenceJob = viewModelScope.launch {
            // A background title pass from the previous turn may still hold the
            // native context. Stop and await it before we generate, so the two
            // never collide on the single non-reentrant engine.
            pendingTitleJob?.cancelAndJoin()

            // Collect the ReAct flow. We split the raw JSON stream in real
            // time into the thinking ("thought") and the visible reply
            // ("should_say") so the user never sees raw JSON. Declared outside
            // the try so the catch fallback can recover any readable text.
            var finalResponse = ""
            var streamingMsgId: String? = null
            val rawBuffer = StringBuilder()
            var currentThinkingText = ""
            var currentVisibleResponse = ""

            try {
                // Store fact in Hindsight
                hindsightMemory.storeFact(
                    content = "User said: $cleanedText",
                    conversationId = conversationId,
                )

                // Get Hindsight context
                val hindsightContext = hindsightMemory.query(cleanedText)
                    .getOrNull()
                    ?.toPromptString()
                    ?: ""

                val systemPrompt = InferenceOrchestrator.DEFAULT_SYSTEM_PROMPT

                val metadata = buildMap<String, String> {
                    structuredIntent?.let { si ->
                        put("intent_type", si.intentType.value)
                        put("intent_confidence", si.confidence.toString())
                        si.actionHint?.let { put("action_hint", it) }
                        si.modifiers.forEachIndexed { index, tag ->
                            put("modifier_${index + 1}", tag)
                        }
                        si.entities.forEach { (k, v) ->
                            put("entity_$k", v)
                        }
                    }
                }

                val stimulus = Stimulus(
                    type = StimulusType.USER_MESSAGE,
                    content = cleanedText,
                    metadata = metadata,
                )

                orchestrator.process(
                    stimulus = stimulus,
                    systemPrompt = systemPrompt,
                    hindsightContext = hindsightContext,
                ).collect { step ->
                when (step) {
                    is ReActStep.Thought -> {
                        if (_showReActSteps.value) {
                            val thoughtMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = step.reasoning,
                                type = BubbleType.THOUGHT,
                                timestamp = formatTime(System.currentTimeMillis()),
                            )
                            _messages.update { it + thoughtMsg }
                            _events.tryEmit(ChatEvent.ScrollToBottom)
                        }
                    }

                    is ReActStep.Action -> {
                        if (_showReActSteps.value) {
                            val actionMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = "${step.tool}(${step.input})",
                                type = BubbleType.ACTION,
                                timestamp = formatTime(System.currentTimeMillis()),
                            )
                            _messages.update { it + actionMsg }
                            _events.tryEmit(ChatEvent.ScrollToBottom)
                        }
                    }

                    is ReActStep.Observation -> {
                        // A tool ran; the agent loops for another turn. Reset the
                        // streaming buffer so the next turn's thought/should_say
                        // parse fresh instead of re-matching this turn's text.
                        rawBuffer.setLength(0)
                        currentVisibleResponse = ""
                    }

                    is ReActStep.FinalAnswer -> {
                        // Finalize: strip any remaining JSON artifacts so only the
                        // clean reply shows. Prefer the live-streamed thinking;
                        // fall back to whatever the sanitizer parsed.
                        val sanitized = ResponseSanitizer.sanitize(step.response)
                        finalResponse = sanitized.chatText
                        val thinking = currentThinkingText.ifBlank { sanitized.reasoningText.orEmpty() }
                            .ifBlank { null }
                        _messages.update { currentList ->
                            val existingIndex = currentList.indexOfFirst { it.id == streamingMsgId }
                            if (existingIndex >= 0) {
                                val updatedList = currentList.toMutableList()
                                updatedList[existingIndex] = updatedList[existingIndex].copy(
                                    text = sanitized.chatText,
                                    type = BubbleType.Mias,
                                    isStreaming = false,
                                    reasoning = thinking,
                                )
                                updatedList
                            } else {
                                currentList + ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    text = sanitized.chatText,
                                    type = BubbleType.Mias,
                                    timestamp = formatTime(System.currentTimeMillis()),
                                    isStreaming = false,
                                    reasoning = thinking,
                                )
                            }
                        }
                        streamingMsgId = null
                        _events.tryEmit(ChatEvent.ScrollToBottom)
                    }

                    is ReActStep.ModelSwitch -> {
                        if (_showReActSteps.value) {
                            val switchMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = "Switched: ${step.from.name} → ${step.to.name}",
                                type = BubbleType.ACTION,
                                timestamp = formatTime(System.currentTimeMillis()),
                            )
                            _messages.update { it + switchMsg }
                        }
                    }

                    is ReActStep.TokenChunk -> {
                        // Accumulate the raw stream, then re-derive the live
                        // thinking + visible split. Only the parsed fields ever
                        // reach the bubble — never the raw JSON.
                        rawBuffer.append(step.text)
                        val parsed = StreamingReActParser.parse(rawBuffer.toString())
                        currentThinkingText = parsed.thinking
                        currentVisibleResponse = parsed.visible

                        if (streamingMsgId == null) {
                            streamingMsgId = UUID.randomUUID().toString()
                            _messages.update {
                                it + ChatMessage(
                                    id = streamingMsgId!!,
                                    text = currentVisibleResponse,
                                    type = BubbleType.Mias,
                                    timestamp = formatTime(System.currentTimeMillis()),
                                    isStreaming = true,
                                    reasoning = currentThinkingText.ifBlank { null },
                                )
                            }
                        } else {
                            _messages.update { currentList ->
                                val idx = currentList.indexOfFirst { it.id == streamingMsgId }
                                if (idx >= 0) {
                                    val updated = currentList.toMutableList()
                                    updated[idx] = updated[idx].copy(
                                        text = currentVisibleResponse,
                                        reasoning = currentThinkingText.ifBlank { null },
                                    )
                                    updated
                                } else {
                                    currentList
                                }
                            }
                        }
                        _events.tryEmit(ChatEvent.ScrollToBottom)
                    }
                }
            }

                // Store Mias's response in Hindsight
                if (finalResponse.isNotBlank()) {
                    hindsightMemory.storeFact(
                        content = "Mias responded: $finalResponse",
                        conversationId = conversationId,
                    )
                }

                // Persist conversation
                saveConversation()

                // First completed exchange — kick off a one-shot title pass.
                maybeGenerateTitle(userText = cleanedText, assistantText = finalResponse)
            } catch (ce: CancellationException) {
                // Stop button / new send cancelled this turn — re-throw so the
                // engine's awaitClose aborts native generation. Never swallow.
                throw ce
            } catch (e: Exception) {
                // Anything else (engine load, parser, IO) must not crash the app.
                android.util.Log.e("ChatViewModel", "Chat generation failed", e)
                // Keep whatever readable text was streamed; otherwise apologise.
                val fallback = currentVisibleResponse
                    .ifBlank { rawBuffer.toString().trim() }
                    .ifBlank { "Something went wrong while I was answering. Please try again." }
                _messages.update { list ->
                    val idx = list.indexOfFirst { it.id == streamingMsgId }
                    if (idx >= 0) {
                        val updated = list.toMutableList()
                        updated[idx] = updated[idx].copy(
                            text = fallback,
                            type = BubbleType.Mias,
                            isStreaming = false,
                        )
                        updated
                    } else {
                        list + ChatMessage(
                            id = UUID.randomUUID().toString(),
                            text = fallback,
                            type = BubbleType.Mias,
                            timestamp = formatTime(System.currentTimeMillis()),
                        )
                    }
                }
                runCatching { saveConversation() }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * After the first user/assistant exchange, ask the model for a short
     * title (≤6 words) via the orchestrator's dedicated, state-free title
     * pass. Runs once per conversation; silent on failure, since the fallback
     * (first 50 chars of the first user message) still applies in
     * [saveConversation]. Held in [titleJob] so a new send can cancel it
     * before touching the shared native context.
     */
    private fun maybeGenerateTitle(userText: String, assistantText: String) {
        if (_generatedTitle.value != null) return
        if (userText.isBlank() || assistantText.isBlank()) return
        if (titleJob?.isActive == true) return

        titleJob = viewModelScope.launch {
            val raw = runCatching {
                orchestrator.summarizeTitle(userText, assistantText)
            }.getOrNull().orEmpty()
            val cleaned = sanitizeTitle(raw)
            if (cleaned.isNotBlank()) {
                _generatedTitle.value = cleaned
                saveConversation()
            }
        }
    }

    private fun sanitizeTitle(raw: String): String {
        if (raw.isBlank()) return ""
        val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val unquoted = firstLine.trim('"', '\'', '`', '*', ' ', '.', ':', '-', '—')
        val words = unquoted.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(6).joinToString(" ").take(60)
    }

    /**
     * Stop button. Cancels the active generation coroutine, which cancels the
     * inference flow — its `awaitClose` flips the native abort flag so the C++
     * loop breaks within one token. Any partial response stays on screen and
     * is persisted.
     */
    fun stopGeneration() {
        val job = inferenceJob ?: return
        inferenceJob = null
        job.cancel()
        _isProcessing.value = false
        _messages.update { list ->
            list.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        }
        viewModelScope.launch { saveConversation() }
    }

    fun toggleReActSteps() {
        _showReActSteps.update { !it }
    }

    fun selectChatModel(modelId: String) {
        viewModelScope.launch { modelManager.assignRole(modelId, ModelRole.CHAT) }
    }

    fun useSuggestion(text: String) {
        _inputText.value = text
    }

    fun attachImage(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { resizeBitmap(it, MAX_ATTACH_DIM) }
                }
            }.getOrNull()
            _attachedImage.value = bitmap
        }
    }

    fun attachBitmap(bitmap: Bitmap?) {
        if (bitmap == null) return
        _attachedImage.value = resizeBitmap(bitmap, MAX_ATTACH_DIM)
    }

    fun clearAttachment() {
        _attachedImage.value = null
    }

    private fun resizeBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDim) return source
        val scale = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun sendWithImage(image: Bitmap, prompt: String) {
        val effectivePrompt = prompt.ifBlank { "What's in this image?" }
        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = userMsgId,
            text = effectivePrompt,
            type = BubbleType.USER,
            timestamp = formatTime(System.currentTimeMillis()),
            image = image,
        )
        _messages.update { it + userMsg }
        _inputText.value = ""
        _attachedImage.value = null
        _events.tryEmit(ChatEvent.ScrollToBottom)

        val pendingTitleJob = titleJob
        titleJob = null
        inferenceJob = viewModelScope.launch {
            pendingTitleJob?.cancelAndJoin()

            // Persist the bitmap off the main thread, then record the path on
            // the message so saveConversation() picks it up.
            val savedPath = withContext(Dispatchers.IO) {
                saveAttachmentToDisk(image, userMsgId)
            }
            if (savedPath != null) {
                _messages.update { list ->
                    val idx = list.indexOfFirst { it.id == userMsgId }
                    if (idx >= 0) {
                        val updated = list.toMutableList()
                        updated[idx] = updated[idx].copy(imagePath = savedPath)
                        updated
                    } else list
                }
            }

            val visionModel = modelManager.getModelForRole(ModelRole.VISION)
            if (visionModel == null) {
                _messages.update {
                    it + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "No vision model is installed yet. Open Models, switch the " +
                            "Hugging Face filter to \"Vision (.task)\", and download a " +
                            "Gemma 3n bundle. I'll be ready as soon as it's installed.",
                        type = BubbleType.Mias,
                        timestamp = formatTime(System.currentTimeMillis()),
                    )
                }
                _events.tryEmit(ChatEvent.ScrollToBottom)
                saveConversation()
                return@launch
            }

            _isProcessing.value = true
            val streamingId = UUID.randomUUID().toString()
            _messages.update {
                it + ChatMessage(
                    id = streamingId,
                    text = "",
                    type = BubbleType.Mias,
                    timestamp = formatTime(System.currentTimeMillis()),
                    isStreaming = true,
                )
            }

            try {
                visionEngine.processStream(visionModel.localPath, image, effectivePrompt)
                    .collect { chunk ->
                        when (chunk) {
                            is MiasResult.Success -> _messages.update { list ->
                                val idx = list.indexOfFirst { it.id == streamingId }
                                if (idx >= 0) {
                                    val updated = list.toMutableList()
                                    val old = updated[idx]
                                    updated[idx] = old.copy(text = old.text + chunk.data)
                                    updated
                                } else list
                            }
                            is MiasResult.Error -> _messages.update { list ->
                                val idx = list.indexOfFirst { it.id == streamingId }
                                if (idx >= 0) {
                                    val updated = list.toMutableList()
                                    updated[idx] = updated[idx].copy(
                                        text = chunk.message,
                                        isStreaming = false,
                                        type = BubbleType.ERROR,
                                    )
                                    updated
                                } else list
                            }
                        }
                        _events.tryEmit(ChatEvent.ScrollToBottom)
                    }
            } finally {
                _messages.update { list ->
                    val idx = list.indexOfFirst { it.id == streamingId }
                    if (idx >= 0) {
                        val updated = list.toMutableList()
                        updated[idx] = updated[idx].copy(isStreaming = false)
                        updated
                    } else list
                }
                _isProcessing.value = false
                saveConversation()

                // Title parity with the text path. Reuses whichever text
                // engine is warm; if only the vision engine is loaded,
                // summarizeTitle finds nothing and we keep the fallback title.
                val finalMsg = _messages.value.firstOrNull { it.id == streamingId }
                if (finalMsg != null && finalMsg.type != BubbleType.ERROR) {
                    maybeGenerateTitle(userText = effectivePrompt, assistantText = finalMsg.text)
                }
            }
        }
    }

    private fun loadExistingConversation() {
        viewModelScope.launch {
            val result = conversationRepository.getConversation(conversationId)
            val conversation = result.getOrNull() ?: return@launch
            // Preserve any previously generated title so we don't re-run the
            // title pass on every reopen. The fallback string is treated as
            // "no title yet" so a real title is still generated on next send.
            val savedTitle = conversation.title
            if (savedTitle.isNotBlank() && savedTitle != "Conversation" && savedTitle != "New Conversation") {
                _generatedTitle.value = savedTitle
            }
            conversationCreatedAt = conversation.createdAt
            _messages.value = conversation.messages.map { msg ->
                ChatMessage(
                    id = msg.id,
                    text = msg.content,
                    type = if (msg.role == Role.USER) BubbleType.USER else BubbleType.Mias,
                    timestamp = formatTime(msg.timestamp),
                    createdAtMillis = msg.timestamp,
                    image = msg.imagePath?.let { loadAttachmentFromDisk(it) },
                    imagePath = msg.imagePath,
                    reasoning = msg.reasoningText,
                )
            }
        }
    }

    private suspend fun saveConversation() {
        // Persistence must never crash the chat. Any failure here is logged and
        // swallowed — losing a save is recoverable; crashing the turn isn't.
        try {
            val msgs = _messages.value
            if (msgs.isEmpty()) return

            // Stable creation time: prefer an already-captured value (loaded or set
            // on a prior save), otherwise the earliest message, then now. Set once
            // so it never drifts on subsequent saves.
            val createdAt = conversationCreatedAt
                ?: msgs.minOfOrNull { it.createdAtMillis }
                ?: System.currentTimeMillis()
            conversationCreatedAt = createdAt

            val conversation = Conversation(
                id = conversationId,
                title = _generatedTitle.value
                    ?: msgs.firstOrNull { it.type == BubbleType.USER }?.text?.take(50)
                    ?: "Conversation",
                messages = msgs.map { msg ->
                    Message(
                        id = msg.id,
                        conversationId = conversationId,
                        role = if (msg.type == BubbleType.USER) Role.USER else Role.ASSISTANT,
                        content = msg.text,
                        timestamp = msg.createdAtMillis,
                        imagePath = msg.imagePath,
                        reasoningText = msg.reasoning,
                    )
                },
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
            )
            val result = conversationRepository.saveConversation(conversation)
            if (result is MiasResult.Error) {
                android.util.Log.e("ChatViewModel", "Failed to save conversation: ${result.message}")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to save conversation", e)
        }
    }

    /**
     * Persist the attached bitmap as a JPEG under
     * `${filesDir}/conversations/<convId>/<msgId>.jpg` and return its
     * absolute path. Returns null on write failure — the message still
     * goes through, just without disk-backed image persistence.
     */
    private fun saveAttachmentToDisk(image: Bitmap, messageId: String): String? = runCatching {
        val dir = java.io.File(context.filesDir, "conversations/$conversationId").apply { mkdirs() }
        val file = java.io.File(dir, "$messageId.jpg")
        java.io.FileOutputStream(file).use { out ->
            image.compress(Bitmap.CompressFormat.JPEG, ATTACHMENT_QUALITY, out)
        }
        file.absolutePath
    }.getOrNull()

    private fun loadAttachmentFromDisk(path: String): Bitmap? = runCatching {
        val file = java.io.File(path)
        if (!file.exists()) return@runCatching null
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    private fun formatTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    companion object {
        private const val MAX_ATTACH_DIM: Int = 1024
        private const val ATTACHMENT_QUALITY: Int = 85
    }
}
