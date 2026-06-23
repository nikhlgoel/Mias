package dev.mias.app.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.agent.capabilities.WebAnswerCapability
import dev.mias.core.agent.capabilities.WebImage
import dev.mias.core.agent.capabilities.WebSource
import dev.mias.core.common.getOrDefault
import dev.mias.core.common.getOrNull
import dev.mias.core.common.model.BrainState
import dev.mias.core.common.model.CognitionState
import dev.mias.core.common.model.Persona
import dev.mias.core.common.model.Personas
import dev.mias.core.common.model.Stimulus
import dev.mias.core.common.model.StimulusType
import dev.mias.core.data.Conversation
import dev.mias.core.data.ConversationRepository
import dev.mias.core.data.Message
import dev.mias.core.data.Role
import dev.mias.core.data.hindsight.HindsightMemory
import dev.mias.core.data.preferences.MiasPreferences
import dev.mias.core.data.rag.DocumentRepository
import dev.mias.core.data.rag.RetrievedContext
import dev.mias.core.common.MiasResult
import dev.mias.core.common.memory.MemoryDistiller
import dev.mias.core.inference.orchestrator.DeviceTier
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import dev.mias.core.inference.react.ReActStep
import dev.mias.core.inference.react.ResponseSanitizer
import dev.mias.core.inference.react.ToolRegistry
import dev.mias.core.inference.react.StreamingReActParser
import dev.mias.core.inference.vision.MediaPipeVisionEngine
import dev.mias.core.inference.vision.VisionModelSupport
import dev.mias.core.language.IntentExtractor
import dev.mias.core.language.IntentType
import dev.mias.core.language.StructuredIntent
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.modelhub.model.capabilityProfile
import dev.mias.core.resilience.DeviceHealthMonitor
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
    /** Document names this answer drew on (RAG citations). */
    val sources: List<String> = emptyList(),
    /** Web pages this answer was grounded on — tappable [n] citations. */
    val webCitations: List<WebSource> = emptyList(),
    /** Facts saved to persistent memory during this turn ("Memory updated" chip). */
    val savedMemories: List<String> = emptyList(),
    /** Lead images pulled from the cited pages (visual queries only). */
    val webImages: List<WebImage> = emptyList(),
    /**
     * A file the assistant produced from this turn. Present only for
     * file-generation intents. The file is written for real on save — the
     * message never *claims* a file exists until [FileArtifact.saved] is true.
     */
    val fileArtifact: FileArtifact? = null,
)

/**
 * A generated file offered on an assistant message. Honest by construction:
 * the content is shown in the bubble; the file is only written when the user
 * saves it (or, for an explicit "export/save" request, written immediately and
 * surfaced with Open/Share). No phantom "I made a file" claims.
 */
