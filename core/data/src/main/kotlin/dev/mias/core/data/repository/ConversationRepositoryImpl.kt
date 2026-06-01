package dev.mias.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.runCatchingMias
import dev.mias.core.data.Conversation
import dev.mias.core.data.ConversationRepository
import dev.mias.core.data.Message
import dev.mias.core.data.Role
import dev.mias.core.data.db.dao.ConversationDao
import dev.mias.core.data.db.entity.ConversationEntity
import dev.mias.core.data.db.entity.MessageEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ConversationDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ConversationRepository {

    override fun getConversations(): Flow<List<Conversation>> =
        dao.observeAll()
            .map { entities ->
                entities.map { entity ->
                    try {
                        val messages = dao.getMessages(entity.id).map { it.toDomain() }
                        entity.toDomain(messages)
                    } catch (e: Exception) {
                        // Log error and return conversation without messages
                        android.util.Log.e("ConversationRepo", "Error loading messages for ${entity.id}", e)
                        entity.toDomain(emptyList())
                    }
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun getConversation(id: String): MiasResult<Conversation> =
        withContext(ioDispatcher) {
            runCatchingMias {
                val entity = dao.getById(id)
                    ?: throw NoSuchElementException("Conversation $id not found")
                val messages = dao.getMessages(id).map { it.toDomain() }
                entity.toDomain(messages)
            }
        }

    override suspend fun saveConversation(conversation: Conversation): MiasResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingMias {
                dao.upsertWithMessages(
                    conversation.toEntity(),
                    conversation.messages.map { it.toEntity() },
                )
            }
        }

    override suspend fun deleteConversation(id: String): MiasResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingMias {
                dao.deleteById(id)
                // Drop any image attachments stored on disk for this
                // conversation. The DB cascade handles message rows; the
                // file tree we wrote alongside them is our responsibility.
                val dir = File(context.filesDir, "conversations/$id")
                if (dir.exists()) {
                    runCatching { dir.deleteRecursively() }
                }
            }
        }

    /** Get a reactive stream of messages for a conversation. */
    fun observeMessages(conversationId: String): Flow<List<Message>> =
        dao.observeMessages(conversationId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
}

private fun ConversationEntity.toDomain(messages: List<Message> = emptyList()) = Conversation(
    id = id,
    title = title,
    messages = messages,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun MessageEntity.toDomain() = Message(
    id = id,
    conversationId = conversationId,
    role = Role.valueOf(role),
    content = content,
    timestamp = timestamp,
    imagePath = imagePath,
    reasoningText = reasoningText,
)

private fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    timestamp = timestamp,
    imagePath = imagePath,
    reasoningText = reasoningText,
)
