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

    fun parse(buffer: String): StreamState {
        val thinking = THOUGHT.find(buffer)?.groupValues?.getOrNull(1)?.let { unescape(it) }.orEmpty()
        val visible = SHOULD_SAY.find(buffer)?.groupValues?.getOrNull(1)?.let { unescape(it) }.orEmpty()
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
