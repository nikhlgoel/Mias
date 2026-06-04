package dev.mias.core.inference.react

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Split of a raw model response into the part shown to (and remembered for)
 * the user, and the internal reasoning that must never re-enter the model's
 * context.
 */
data class SanitizedResponse(
    /** Clean, user-visible conversational text. Never raw JSON. */
    val chatText: String,
    /** Parsed thought / structural detail. For display/debug only; never fed back. */
    val reasoningText: String?,
)

/**
 * Turns raw model output — which may be a JSON object like
 * `{ "thought": "...", "should_say": "..." }`, JSON inside markdown fences,
 * or plain prose — into a [SanitizedResponse].
 *
 * Why this exists: persisting the raw stream (reasoning JSON + control
 * headers) as the assistant's conversational history poisons the context.
 * On the next turn the model sees raw JSON in its own history and collapses
 * into emitting only JSON. We store and replay **only** [SanitizedResponse.chatText].
 */
object ResponseSanitizer {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Field names a model might use for the user-facing reply, in priority order.
    private val CONVERSATIONAL_KEYS = listOf(
        "should_say", "response", "answer", "reply", "say", "final_answer",
        "message", "text", "content",
    )
    private val REASONING_KEYS = listOf("thought", "reasoning", "thinking", "plan")

    fun sanitize(raw: String): SanitizedResponse {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return SanitizedResponse("", null)

        extractJsonObject(trimmed)?.let { jsonStr ->
            runCatching {
                val obj = json.parseToJsonElement(jsonStr).jsonObject
                val say = firstStringField(obj, CONVERSATIONAL_KEYS)
                val thought = firstStringField(obj, REASONING_KEYS)

                if (!say.isNullOrBlank()) {
                    return SanitizedResponse(say.trim(), buildReasoning(thought, jsonStr))
                }
                // JSON with no conversational field — the thought is the best
                // user-facing text we have; keep the raw JSON as reasoning.
                if (!thought.isNullOrBlank()) {
                    return SanitizedResponse(thought.trim(), jsonStr)
                }
            }
        }

        // No cleanly-parseable JSON. The model may still have emitted *malformed*
        // JSON (truncated braces, leading prose, etc.) where a clean parse fails
        // but the should_say value is recoverable — strip the residue.
        val cleaned = stripJsonResidue(cleanupPlain(trimmed))
        return SanitizedResponse(cleaned, null)
    }

    /**
     * Last-resort cleaner for text that isn't valid JSON but carries JSON
     * residue. Pulls out a `should_say` value if present; otherwise removes
     * dangling braces, control keys (`is_final`, `action`, `thought`, …) and
     * the punctuation around them, leaving only the conversational text.
     */
    fun stripJsonResidue(input: String): String {
        if (input.isBlank()) return input
        // 1. If a should_say value is present, that *is* the reply.
        SHOULD_SAY_VALUE.find(input)?.groups?.get(1)?.value?.let { value ->
            return unescapeJson(value).trim()
        }
        // 2. Otherwise scrub dangling structural fragments.
        var out = input
            .replace(MARKDOWN_JSON_FENCE, "")
            .replace(STRUCTURAL_KEY, "")     // "is_final": false , "action": "x" , …
            .replace(DANGLING_PUNCT, " ")    // leftover { } [ ] : , around them
            .replace(Regex("\\s+"), " ")
            .trim()
        // Trim a leading/trailing brace or comma the regexes may have left.
        out = out.trim('{', '}', ',', ' ', '"')
        return out.trim()
    }

    private fun unescapeJson(s: String): String = s
        .replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")

    private val SHOULD_SAY_VALUE =
        Regex("\"should_say\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val MARKDOWN_JSON_FENCE = Regex("```[a-zA-Z]*|```")
    private val STRUCTURAL_KEY =
        Regex("\"(is_final|action|action_input|thought|response|should_say)\"\\s*:\\s*(\"[^\"]*\"|true|false|null|\\{[^}]*\\}|\\[[^\\]]*\\])?")
    private val DANGLING_PUNCT = Regex("[{}\\[\\]]|^[\\s,:]+|[\\s,:]+$")

    private fun firstStringField(obj: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val v = obj[key]
            if (v is JsonPrimitive && v.isString) {
                val s = v.content
                if (s.isNotBlank()) return s
            }
        }
        return null
    }

    private fun buildReasoning(thought: String?, jsonStr: String): String =
        if (!thought.isNullOrBlank()) thought.trim() else jsonStr

    /** First balanced top-level `{ … }`, ignoring markdown code fences. */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Fallback for non-JSON output: drop markdown code fences and any leading
     * role/control headers, leaving clean conversational prose. If a stray
     * unparsed JSON-looking block remains, strip it rather than show braces.
     */
    private fun cleanupPlain(text: String): String {
        var out = text
            .replace(CODE_FENCE_REGEX, "")
            .replace(CONTROL_HEADER_REGEX, "")
            .trim()
        // Remove a leading "Assistant:" / "AI:" style speaker label.
        out = out.replace(SPEAKER_LABEL_REGEX, "").trim()
        // If what's left still opens with a brace, it's an unparsed structural
        // block — strip to the first sentence-like content after it if any.
        if (out.startsWith("{")) {
            val afterBrace = out.substringAfter('}', "").trim()
            out = afterBrace.ifBlank { out.removePrefix("{").removeSuffix("}").trim() }
        }
        return out
    }

    private val CODE_FENCE_REGEX = "```[a-zA-Z]*\\n?|```".toRegex()
    private val CONTROL_HEADER_REGEX =
        "<\\|[a-zA-Z_]+\\|>|## Instructions?\\b".toRegex()
    private val SPEAKER_LABEL_REGEX = "^(Assistant|AI|Mias)\\s*:\\s*".toRegex(RegexOption.IGNORE_CASE)
}
