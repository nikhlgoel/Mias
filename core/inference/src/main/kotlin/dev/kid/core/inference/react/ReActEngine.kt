package dev.kid.core.inference.react

import dev.kid.core.common.KidResult
import dev.kid.core.inference.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

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

        while (iterations < maxIterations) {
            iterations++

            val responseBuffer = StringBuilder()
            var errorResult: String? = null

            engine.generateStream(
                prompt = conversationBuffer.toString(),
                maxTokens = 1024,
            ).collect { result ->
                when (result) {
                    is KidResult.Success -> {
                        // Extract just the diff text if possible to stream, 
                        // or just stream the raw token. For simplicity we assume
                        // the engine emits the cumulative string, so we calculate the diff.
                        val newStr = result.data
                        val diff = newStr.removePrefix(responseBuffer.toString())
                        if (diff.isNotEmpty()) {
                            responseBuffer.append(diff)
                            emit(ReActStep.TokenChunk(diff))
                        }
                    }
                    is KidResult.Error -> {
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
            emit(ReActStep.Thought(parsed.thought))

            // Check if this is a direct response to user
            if (parsed.isFinal || parsed.action == "respond_user") {
                val response = parsed.actionInput["response"]
                    ?: parsed.actionInput["text"]
                    ?: parsed.thought
                emit(ReActStep.FinalAnswer(response))
                return@flow
            }

            // Execute the action
            emit(ReActStep.Action(parsed.action, parsed.actionInput))

                    val rawObservation = executeAction(parsed.action, parsed.actionInput)
                    val observation = if (rawObservation.length > MAX_TOOL_OUTPUT_LENGTH) {
                        rawObservation.take(MAX_TOOL_OUTPUT_LENGTH) +
                            "\n... [output truncated at $MAX_TOOL_OUTPUT_LENGTH chars]"
                    } else {
                        rawObservation
                    }
                    emit(ReActStep.Observation(observation))

                    // Feed observation back into the conversation
                    conversationBuffer.append("\n\nAction: ${parsed.action}")
                    conversationBuffer.append("\nObservation: $observation")
                    conversationBuffer.append("\n\nContinue reasoning. Respond with JSON.")
        }

        // Max iterations reached
        emit(
            ReActStep.FinalAnswer(
                "I've been thinking about this for a while. " +
                    "Let me give you what I have so far.",
            ),
        )
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
        return try {
            // Extract JSON from the response (model may include surrounding text)
            val jsonStr = extractJson(raw) ?: return null
            // Simple manual JSON parsing to avoid kotlinx.serialization dependency in inference
            val thought = extractField(jsonStr, "thought") ?: ""
            val action = extractField(jsonStr, "action") ?: "respond_user"
            val isFinal = jsonStr.contains("\"is_final\"") &&
                (jsonStr.contains("\"is_final\": true") || jsonStr.contains("\"is_final\":true"))

            // Extract action_input as a simple map
            val actionInput = extractActionInput(jsonStr)

            ParsedReAct(thought, action, actionInput, isFinal)
        } catch (_: Exception) {
            null
        }
    }

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

    private fun extractField(json: String, field: String): String? {
        val pattern = "\"$field\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractActionInput(json: String): Map<String, String> {
        val inputStart = json.indexOf("\"action_input\"")
        if (inputStart == -1) return emptyMap()
        val objStart = json.indexOf('{', inputStart + 14)
        if (objStart == -1) return emptyMap()

        var depth = 0
        var end = objStart
        for (i in objStart until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }

        val inputJson = json.substring(objStart, end + 1)
        val result = mutableMapOf<String, String>()
        val fieldPattern = "\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        fieldPattern.findAll(inputJson).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
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
