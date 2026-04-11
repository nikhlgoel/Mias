package dev.kid.core.data.repository

import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import dev.kid.core.common.runCatchingKid
import dev.kid.core.data.Conversation
import dev.kid.core.data.ConversationRepository
import dev.kid.core.data.Message
import dev.kid.core.data.Role
import dev.kid.core.data.db.dao.ConversationDao
import dev.kid.core.data.db.entity.ConversationEntity
import dev.kid.core.data.db.entity.MessageEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val dao: ConversationDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ConversationRepository {

    override fun getConversations(): Flow<List<Conversation>> =
        dao.observeAll()
            .map { entities ->
                entities.map { entity ->
                    val messages = dao.getMessages(entity.id).map { it.toDomain() }
                    entity.toDomain(messages)
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun getConversation(id: String): KidResult<Conversation> =
        withContext(ioDispatcher) {
            runCatchingKid {
                val entity = dao.getById(id)
                    ?: throw NoSuchElementException("Conversation $id not found")
                val messages = dao.getMessages(id).map { it.toDomain() }
                entity.toDomain(messages)
            }
        }

    override suspend fun saveConversation(conversation: Conversation): KidResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingKid {
                dao.upsertWithMessages(
                    conversation.toEntity(),
                    conversation.messages.map { it.toEntity() },
                )
            }
        }

    override suspend fun deleteConversation(id: String): KidResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingKid { dao.deleteById(id) }
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
)

private fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    timestamp = timestamp,
)
