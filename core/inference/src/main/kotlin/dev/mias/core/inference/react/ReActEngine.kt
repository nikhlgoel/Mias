package dev.mias.core.inference.react

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.inference.InferenceEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private fun JsonElement.asPlainString(): String? =
    (this as? JsonPrimitive)?.content

/**
 * ReAct engine — drives the Stimulus → Thought → Action → Observation loop.
 *
 * Model-agnostic: wraps any [InferenceEngine] and produces a [Flow] of
 * [ReActStep]s. Constrained decoding forces valid JSON output from the LLM.
 * Max iterations prevents infinite loops.
 */
@Singleton
class ReActEngine @Inject constructor(
    private val toolRegistry: ToolRegistry,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Run a full ReAct loop for the given prompt using the provided engine.
     *
     * @param engine The InferenceEngine to use (Gemma, MobileLLM, etc.)
     * @param systemPrompt System instructions for the model
     * @param userPrompt The user's actual input
     * @param hindsightContext Relevant memory context from Hindsight
     * @param maxIterations Safety cap on loop iterations
     */
    fun execute(
        engine: InferenceEngine,
        systemPrompt: String,
        userPrompt: String,
        hindsightContext: String = "",
        templateKind: ChatTemplateKind = ChatTemplateKind.PLAIN,
        maxIterations: Int = MAX_ITERATIONS,
    ): Flow<ReActStep> = flow {
        val conversationBuffer = StringBuilder()

        // Assemble the system block: persona + memory + tool contract. The
        // user message is kept separate so the per-model template can wrap each
        // turn in the control tokens the model was trained on.
        val toolCatalogue = toolRegistry.describeForPrompt()
        val systemBlock = buildString {
            append(systemPrompt)
            if (hindsightContext.isNotBlank()) append("\n\n").append(hindsightContext)
            if (toolCatalogue.isNotBlank()) {
                append("\n\nAvailable tools:\n").append(toolCatalogue)
                // Plain-text-first contract: a normal reply needs no JSON; the
                // schema is only for an actual tool call. (Forcing JSON via a
                // GBNF grammar previously made mobile sampling crawl.)
                append(
                    "\n\nReply directly in plain, natural language. Only if you " +
                        "genuinely need one of the tools above, instead output a single " +
                        "JSON object: {\"thought\":\"<why>\",\"action\":\"<exact tool " +
                        "name>\",\"action_input\":{...},\"is_final\":false,\"should_say\":" +
                        "\"<short note to the user>\"}.",
                )
            } else {
                append("\n\nReply directly in plain, natural language.")
            }
        }
        conversationBuffer.append(ChatTemplate.build(templateKind, systemBlock, userPrompt))

        var iterations = 0
        var lastThought = ""
        var lastShouldSay = ""
        var lastObservation = ""

        while (iterations < maxIterations) {
            iterations++

            val responseBuffer = StringBuilder()
            var errorResult: String? = null

            engine.generateStream(
                prompt = conversationBuffer.toString(),
                // Capped per turn so a slow on-device CPU can't grind through a
                // 1024-token response before the user sees anything.
                maxTokens = MAX_RESPONSE_TOKENS,
                // No GBNF grammar: token-level JSON masking made sampling
                // pathologically slow on mobile CPUs. The lenient parser
                // (StreamingReActParser / parseReActOutput) handles both plain
                // prose and the optional tool-call JSON instead.
                grammar = null,
            ).collect { result ->
                when (result) {
                    is MiasResult.Success -> {
                        // Per InferenceEngine.generateStream contract, each emission
                        // is an incremental delta — append directly.
                        val delta = result.data
                        if (delta.isNotEmpty()) {
                            responseBuffer.append(delta)
                            emit(ReActStep.TokenChunk(delta))
                        }
                    }
                    is MiasResult.Error -> {
                        errorResult = result.message
                    }
                }
            }

            if (errorResult != null) {
                emit(
                    ReActStep.FinalAnswer(
                        "I'm not able to respond right now. Details: $errorResult. " +
                            "Please try again in a moment.",
                    ),
                )
                return@flow
            }

            val finalOutput = responseBuffer.toString()
            val parsed = parseReActOutput(finalOutput)
            
            if (parsed == null) {
                // Model output wasn't valid — treat as final response
                emit(ReActStep.FinalAnswer(finalOutput))
                return@flow
            }

            // Emit the thought
            if (parsed.thought.isNotBlank()) lastThought = parsed.thought
            parsed.shouldSay?.takeIf { it.isNotBlank() }?.let { lastShouldSay = it }
            emit(ReActStep.Thought(parsed.thought))

            // Check if this is a direct response to user
            if (parsed.isFinal || parsed.action == "respond_user") {
                // `should_say` is the grammar's canonical user-facing field;
                // fall back to legacy action_input fields, then the thought.
                val response = parsed.shouldSay?.takeIf { it.isNotBlank() }
                    ?: parsed.actionInput["response"]
                    ?: parsed.actionInput["text"]
                    ?: parsed.thought
                emit(ReActStep.FinalAnswer(response))
                return@flow
            }

            val rawObservation = executeAction(parsed.action, parsed.actionInput)
            val observation = if (rawObservation.length > MAX_TOOL_OUTPUT_LENGTH) {
                rawObservation.take(MAX_TOOL_OUTPUT_LENGTH) +
                    "\n... [output truncated at $MAX_TOOL_OUTPUT_LENGTH chars]"
            } else {
                rawObservation
            }
            lastObservation = observation
            emit(ReActStep.Observation(observation))

            conversationBuffer.append("\n\nAction: ${parsed.action}")
            conversationBuffer.append("\nObservation: $observation")
            conversationBuffer.append("\n\nContinue reasoning. Respond with JSON.")
        }

        // Loop limit reached without an explicit final turn. Prefer the
        // model's most recent user-facing text; otherwise share the partial
        // reasoning rather than disappearing into silence.
        val fallback = lastShouldSay.takeIf { it.isNotBlank() }
            ?: buildString {
                append("I considered this carefully but couldn't reach a complete answer ")
                append("in the time available. Here is what I had so far:")
                if (lastThought.isNotBlank()) append("\n\nLine of thought: $lastThought")
                if (lastObservation.isNotBlank()) append("\n\nLast finding: $lastObservation")
            }
        emit(ReActStep.FinalAnswer(fallback))
    }

    /**
     * Resolve and run a tool. Tool work is dispatched to [ioDispatcher] so a
     * blocking capability (filesystem, network) never freezes the UI thread.
     * Resolution is lenient — a phrase like "Respond with the current time"
     * still maps to the `datetime` tool.
     */
    private suspend fun executeAction(action: String, input: Map<String, String>): String {
        val resolved = toolRegistry.resolve(action)
            ?: return "Tool '$action' is not available. Use exactly one of: " +
                "${toolRegistry.availableTools()}, or set action to \"respond_user\"."
        val handler = toolRegistry.get(resolved)
            ?: return "Tool '$resolved' is not available."
        return withContext(ioDispatcher) {
            try {
                handler.execute(input)
            } catch (e: Exception) {
                "Tool error: ${e.message}"
            }
        }
    }

    /** Parse the model's JSON output into structured ReAct components. */
    private fun parseReActOutput(raw: String): ParsedReAct? {
        val jsonStr = extractJson(raw) ?: return null
        return try {
            val element = json.parseToJsonElement(jsonStr).jsonObject
            val thought = element["thought"]?.asPlainString().orEmpty()
            val action = element["action"]?.asPlainString() ?: "respond_user"
            val isFinal = element["is_final"]?.let { e ->
                (e as? JsonPrimitive)?.booleanOrNull
                    ?: e.asPlainString()?.toBooleanStrictOrNull()
            } ?: false

            // action_input is a JSON object of tool arguments. Primitive values
            // (string/number/bool) come through as their plain content; nested
            // objects/arrays are kept as compact JSON strings so a tool can
            // re-parse structured args rather than losing them.
            val actionInput = (element["action_input"] as? JsonObject)
                ?.mapValues { (_, v) ->
                    when (v) {
                        is JsonPrimitive -> v.content
                        else -> v.toString()
                    }
                }
                .orEmpty()

            val shouldSay = element["should_say"]?.asPlainString()

            ParsedReAct(thought, action, actionInput, isFinal, shouldSay)
        } catch (_: Exception) {
            null
        }
    }

    /** Slice the first balanced top-level `{ … }` from possibly-noisy model output. */
    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private data class ParsedReAct(
        val thought: String,
        val action: String,
        val actionInput: Map<String, String>,
        val isFinal: Boolean,
        val shouldSay: String? = null,
    )

    companion object {
        // Hard cap on agent turns: initial decision + at most two tool
        // round-trips. Prevents infinite loops when a tool keeps erroring.
        const val MAX_ITERATIONS = 3
        const val MAX_TOOL_OUTPUT_LENGTH = 2000

        /** Per-turn generation cap. Keeps worst-case latency bounded on CPU-only devices. */
        const val MAX_RESPONSE_TOKENS = 512

        /**
         * GBNF grammar constraining model output to the ReAct JSON schema.
         * Passed to the native llama.cpp sampler (see [InferenceEngine.generateStream]'s
         * `grammar` param) so weak on-device models cannot emit malformed JSON.
         *
         * `action_input` is a full JSON **object** (recursive values: string,
         * number, boolean, null, array, nested object) so tool calls can carry
         * real structured arguments — e.g. `{"path":"/a.txt","lines":[1,2]}` —
         * not just a flat string. Use `{}` when no tool is needed. This is the
         * standard llama.cpp JSON grammar shape, proven with small models.
         *
         * `char` accepts any byte except `"`/`\` plus standard JSON escapes
         * (including `\uXXXX`), so tabs, newlines, and multi-byte UTF-8 in
         * string values are handled without breaking the constraint.
         */
        val REACT_GRAMMAR = """
            root    ::= object
            object  ::= "{" ws "\"thought\":" ws string "," ws "\"action\":" ws string "," ws "\"action_input\":" ws args "," ws "\"is_final\":" ws boolean "," ws "\"should_say\":" ws string ws "}"
            args    ::= "{" ws ( member ( ws "," ws member )* )? ws "}"
            member  ::= string ws ":" ws value
            value   ::= string | number | boolean | "null" | array | args
            array   ::= "[" ws ( value ( ws "," ws value )* )? ws "]"
            string  ::= "\"" char* "\""
            char    ::= [^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F])
            number  ::= "-"? ( "0" | [1-9] [0-9]* ) ( "." [0-9]+ )? ( [eE] [-+]? [0-9]+ )?
            boolean ::= "true" | "false"
            ws      ::= [ \t\n\r]*
        """.trimIndent()
    }
}
