package dev.kid.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kid.core.common.getOrDefault
import dev.kid.core.common.getOrNull
import dev.kid.core.common.model.BrainState
import dev.kid.core.common.model.CognitionState
import dev.kid.core.common.model.Stimulus
import dev.kid.core.common.model.StimulusType
import dev.kid.core.data.Conversation
import dev.kid.core.data.ConversationRepository
import dev.kid.core.data.Message
import dev.kid.core.data.Role
import dev.kid.core.data.hindsight.HindsightMemory
import dev.kid.core.inference.orchestrator.InferenceOrchestrator
import dev.kid.core.inference.react.ReActStep
import dev.kid.core.language.IntentExtractor
import dev.kid.core.soul.SoulEngine
import dev.kid.core.ui.components.BubbleType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String,
    val text: String,
    val type: BubbleType,
    val timestamp: String,
    val isStreaming: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isProcessing: Boolean = false,
    val brainState: BrainState = BrainState.GEMMA_NPU,
    val cognitionState: CognitionState = CognitionState.IDLE,
    val conversationTitle: String = "New Conversation",
    val showReActSteps: Boolean = false,
)

sealed interface ChatEvent {
    data object ScrollToBottom : ChatEvent
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val orchestrator: InferenceOrchestrator,
    private val soulEngine: SoulEngine,
    private val hindsightMemory: HindsightMemory,
    private val intentExtractor: IntentExtractor,
    private val conversationRepository: ConversationRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: String =
        savedStateHandle.get<String>("conversationId") ?: UUID.randomUUID().toString()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _inputText = MutableStateFlow("")
    private val _isProcessing = MutableStateFlow(false)
    private val _showReActSteps = MutableStateFlow(false)

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ChatUiState> = combine(
        _messages,
        _inputText,
        _isProcessing,
        orchestrator.brainState,
        orchestrator.cognitionState,
    ) { messages, input, processing, brain, cognition ->
        ChatUiState(
            messages = messages,
            inputText = input,
            isProcessing = processing,
            brainState = brain,
            cognitionState = cognition,
            showReActSteps = _showReActSteps.value,
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
        if (text.isBlank() || _isProcessing.value) return

        val structuredIntent = intentExtractor.extract(text)
        val cleanedText = structuredIntent.cleanedText.ifBlank { text }

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

        // Process with Soul + ReAct
        soulEngine.processUserMessage(cleanedText)

        viewModelScope.launch {
            // Store fact in Hindsight
            hindsightMemory.storeFact(
                content = "User said: $cleanedText",
                conversationId = conversationId,
            )

            // Get Hindsight context
            val hindsightContext = hindsightMemory.query(structuredIntent)
                .getOrNull()
                ?.toPromptString()
                ?: ""

            val personalityPrompt = soulEngine.getPersonalityPrompt()
            val systemPrompt = InferenceOrchestrator.DEFAULT_SYSTEM_PROMPT +
                "\n\n" + personalityPrompt

            val metadata = buildMap<String, String> {
                put("intent_type", structuredIntent.intentType.value)
                put("intent_confidence", structuredIntent.confidence.toString())
                structuredIntent.actionHint?.let { put("action_hint", it) }
                structuredIntent.modifiers.forEachIndexed { index, tag ->
                    put("modifier_${index + 1}", tag)
                }
                structuredIntent.entities.forEach { (k, v) ->
                    put("entity_$k", v)
                }
            }

            val stimulus = Stimulus(
                type = StimulusType.USER_MESSAGE,
                content = cleanedText,
                metadata = metadata,
            )

            // Collect the ReAct flow
            var finalResponse = ""

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
                        // Observations are internal — not shown
                    }

                    is ReActStep.FinalAnswer -> {
                        finalResponse = step.response
                        val kidMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            text = step.response,
                            type = BubbleType.KID,
                            timestamp = formatTime(System.currentTimeMillis()),
                        )
                        _messages.update { it + kidMsg }
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
                        // Progressive token display — update last message
                    }
                }
            }

            // Store Kid's response in Hindsight
            if (finalResponse.isNotBlank()) {
                hindsightMemory.storeFact(
                    content = "Kid responded: $finalResponse",
                    conversationId = conversationId,
                )
            }

            // Persist conversation
            saveConversation()
            _isProcessing.value = false
        }
    }

    fun toggleReActSteps() {
        _showReActSteps.update { !it }
    }

    private fun loadExistingConversation() {
        viewModelScope.launch {
            val result = conversationRepository.getConversation(conversationId)
            val conversation = result.getOrNull() ?: return@launch
            _messages.value = conversation.messages.map { msg ->
                ChatMessage(
                    id = msg.id,
                    text = msg.content,
                    type = if (msg.role == Role.USER) BubbleType.USER else BubbleType.KID,
                    timestamp = formatTime(msg.timestamp),
                )
            }
        }
    }

    private suspend fun saveConversation() {
        val msgs = _messages.value
        if (msgs.isEmpty()) return

        val conversation = Conversation(
            id = conversationId,
            title = msgs.firstOrNull { it.type == BubbleType.USER }?.text?.take(50)
                ?: "Conversation",
            messages = msgs.mapIndexed { i, msg ->
                Message(
                    id = msg.id,
                    conversationId = conversationId,
                    role = if (msg.type == BubbleType.USER) Role.USER else Role.ASSISTANT,
                    content = msg.text,
                    timestamp = System.currentTimeMillis() - (msgs.size - i) * 1000L,
                )
            },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        conversationRepository.saveConversation(conversation)
    }

    private fun formatTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}
