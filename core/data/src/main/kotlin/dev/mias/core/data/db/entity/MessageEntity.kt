package dev.mias.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    /** Clean, user-visible conversational text. Never raw reasoning JSON. */
    val content: String,
    val brainState: String? = null,
    val timestamp: Long,
    /** Absolute path to an image attached to this message, if any. */
    val imagePath: String? = null,
    /**
     * Parsed reasoning / thought / structural detail for an assistant turn.
     * Display/debug only — never replayed into the model's context.
     */
    val reasoningText: String? = null,
)
