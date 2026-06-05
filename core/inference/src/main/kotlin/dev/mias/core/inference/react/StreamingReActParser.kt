package dev.mias.core.inference.react

/**
 * Real-time splitter for the ReAct JSON stream.
 *
 * The grammar forces the model to emit
 * `{"thought":"…","action":"…","action_input":{…},"is_final":…,"should_say":"…"}`.
 * As tokens arrive we don't have a complete JSON object yet, so a strict
 * parser can't be used. Instead we run two lenient regexes over the growing
 * buffer and surface:
 *   - [StreamState.thinking]  — the current `thought` value (for the
 *     collapsible "Thinking Process" box), and
 *   - [StreamState.visible]   — the current `should_say` value (the clean
 *     reply shown in the bubble body).
 *
 * Both regexes match a JSON string body that may be unterminated (still
 * streaming), so partial values appear live. JSON escapes are decoded so the
 * UI never shows `\n` / `\"` artifacts.
 */
object StreamingReActParser {

    data class StreamState(val thinking: String, val visible: String)

    // "<key>" : "<body>"  where body allows escaped chars and may be unterminated.
    private val THOUGHT = Regex("\"thought\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)")
    private val SHOULD_SAY = Regex("\"should_say\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)")

    private val FORM_FEED: Char = 12.toChar()

    // Keys unique to our ReAct schema. Kept deliberately specific (not generic
    // words like "action"/"thought") so a plain reply that merely quotes such a
    // word isn't misread as JSON and hidden from the bubble.
    private val JSON_MARKERS = listOf(
        "\"should_say\"", "\"is_final\"", "\"action_input\"",
    )

    fun parse(buffer: String): StreamState {
        val thinking = THOUGHT.find(buffer)?.groupValues?.getOrNull(1)?.let { unescape(it) }.orEmpty()
        val say = SHOULD_SAY.find(buffer)?.groupValues?.getOrNull(1)?.let { unescape(it) }

        val looksJson = buffer.trimStart().startsWith("{") ||
            JSON_MARKERS.any { it in buffer }

        val visible = when {
            // JSON form: surface ONLY the should_say value, never the structure.
            say != null -> say
            // JSON forming/partial — show nothing in the body until should_say
            // lands (the thought box carries the activity meanwhile). This is
            // what stops fragments like `,"is_final":false,...}` from leaking.
            looksJson -> ""
            // Genuine plain-language reply — stream it straight through.
            else -> buffer
        }
        return StreamState(thinking = thinking.trim(), visible = visible)
    }

    /** Decode JSON string escapes; tolerant of a trailing partial escape. */
    private fun unescape(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'r' -> append('\r')
                    'b' -> append('\b')
                    'f' -> append(FORM_FEED)
                    '"' -> append('"')
                    '\\' -> append('\\')
                    '/' -> append('/')
                    'u' -> {
                        val hex = if (i + 6 <= s.length) s.substring(i + 2, i + 6) else null
                        val code = hex?.toIntOrNull(16)
                        if (code != null) {
                            append(code.toChar())
                            i += 6
                            continue
                        }
                        append(n)
                    }
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
