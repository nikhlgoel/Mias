package dev.mias.core.data

import dev.mias.core.common.MiasResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for local conversation storage.
 * Data never leaves the device.
 */
interface ConversationRepository {
    fun getConversations(): Flow<List<Conversation>>
    suspend fun getConversation(id: String): MiasResult<Conversation>
    suspend fun saveConversation(conversation: Conversation): MiasResult<Unit>
    suspend fun deleteConversation(id: String): MiasResult<Unit>
}

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<Message>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Message(
    val id: String,
    val conversationId: String,
    val role: Role,
    val content: String,
    val timestamp: Long,
)

enum class Role { USER, ASSISTANT }