data class FileArtifact(
    val fileName: String,
    val content: String,
    val saved: Boolean = false,
    /** content:// URI once written, for Open/Share. */
    val savedUri: String? = null,
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
    /** Short capability tag for the active model: "Vision" / "Pro" / "Lite". */
    val activeModelCapability: String? = null,
    val attachedImage: Bitmap? = null,
    val hasVisionModel: Boolean = false,
    val personas: List<Persona> = Personas.ALL,
    val selectedPersona: Persona = Personas.DEFAULT,
    val ragActive: Boolean = false,
    /** Forced tool from the composer menu (null = Auto). */
    val forcedSkill: String? = null,
    /** Transient banner after attaching a document from chat. */
    val attachNotice: String? = null,
    /** Estimated tokens of the conversation context, and the model's window. */
    val contextUsedTokens: Int = 0,
    val contextWindowTokens: Int = 4096,
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
    private val miasPreferences: MiasPreferences,
    private val documentRepository: DocumentRepository,
    private val toolRegistry: ToolRegistry,
    private val webAnswer: WebAnswerCapability,
    private val deviceHealthMonitor: DeviceHealthMonitor,
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
     * Background memory-distillation pass for the latest exchange. Like
     * [titleJob], it generates under the orchestrator's lock, so a new send
     * must cancel-and-join it before generating.
     */
    private var memoryJob: Job? = null

    /** The active persona (system-prompt preset). Persisted via DataStore. */
    private val _selectedPersona = MutableStateFlow(Personas.DEFAULT)

    /** Whether the local knowledge base feeds into answers (persisted preference). */
    private val _useDocuments = MutableStateFlow(true)

    /** A tool the user explicitly chose from the composer ("+") menu; null = Auto. */
    private val _forcedSkill = MutableStateFlow<String?>(null)

    /** Transient banner shown after attaching a document from chat. */
    private val _attachNotice = MutableStateFlow<String?>(null)

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
        val ragActive: Boolean,
    )

    private val chatModelSelection: kotlinx.coroutines.flow.Flow<ChatModelInfo> =
        combine(
            modelManager.installedModels,
            modelManager.roleAssignments,
            documentRepository.observeDocumentCount(),
            miasPreferences.prefsFlow,
        ) { installed, assignments, docCount, prefs ->
            ChatModelInfo(
                chatModels = installed.filter { ModelRole.CHAT in it.card.roles },
                activeChatModelId = assignments[ModelRole.CHAT],
                hasVisionModel = installed.any { ModelRole.VISION in it.card.roles },
                ragActive = prefs.useDocuments && docCount > 0,
            )
        }

    private data class MessagesSnapshot(
        val messages: List<ChatMessage>,
        val attachedImage: Bitmap?,
        val title: String?,
    )

    private data class SessionFlags(
        val brainState: BrainState,
        val cognitionState: CognitionState,
        val persona: Persona,
        val forcedSkill: String?,
        val attachNotice: String?,
    )

    val uiState: StateFlow<ChatUiState> = combine(
        combine(_messages, _attachedImage, _generatedTitle) { msgs, img, title ->
            MessagesSnapshot(msgs, img, title)
        },
        _inputText,
        _isProcessing,
        combine(
            orchestrator.brainState,
            orchestrator.cognitionState,
            _selectedPersona,
            _forcedSkill,
            _attachNotice,
        ) { b, c, persona, skill, notice -> SessionFlags(b, c, persona, skill, notice) },
        chatModelSelection,
    ) { snapshot, input, processing, flags, info ->
        // Rough context estimate (~4 chars/token): persona + scaffolding +
        // everything in the conversation + the pending input, versus the active
        // model's real window (Qwen 32k, Phi 128k, …).
        val activeModel = info.chatModels.firstOrNull { it.id == info.activeChatModelId }
            ?: info.chatModels.firstOrNull()
        val window = activeModel?.card?.contextLength?.takeIf { it > 0 } ?: CONTEXT_WINDOW_TOKENS
        val capability = activeModel?.card?.capabilityProfile()?.let { p ->
            when {
                p.supportsVision -> "Vision"
                p.supportsToolCalls -> "Pro"
                else -> "Lite"
            }
        }
        val systemChars = flags.persona.systemPrompt.length
        val convChars = snapshot.messages.sumOf { it.text.length + (it.reasoning?.length ?: 0) }
        val usedTokens = SYSTEM_SCAFFOLD_TOKENS +
            (systemChars + convChars + input.length) / CHARS_PER_TOKEN
        ChatUiState(
            messages = snapshot.messages,
            inputText = input,
            isProcessing = processing,
            brainState = flags.brainState,
            cognitionState = flags.cognitionState,
            conversationTitle = snapshot.title
                ?: snapshot.messages.firstOrNull { it.type == BubbleType.USER }?.text?.take(40)
                ?: "New Conversation",
            showReActSteps = _showReActSteps.value,
            chatModels = info.chatModels,
            activeChatModelId = info.activeChatModelId,
            activeModelCapability = capability,
            attachedImage = snapshot.attachedImage,
            hasVisionModel = info.hasVisionModel,
            personas = Personas.ALL,
            selectedPersona = flags.persona,
            ragActive = info.ragActive,
            forcedSkill = flags.forcedSkill,
            attachNotice = flags.attachNotice,
            contextUsedTokens = usedTokens.coerceAtMost(window),
            contextWindowTokens = window,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    init {
        loadExistingConversation()
        // Opening a chat is a strong signal the user is about to send — warm the
        // chat model now (idempotent, load-mutex-guarded) so the first message
        // streams immediately instead of waiting on the weights to load.
        viewModelScope.launch { orchestrator.warmUp() }
        // Keep persona + knowledge-base preference in sync with persistence.
        viewModelScope.launch {
            miasPreferences.prefsFlow.collect { prefs ->
                _selectedPersona.value = Personas.byId(prefs.personaId)
                _useDocuments.value = prefs.useDocuments
            }
        }
    }

    /** Switch the active persona (system-prompt preset); persisted across launches. */
    fun selectPersona(id: String) {
        viewModelScope.launch { miasPreferences.setPersonaId(id) }
    }

    /** Choose a tool to favor for upcoming messages, or null for Auto. */
    fun setForcedSkill(skill: String?) {
        _forcedSkill.value = skill
    }

    /**
     * Deterministically run a forced tool and format its result for the prompt.
     * The user's text is supplied under every common parameter name so the tool
     * picks the one it needs. Bounded by a timeout and never throws.
     */
    private suspend fun runSkill(skill: String, userText: String): String {
        val handler = toolRegistry.resolve(skill)?.let { toolRegistry.get(it) } ?: return ""
        val input = mapOf(
            "query" to userText,
            "expression" to userText,
            "input" to userText,
            "text" to userText,
        )
        val result = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(SKILL_TIMEOUT_MS) { handler.execute(input) }
        }.getOrNull()
        if (result.isNullOrBlank()) return ""
        return "## ${skillDisplayName(skill)} result\n$result"
    }

    private fun skillDisplayName(skill: String): String = when (skill) {
        "web_search" -> "Web search"
        "calculator" -> "Calculator"
        "datetime" -> "Date & time"
        else -> skill
    }

    /**
     * Heuristic: does this query need up-to-the-minute info the model can't know
     * from training? If so we auto-run web search so the user actually gets
     * current results instead of "I don't have live data".
     */
    private fun needsFreshInfo(text: String): Boolean {
        val l = text.lowercase()
        return FRESH_INFO_KEYWORDS.any { it in l }
    }

    // ── Web grounding (search + read + cite) ────────────────────────────

    private data class WebAnswerBundle(
        val text: String,
        val sources: List<WebSource>,
        val images: List<WebImage>,
    ) {
        companion object {
            val EMPTY = WebAnswerBundle("", emptyList(), emptyList())
        }
    }

    /**
     * Search + read the top results + cite. Depth adapts to the device tier
     * AND to whether the query wants depth (a quick fact reads one source; an
     * "explain/compare" reads up to the device ceiling). Images are pulled only
     * for visual queries. Never throws — degrades to empty so a turn proceeds.
     */
    private suspend fun runWebAnswer(query: String): WebAnswerBundle {
        val ceiling = when (DeviceTier.from(deviceHealthMonitor.health.value)) {
            DeviceTier.LOW -> 1
            DeviceTier.MID -> 2
            DeviceTier.HIGH -> 3
        }
        val q = query.lowercase()
        val wantsDepth = DEEP_QUERY_KEYWORDS.any { it in q }
        val maxResults = (if (wantsDepth) ceiling else 1).coerceAtMost(ceiling)
        val perSource = (TOTAL_WEB_CHAR_BUDGET / maxResults).coerceIn(1500, 4000)
        val visual = VISUAL_QUERY_KEYWORDS.any { it in q }

        val result = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(WEB_ANSWER_TIMEOUT_MS) {
                webAnswer.answer(
                    query = query,
                    maxResults = maxResults,
                    maxCharsPerSource = perSource,
                    includeImages = visual,
                )
            }
        }.getOrNull()

        return if (result == null || !result.online || result.promptText.isBlank()) {
            WebAnswerBundle.EMPTY
        } else {
            WebAnswerBundle(result.promptText, result.sources, result.images)
        }
    }

    /** Open a cited web source / image in the browser (user-initiated tap). */
    fun openUrl(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { _attachNotice.value = "Couldn't open that link." }
    }

    // ── Honest file generation ──────────────────────────────────────────

    private fun isFileGenIntent(intent: StructuredIntent?): Boolean =
        intent?.intentType == IntentType.FILE_GENERATION

    private fun isExplicitExport(text: String): Boolean {
        val l = text.lowercase()
        return EXPLICIT_EXPORT_KEYWORDS.any { it in l }
    }

    /**
     * Pick a filename: honour an explicit one in the request ("report.csv"),
     * else infer the extension from the produced content.
     */
    private fun deriveFileName(userText: String, content: String): String {
        FILENAME_IN_TEXT.find(userText)?.value?.let { return it.substringAfterLast('/').substringAfterLast('\\') }
        val lower = content.trimStart().lowercase()
        val ext = when {
            lower.startsWith("<!doctype html") || lower.startsWith("<html") || "<body" in lower -> "html"
            lower.startsWith("{") || lower.startsWith("[") -> "json"
            content.lineSequence().take(4).count { it.count { c -> c == ',' } >= 2 } >= 2 -> "csv"
            else -> "md"
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "mias-$stamp.$ext"
    }

    private fun mimeFor(fileName: String): String = when {
        fileName.endsWith(".md", true) -> "text/markdown"
        fileName.endsWith(".txt", true) -> "text/plain"
        fileName.endsWith(".json", true) -> "application/json"
        fileName.endsWith(".csv", true) -> "text/csv"
        fileName.endsWith(".html", true) -> "text/html"
        else -> "application/octet-stream"
    }

    /** Write a saveable artifact to public Documents and reveal Open/Share. */
    fun saveFile(messageId: String) {
        viewModelScope.launch {
            val artifact = _messages.value.firstOrNull { it.id == messageId }?.fileArtifact
                ?: return@launch
            if (artifact.saved) return@launch
            val uri = exportToMediaStore(artifact.fileName, artifact.content)
            if (uri == null) {
                _attachNotice.value = "Couldn't save ${artifact.fileName}."
                return@launch
            }
            _messages.update { list ->
                list.map {
                    if (it.id == messageId) {
                        it.copy(fileArtifact = artifact.copy(saved = true, savedUri = uri.toString()))
                    } else {
                        it
                    }
                }
            }
            _attachNotice.value = "Saved ${artifact.fileName} to Documents/MiasExports"
        }
    }

    fun openFile(messageId: String) = launchFileIntent(messageId) { uri, artifact ->
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(artifact.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareFile(messageId: String) = launchFileIntent(messageId) { uri, artifact ->
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeFor(artifact.fileName)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share ${artifact.fileName}",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun launchFileIntent(messageId: String, build: (Uri, FileArtifact) -> Intent) {
        val artifact = _messages.value.firstOrNull { it.id == messageId }?.fileArtifact ?: return
        val uri = artifact.savedUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
        runCatching { context.startActivity(build(uri, artifact)) }
            .onFailure { _attachNotice.value = "No app available for that file." }
    }

    private suspend fun exportToMediaStore(fileName: String, content: String): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(fileName))
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/MiasExports",
                    )
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                    ?: return@runCatching null
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                uri
            }.getOrNull()
        }

    fun clearAttachNotice() {
        _attachNotice.value = null
    }

    /**
     * Add a picked document to the local knowledge base from the chat composer.
     * Reuses the same RAG ingestion as the Knowledge screen; once stored, future
     * answers can draw on it. Reads off the main thread; reports via a banner.
     */
    fun attachDocument(uri: Uri) {
        viewModelScope.launch {
            _attachNotice.value = "Adding document…"
            try {
                val (name, text) = withContext(Dispatchers.IO) { readDocument(uri) }
                if (text.isBlank()) {
                    _attachNotice.value = "Couldn't read text from that file."
                    return@launch
                }
                // Scope chat-attached documents to this conversation.
                val ingestResult = documentRepository.ingest(name, text, conversationId)
                _attachNotice.value = when (ingestResult) {
                    is MiasResult.Success -> "Added \"${ingestResult.data.name}\" — I can answer from it now"
                    is MiasResult.Error -> ingestResult.message
                }
            } catch (e: Exception) {
                _attachNotice.value = "Couldn't add that file: ${e.message}"
            }
        }
    }

    private fun readDocument(uri: Uri): Pair<String, String> {
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: "Document"

        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
        val text = runCatching {
            if (isPdf) {
                dev.mias.app.util.PdfTextExtractor.extract(context, uri)
            } else {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
        }.getOrNull().orEmpty()
        return name to text
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
        val pendingMemoryJob = memoryJob
        memoryJob = null
        inferenceJob = viewModelScope.launch {
            // Background title/memory passes from the previous turn may still
            // hold the native context — stop and await them before generating.
            pendingMemoryJob?.cancelAndJoin()
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

                // Retrieve relevant passages from the user's documents (RAG),
                // scoped to global + this conversation. Best-effort: empty when
                // disabled, no docs, or no embedding model — never blocks a turn.
                val rag = if (_useDocuments.value) {
                    runCatching { documentRepository.retrieve(cleanedText, conversationId) }
                        .getOrNull() ?: RetrievedContext.EMPTY
                } else {
                    RetrievedContext.EMPTY
                }
                val ragContext = rag.promptText
                val turnSources = rag.sources

                // Run a tool ourselves and feed the result in, rather than relying
                // on a small model to emit the tool-call JSON. Web queries go
                // through web_answer (search + read top results + cite); other
                // forced skills run directly. Deterministic.
                val forced = _forcedSkill.value
                val isForcedWeb = forced == "web_search" || forced == "web_answer"
                val web = if (isForcedWeb || (forced == null && needsFreshInfo(cleanedText))) {
                    runWebAnswer(cleanedText)
                } else {
                    WebAnswerBundle.EMPTY
                }
                val turnWebSources = web.sources
                val turnWebImages = web.images

                val skillContext = when {
                    web.text.isNotBlank() -> web.text
                    forced != null && !isForcedWeb -> runSkill(forced, cleanedText)
                    else -> ""
                }

                // Explicit "remember this" — deterministic, saved BEFORE the
                // model replies, so the reply can acknowledge it honestly and
                // the bubble carries a "Memory updated" chip. A bare command
                // ("save this to memory") refers to the previous reply.
                var explicitMemory: String? = null
                var memoryContext = ""
                MemoryDistiller.extractExplicitMemory(cleanedText)?.let { inline ->
                    val content = inline.ifBlank {
                        _messages.value.lastOrNull {
                            it.type == BubbleType.Mias && it.text.isNotBlank()
                        }?.text?.take(MemoryDistiller.EXPLICIT_MAX_CHARS).orEmpty()
                    }
                    if (content.isNotBlank()) {
                        val stored = hindsightMemory.storeUserMemory(content, confidence = 1f)
                        val storedNew = (stored as? MiasResult.Success)?.data == true
                        if (storedNew) {
                            explicitMemory = content
                            _attachNotice.value = "Saved to memory"
                        } else if (stored is MiasResult.Success) {
                            // Near-duplicate of something already remembered.
                            _attachNotice.value = "Already in memory"
                        }
                        memoryContext =
                            "## Memory update\nThe app just saved this to persistent " +
                                "memory: \"$content\". Briefly confirm to the user that " +
                                "you'll remember it."
                    }
                }
                val turnSavedMemories = listOfNotNull(explicitMemory)

                val retrievalContext = listOf(memoryContext, skillContext, ragContext, hindsightContext)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")

                val systemPrompt = _selectedPersona.value.systemPrompt

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
                    hindsightContext = retrievalContext,
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

                        // Honest file generation: if this was a file-gen intent,
                        // offer the produced text as a real saveable file. The
                        // bubble shows the content; nothing claims a file exists.
                        val artifact = if (isFileGenIntent(structuredIntent) &&
                            sanitized.chatText.isNotBlank()
                        ) {
                            FileArtifact(
                                fileName = deriveFileName(cleanedText, sanitized.chatText),
                                content = sanitized.chatText,
                            )
                        } else {
                            null
                        }
                        val finalMsgId = streamingMsgId ?: UUID.randomUUID().toString()

                        _messages.update { currentList ->
                            val existingIndex = currentList.indexOfFirst { it.id == streamingMsgId }
                            if (existingIndex >= 0) {
                                val updatedList = currentList.toMutableList()
                                updatedList[existingIndex] = updatedList[existingIndex].copy(
                                    text = sanitized.chatText,
                                    type = BubbleType.Mias,
                                    isStreaming = false,
                                    reasoning = thinking,
                                    sources = turnSources,
                                    webCitations = turnWebSources,
                                    webImages = turnWebImages,
                                    savedMemories = turnSavedMemories,
                                    fileArtifact = artifact,
                                )
                                updatedList
                            } else {
                                currentList + ChatMessage(
                                    id = finalMsgId,
                                    text = sanitized.chatText,
                                    type = BubbleType.Mias,
                                    timestamp = formatTime(System.currentTimeMillis()),
                                    isStreaming = false,
                                    reasoning = thinking,
                                    sources = turnSources,
                                    webCitations = turnWebSources,
                                    webImages = turnWebImages,
                                    savedMemories = turnSavedMemories,
                                    fileArtifact = artifact,
                                )
                            }
                        }
                        streamingMsgId = null
                        _events.tryEmit(ChatEvent.ScrollToBottom)

                        // Explicit "save/export" request → write immediately and
                        // surface Open/Share; otherwise wait for a "Save" tap.
                        if (artifact != null && isExplicitExport(cleanedText)) {
                            saveFile(finalMsgId)
                        }
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

                        // Circuit-breaker: if the model falls into a runaway
                        // repetition loop (gibberish clusters), trim the loop and
                        // stop generation rather than streaming garbage forever.
                        if (isRunawayRepetition(rawBuffer.toString())) {
                            val trimmed = trimRepetition(currentVisibleResponse)
                            _messages.update { list ->
                                val idx = list.indexOfFirst { it.id == streamingMsgId }
                                if (idx >= 0) {
                                    val updated = list.toMutableList()
                                    updated[idx] = updated[idx].copy(text = trimmed)
                                    updated
                                } else {
                                    list
                                }
                            }
                            stopGeneration()
                            return@collect
                        }

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

                // Cross-conversation memory: if the user shared something
                // durable about themselves (or the conversation has grown long
                // enough for a periodic sweep), distill and remember it. Skipped
                // when an explicit "remember this" already saved this turn.
                if (explicitMemory == null) {
                    maybeDistillMemories(userText = cleanedText, assistantText = finalResponse)
                }
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
    /**
     * Persistent-memory write path (ChatGPT/Claude-style): after a completed
     * exchange, distill durable facts about the user and store them.
     *
     * Two triggers:
     *  - a cheap personal-signal heuristic on this turn's text (so ordinary
     *    Q&A doesn't pay an extra model pass), or
     *  - a periodic sweep every [LONG_CONVERSATION_DISTILL_EVERY] user turns —
     *    long conversations accumulate context worth keeping even when no
     *    single message tripped the gate; the sweep distills over a digest of
     *    the recent user messages.
     *
     * The distillation runs in the background under the orchestrator's
     * generation lock (cancelled by the next send if still pending). When
     * something was actually stored, the latest assistant bubble gets a
     * "Memory updated" chip. Recall is automatic — stored memories surface
     * through the Hindsight context already injected into every turn.
     */
    private fun maybeDistillMemories(userText: String, assistantText: String) {
        if (userText.isBlank() || assistantText.isBlank()) return
        val userTurns = _messages.value.count { it.type == BubbleType.USER }
        val periodicSweep = userTurns > 0 && userTurns % LONG_CONVERSATION_DISTILL_EVERY == 0
        if (!MemoryDistiller.containsPersonalSignal(userText) && !periodicSweep) return
        if (memoryJob?.isActive == true) return

        // For the periodic sweep, look back across recent user messages, not
        // just the latest — the durable detail may be a few turns up.
        val distillInput = if (periodicSweep) {
            _messages.value
                .filter { it.type == BubbleType.USER }
                .takeLast(RECENT_TURNS_IN_SWEEP)
                .joinToString("\n") { it.text }
        } else {
            userText
        }
        val targetMessageId = _messages.value.lastOrNull { it.type == BubbleType.Mias }?.id

        memoryJob = viewModelScope.launch {
            runCatching {
                val stored = orchestrator.distillMemories(distillInput, assistantText)
                    .filter { memory ->
                        (hindsightMemory.storeUserMemory(memory) as? MiasResult.Success)?.data == true
                    }
                // Surface what was remembered on the bubble, like the explicit path.
                if (stored.isNotEmpty() && targetMessageId != null) {
                    _messages.update { list ->
                        list.map {
                            if (it.id == targetMessageId) {
                                it.copy(savedMemories = it.savedMemories + stored)
                            } else {
                                it
                            }
                        }
                    }
                }
            }
        }
    }

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

    /**
     * Re-run the last user turn. Drops the last user message and everything
     * after it (the assistant reply), then replays it through [onSend] so the
     * whole pipeline — model load, streaming, persistence — is reused exactly.
     * No-op while generating or before the first user message.
     */
    fun regenerate() {
        if (_isProcessing.value) return
        val msgs = _messages.value
        val lastUserIdx = msgs.indexOfLast { it.type == BubbleType.USER }
        if (lastUserIdx < 0) return
        val userText = msgs[lastUserIdx].text
        // Keep everything before the last user turn; re-send that prompt fresh.
        _messages.value = msgs.subList(0, lastUserIdx).toList()
        _inputText.value = userText
        onSend()
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
        val pendingMemoryJob = memoryJob
        memoryJob = null
        inferenceJob = viewModelScope.launch {
            pendingMemoryJob?.cancelAndJoin()
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
            val incompatible = visionModel != null &&
                !VisionModelSupport.isTaskBundle(visionModel.localPath)
            if (visionModel == null || incompatible) {
                val notice = if (incompatible) {
                    "“${visionModel!!.card.name}” is a ${visionModel.card.format} text " +
                        "model — it can't analyze images. Vision needs a MediaPipe .task model. " +
                        "Open Models, filter Hugging Face to \"Vision (.task)\", and install " +
                        "Gemma 3n."
                } else {
                    "No vision model is installed yet. Open Models, switch the " +
                        "Hugging Face filter to \"Vision (.task)\", and download a " +
                        "Gemma 3n bundle. I'll be ready as soon as it's installed."
                }
                _messages.update {
                    it + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = notice,
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

    /**
     * Detect a runaway repetition loop in the streamed text. Looks at the tail
     * for three classic failure shapes: a single character repeated many times,
     * very low character diversity over a long span, and a short cluster
     * repeated consecutively. The native repeat-penalty handles most cases; this
     * is the belt-and-suspenders circuit-breaker.
     */
    private fun isRunawayRepetition(text: String): Boolean {
        val tail = text.takeLast(REP_TAIL)
        if (tail.length < 48) return false

        // (a) a single non-space char repeated 12+ times in a row
        var run = 1
        var maxRun = 1
        for (i in 1 until tail.length) {
            if (tail[i] == tail[i - 1] && !tail[i].isWhitespace()) {
                run++
                if (run > maxRun) maxRun = run
            } else {
                run = 1
            }
        }
        if (maxRun >= 12) return true

        // (b) gibberish clusters — long tail, almost no distinct characters
        val distinct = tail.filterNot { it.isWhitespace() }.toSet().size
        if (distinct in 1..5) return true

        // (c) a 2–6 char cluster repeated 6+ times at the end
        for (p in 2..6) {
            if (tail.length < p * 6) continue
            val unitStart = tail.length - p
            var count = 0
            var idx = tail.length
            while (idx - p >= 0 && tail.regionMatches(idx - p, tail, unitStart, p)) {
                count++
                idx -= p
            }
            if (count >= 6) return true
        }
        return false
    }

    /** Drop a trailing repeated run so the saved message isn't garbage. */
    private fun trimRepetition(text: String): String {
        var out = text.trimEnd()
        if (out.isNotEmpty()) {
            val last = out.last()
            var end = out.length
            while (end > 0 && out[end - 1] == last) end--
            if (out.length - end >= 6) out = out.substring(0, end)
        }
        return out.trimEnd().ifBlank {
            "I got stuck repeating myself there — could you rephrase that?"
        }
    }

    companion object {
        private const val MAX_ATTACH_DIM: Int = 1024
        private const val SKILL_TIMEOUT_MS: Long = 15_000L
        private const val REP_TAIL: Int = 80

        /** Native context window (must match cparams.n_ctx in the JNI bridge). */
        private const val CONTEXT_WINDOW_TOKENS: Int = 4096
        /** Rough English token heuristic for the context meter. */
        private const val CHARS_PER_TOKEN: Int = 4
        /** Fixed overhead for tool catalogue + JSON instruction + chat template. */
        private const val SYSTEM_SCAFFOLD_TOKENS: Int = 220

        /**
         * Strong signals that the user wants current/live info → auto web search.
         * Deliberately specific: common words like "today"/"current"/"live" are
         * excluded so ordinary chats don't trigger a needless network round-trip.
         */
        private val FRESH_INFO_KEYWORDS = listOf(
            "latest", "news", "breaking", "headlines", "right now",
            "this week", "update on", "what's happening", "whats happening",
            "stock price", "share price", "weather", "2025", "2026",
        )

        /** Queries that warrant reading more sources (vs a quick fact). */
        private val DEEP_QUERY_KEYWORDS = listOf(
            "explain", "compare", "comparison", "detailed", "in depth", "in-depth",
            "comprehensive", "guide", "how does", "why does", "pros and cons",
            "difference between", "overview of", "deep dive", "analyse", "analyze",
        )

        /** Queries where a picture genuinely helps → pull a lead image. */
        private val VISUAL_QUERY_KEYWORDS = listOf(
            "show me", "what does", "look like", "looks like", "picture", "photo",
            "image of", "images of", "diagram", "map of", "logo", "screenshot",
            "appearance",
        )

        /** Distill memories every N user turns even without a personal signal. */
        private const val LONG_CONVERSATION_DISTILL_EVERY = 8

        /** How many recent user messages the periodic sweep digests. */
        private const val RECENT_TURNS_IN_SWEEP = 4

        /** Total chars of web article text fed to the model across all sources. */
        private const val TOTAL_WEB_CHAR_BUDGET = 6000
        private const val WEB_ANSWER_TIMEOUT_MS = 20_000L

        /** Phrases that mean "write this to a file now" → auto-save the artifact. */
        private val EXPLICIT_EXPORT_KEYWORDS = listOf(
            "save", "export", "download", "write to file", "write a file",
            "create a file", "make a file", "save as", "save to",
        )

        /** An explicit filename mentioned in the request, e.g. "report.csv". */
        private val FILENAME_IN_TEXT =
            Regex("[\\w./\\\\-]+\\.(?:md|txt|csv|html|json)", RegexOption.IGNORE_CASE)

        private const val ATTACHMENT_QUALITY: Int = 85
    }
}
