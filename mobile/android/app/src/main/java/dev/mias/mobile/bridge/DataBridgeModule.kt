package dev.mias.mobile.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mias.core.common.MiasResult
import dev.mias.core.data.Conversation
import dev.mias.core.data.ConversationRepository
import dev.mias.core.data.Message
import dev.mias.core.data.Role
import dev.mias.core.data.hindsight.HindsightMemory
import dev.mias.core.data.rag.DocumentRepository
import dev.mias.core.data.rag.RetrievedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conversations + retrieval context for the RN app. JSON strings cross the
 * bridge (stable contract, no Room schema duplicated in JS — the TS side maps
 * them to its own types).
 */
class DataBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DataEntryPoint {
        fun conversations(): ConversationRepository
        fun hindsight(): HindsightMemory
        fun documents(): DocumentRepository
    }

    private val entry by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            DataEntryPoint::class.java,
        )
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getName(): String = NAME

    /** Newest-first conversation summaries. */
    @ReactMethod
    fun listConversations(promise: Promise) {
        scope.launch {
            try {
                val list = entry.conversations().getConversations().first()
                val arr = JSONArray()
                for (c in list.sortedByDescending { it.updatedAt }) {
                    arr.put(
                        JSONObject()
                            .put("id", c.id)
                            .put("title", c.title)
                            .put("updatedAt", c.updatedAt)
                            .put("messageCount", c.messages.size),
                    )
                }
                promise.resolve(arr.toString())
            } catch (t: Throwable) {
                promise.reject("data_list", t.message, t)
            }
        }
    }

    @ReactMethod
    fun getConversation(id: String, promise: Promise) {
        scope.launch {
            when (val res = entry.conversations().getConversation(id)) {
                is MiasResult.Success -> promise.resolve(toJson(res.data).toString())
                is MiasResult.Error -> promise.resolve(null) // absent, not an error
            }
        }
    }

    /**
     * Persist a conversation. Input JSON mirrors [toJson]; role is
     * "user"/"assistant" (thought/action/error bubbles are UI-only and must not
     * be persisted or replayed).
     */
    @ReactMethod
    fun saveConversation(json: String, promise: Promise) {
        scope.launch {
            try {
                val o = JSONObject(json)
                val msgs = o.getJSONArray("messages")
                val conversationId = o.getString("id")
                val messages = ArrayList<Message>(msgs.length())
                for (i in 0 until msgs.length()) {
                    val m = msgs.getJSONObject(i)
                    messages.add(
                        Message(
                            id = m.getString("id"),
                            conversationId = conversationId,
                            role = if (m.getString("role") == "user") Role.USER else Role.ASSISTANT,
                            content = m.getString("content"),
                            timestamp = m.getLong("timestamp"),
                            reasoningText = m.optString("reasoning").ifBlank { null },
                        ),
                    )
                }
                val conversation = Conversation(
                    id = conversationId,
                    title = o.getString("title"),
                    messages = messages,
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                )
                when (val res = entry.conversations().saveConversation(conversation)) {
                    is MiasResult.Success -> promise.resolve(null)
                    is MiasResult.Error -> promise.reject("data_save", res.message)
                }
            } catch (t: Throwable) {
                promise.reject("data_save", t.message, t)
            }
        }
    }

    @ReactMethod
    fun deleteConversation(id: String, promise: Promise) {
        scope.launch {
            when (val res = entry.conversations().deleteConversation(id)) {
                is MiasResult.Success -> promise.resolve(null)
                is MiasResult.Error -> promise.reject("data_delete", res.message)
            }
        }
    }

    /**
     * Retrieval context for a turn: Hindsight memories + RAG passages (scoped
     * global + this conversation). Optionally stores the user utterance as a
     * fact first (the Kotlin turn did both). Best-effort: failures yield empty
     * strings, never a failed turn.
     */
    @ReactMethod
    fun getTurnContext(query: String, conversationId: String, storeUserFact: Boolean, promise: Promise) {
        scope.launch {
            try {
                if (storeUserFact) {
                    runCatching { entry.hindsight().storeFact("User said: $query", conversationId = conversationId) }
                }
                val hindsight = runCatching {
                    (entry.hindsight().query(query) as? MiasResult.Success)?.data?.toPromptString().orEmpty()
                }.getOrDefault("")
                val rag: RetrievedContext = runCatching {
                    entry.documents().retrieve(query, conversationId)
                }.getOrDefault(RetrievedContext.EMPTY)
                val out = JSONObject()
                    .put("hindsight", hindsight)
                    .put("rag", rag.promptText)
                    .put("sources", JSONArray(rag.sources))
                promise.resolve(out.toString())
            } catch (t: Throwable) {
                promise.resolve(JSONObject().put("hindsight", "").put("rag", "").put("sources", JSONArray()).toString())
            }
        }
    }

    /** Global document count in the knowledge base. */
    @ReactMethod
    fun documentCount(promise: Promise) {
        scope.launch {
            promise.resolve(runCatching { entry.documents().observeDocumentCount().first() }.getOrDefault(0))
        }
    }

    /** Add a document (name + plain text) to the global knowledge base (RAG). */
    @ReactMethod
    fun ingestDocument(name: String, text: String, promise: Promise) {
        scope.launch {
            when (val res = runCatching { entry.documents().ingest(name, text, null) }.getOrElse {
                MiasResult.Error(it.message ?: "ingest failed")
            }) {
                is MiasResult.Success -> promise.resolve(res.data.name)
                is MiasResult.Error -> promise.reject("data_ingest", res.message)
            }
        }
    }

    /** Store the assistant's reply as a Hindsight fact (fire-and-forget semantics). */
    @ReactMethod
    fun storeAssistantFact(text: String, conversationId: String, promise: Promise) {
        scope.launch {
            runCatching { entry.hindsight().storeFact("Mias responded: $text", conversationId = conversationId) }
            promise.resolve(null)
        }
    }

    private fun toJson(c: Conversation): JSONObject {
        val msgs = JSONArray()
        for (m in c.messages) {
            msgs.put(
                JSONObject()
                    .put("id", m.id)
                    .put("role", if (m.role == Role.USER) "user" else "assistant")
                    .put("content", m.content)
                    .put("timestamp", m.timestamp)
                    .apply { m.reasoningText?.let { put("reasoning", it) } },
            )
        }
        return JSONObject()
            .put("id", c.id)
            .put("title", c.title)
            .put("createdAt", c.createdAt)
            .put("updatedAt", c.updatedAt)
            .put("messages", msgs)
    }

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    companion object {
        const val NAME = "MiasData"
    }
}
