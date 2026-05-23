package dev.mias.app.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mias.core.data.Conversation
import dev.mias.core.data.ConversationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatsUiState(
    val conversations: List<Conversation> = emptyList(),
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val repository: ConversationRepository,
) : ViewModel() {

    val uiState: StateFlow<ChatsUiState> = repository.getConversations()
        .map { list -> ChatsUiState(conversations = list.sortedByDescending { it.updatedAt }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatsUiState(),
        )

    fun deleteConversation(id: String) {
        viewModelScope.launch { repository.deleteConversation(id) }
    }
}
