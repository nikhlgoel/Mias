package dev.mias.core.inference.react

import dev.mias.core.common.MiasResult
import dev.mias.core.inference.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
        maxIterations: Int = MAX_ITERATIONS,
    ): Flow<ReActStep> = flow {
        val conversationBuffer = StringBuilder()

        // Build the full prompt
        conversationBuffer.append(systemPrompt)
        if (hindsightContext.isNotBlank()) {
            conversationBuffer.append("\n\n$hindsightContext")
        }
        conversationBuffer.append("\n\nUser: $userPrompt")
        conversationBuffer.append("\n\nRespond with a JSON object containing: thought, action, action_input, is_final")

        var iterations = 0
        var lastThought = ""
        var lastObservation = ""

        while (iterations < maxIterations) {
            iterations++

            val responseBuffer = StringBuilder()
            var errorResult: String? = null

            engine.generateStream(
                prompt = conversationBuffer.toString(),
                maxTokens = 1024,
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
                        "I'm having trouble thinking right now. Error: $errorResult"
                    )
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
            emit(ReActStep.Thought(parsed.thought))

            // Check if this is a direct response to user
            if (parsed.isFinal || parsed.action == "respond_user") {
                val response = parsed.actionInput["response"]
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

        // Max iterations reached — surface whatever partial reasoning we have
        // rather than a generic "let me give you what I have" with nothing attached.
        val partial = buildString {
            append("I worked on this for $maxIterations rounds without converging.")
            if (lastThought.isNotBlank()) append("\n\nLast thought: $lastThought")
            if (lastObservation.isNotBlank()) append("\n\nLast observation: $lastObservation")
        }
        emit(ReActStep.FinalAnswer(partial))
    }

    private suspend fun executeAction(tool: String, input: Map<String, String>): String {
        val handler = toolRegistry.get(tool)
            ?: return "Tool '$tool' not available. Available: ${toolRegistry.availableTools()}"
        return try {
            handler.execute(input)
        } catch (e: Exception) {
            "Tool error: ${e.message}"
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

            val actionInput = (element["action_input"] as? JsonObject)
                ?.mapValues { (_, v) -> v.asPlainString().orEmpty() }
                .orEmpty()

            ParsedReAct(thought, action, actionInput, isFinal)
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
    )

    companion object {
        const val MAX_ITERATIONS = 7
        const val MAX_TOOL_OUTPUT_LENGTH = 2000
    }
}
