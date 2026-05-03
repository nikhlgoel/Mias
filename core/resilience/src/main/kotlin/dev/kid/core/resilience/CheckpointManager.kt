package dev.kid.core.resilience

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kid.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.checkpointStore: DataStore<Preferences>
    by preferencesDataStore(name = "kid_checkpoints")

/**
 * Checkpoint system for crash recovery and session resumption.
 * Persists conversation state so the AI can resume after app kill/crash.
 */
@Singleton
class CheckpointManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Save a conversation checkpoint for crash recovery. */
    suspend fun saveCheckpoint(checkpoint: Checkpoint) = withContext(ioDispatcher) {
        context.checkpointStore.edit { prefs ->
            prefs[KEY_ACTIVE_CONVERSATION] = checkpoint.conversationId
            prefs[KEY_LAST_USER_MSG] = checkpoint.lastUserMessage
            prefs[KEY_PENDING_RESPONSE] = checkpoint.pendingResponse ?: ""
            prefs[KEY_TIMESTAMP] = checkpoint.timestamp
            prefs[KEY_STATE_JSON] = json.encodeToString(
                CheckpointState.serializer(),
                checkpoint.state,
            )
        }
    }

    /** Restore the last checkpoint (returns null if none or expired). */
    suspend fun restoreCheckpoint(): Checkpoint? = withContext(ioDispatcher) {
        val prefs = context.checkpointStore.data.first()
        val conversationId = prefs[KEY_ACTIVE_CONVERSATION] ?: return@withContext null
        val timestamp = prefs[KEY_TIMESTAMP] ?: return@withContext null

        // Expire checkpoints older than 1 hour
        if (System.currentTimeMillis() - timestamp > EXPIRY_MS) {
            clearCheckpoint()
            return@withContext null
        }

        Checkpoint(
            conversationId = conversationId,
            lastUserMessage = prefs[KEY_LAST_USER_MSG] ?: "",
            pendingResponse = prefs[KEY_PENDING_RESPONSE]?.takeIf { it.isNotBlank() },
            timestamp = timestamp,
            state = prefs[KEY_STATE_JSON]?.let {
                try {
                    json.decodeFromString(CheckpointState.serializer(), it)
                } catch (_: Exception) {
                    CheckpointState()
                }
            } ?: CheckpointState(),
        )
    }

    /** Clear the active checkpoint (called after successful response delivery). */
    suspend fun clearCheckpoint() = withContext(ioDispatcher) {
        context.checkpointStore.edit { it.clear() }
    }

    companion object {
        private val KEY_ACTIVE_CONVERSATION = stringPreferencesKey("active_conversation")
        private val KEY_LAST_USER_MSG = stringPreferencesKey("last_user_msg")
        private val KEY_PENDING_RESPONSE = stringPreferencesKey("pending_response")
        private val KEY_TIMESTAMP = longPreferencesKey("checkpoint_ts")
        private val KEY_STATE_JSON = stringPreferencesKey("state_json")
        private const val EXPIRY_MS = 3_600_000L // 1 hour
    }
}

data class Checkpoint(
    val conversationId: String,
    val lastUserMessage: String,
    val pendingResponse: String?,
    val timestamp: Long,
    val state: CheckpointState = CheckpointState(),
)

@Serializable
data class CheckpointState(
    val activeModelId: String? = null,
    val reactIterations: Int = 0,
    val partialToolResults: List<String> = emptyList(),
)
