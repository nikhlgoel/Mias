package dev.mias.core.common.memory

/**
 * Pure helpers for the persistent-memory pipeline ("remember the user across
 * conversations", persistent assistant-memory style).
 *
 * Every chat turn is stateless — the model never replays history. What persists
 * is a small set of *distilled* facts about the user (name, preferences,
 * projects, goals) extracted after an exchange, deduplicated, and stored with
 * embeddings. On later turns the existing Hindsight recall injects whichever of
 * them are relevant ("What I Know About You"), so a fresh chat can still know
 * the person.
 *
 * This object owns the model-facing text work: the gate that decides whether
 * an exchange is even worth a distillation pass, the extraction prompt, and
 * the defensive parser for the model's reply. All pure — fully unit-testable.
 */
object MemoryDistiller {

    /** Max memories accepted from one exchange. */
    const val MAX_MEMORIES = 3

    /** Length cap for an explicitly requested memory. */
    const val EXPLICIT_MAX_CHARS = 240

    /**
     * Detect an explicit "remember this" command and pull out what to remember.
     *
     * Returns:
     *  - `null` — no memory command in the message;
     *  - non-blank string — the inline content ("remember that my birthday is
     *    June 5" → "my birthday is June 5");
     *  - blank string — a bare command ("save this to memory") that refers to
     *    earlier content; the caller supplies it (e.g. the previous reply).
     *
     * Deterministic on purpose: an explicit user request to remember must
     * never depend on a small model choosing to cooperate.
     */
    fun extractExplicitMemory(userText: String): String? {
        val t = userText.trim()
        if (t.isBlank()) return null
        for (pattern in EXPLICIT_PATTERNS) {
            val match = pattern.find(t) ?: continue
            return match.groups["content"]?.value
                ?.trim()
                ?.trim('.', '!', ',', ':', ';')
                ?.take(EXPLICIT_MAX_CHARS)
                .orEmpty()
        }
        return null
    }

    /**
     * Cheap gate: does the user's message plausibly contain something durable
     * about *them*? A distillation pass costs a full prompt-process + short
     * generation on-device, so it must not run on "what's 2+2".
     * Deliberately recall-leaning — the extraction model filters further, and
     * a missed gate just means that fact gets captured a later time it comes up.
     */
    fun containsPersonalSignal(userText: String): Boolean {
        val t = " " + userText.lowercase().replace(Regex("\\s+"), " ").trim() + " "
        return PERSONAL_SIGNALS.any { it in t }
    }

    /**
     * Extraction prompt for the already-loaded chat model. Constrained shape
     * (one fact per line / literal NONE) so small models stay parseable, and
     * "stated by the user" so the model doesn't invent biography.
     */
    fun buildPrompt(userText: String, assistantText: String): String = buildString {
        append("From the exchange below, extract up to ")
        append(MAX_MEMORIES)
        append(" lasting facts about the user worth remembering for future ")
        append("conversations — their name, preferences, projects, goals, or ")
        append("important personal context. Only facts the user stated about ")
        append("themselves; ignore one-off questions and small talk. ")
        append("Write each fact as a short third-person sentence, one per line. ")
        append("If there is nothing worth remembering, reply with exactly: NONE")
        append("\n\nUser: ").append(userText.take(INPUT_CHAR_CAP))
        append("\nAssistant: ").append(assistantText.take(INPUT_CHAR_CAP))
        append("\n\nFacts:")
    }

    /**
     * Parse the model's reply into clean memory sentences. Defensive against
     * everything a small model does: bullets, numbering, a "Facts:" echo,
     * NONE in creative casings, duplicate lines, rambling overlong output.
     */
    fun parse(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim().removePrefix("Facts:").trim() }
            .map { it.trimStart('-', '•', '*', '–', ' ') }
            .map { it.replace(LEADING_NUMBERING, "") }
            .map { it.trim() }
            .filter { it.length in MIN_FACT_CHARS..MAX_FACT_CHARS }
            .filterNot { NONE_MARKERS.contains(it.lowercase().trimEnd('.', '!')) }
            .filterNot { it.startsWith("{") || it.startsWith("\"") } // JSON leak guard
            .distinctBy { it.lowercase() }
            .take(MAX_MEMORIES)
            .toList()
    }

    // What a "remember me" signal looks like in casual chat. Word-ish bounded
    // by the padded-space matching in [containsPersonalSignal].
    private val PERSONAL_SIGNALS = listOf(
        " i am ", " i'm ", " im ", " my name", " call me ", " i like ", " i love ",
        " i hate ", " i prefer ", " i work ", " i live ", " i study ", " i use ",
        " my project", " my app ", " my company", " my team ", " my goal",
        " my favorite", " my favourite", " my birthday", " my wife", " my husband",
        " my kid", " my son ", " my daughter", " my dog ", " my cat ",
        " remember ", " don't forget ", " dont forget ", " from now on ",
        " i want to build", " i'm building", " i am building", " i plan ",
    )

    /**
     * Explicit memory commands, most-specific first (the "that …" forms carry
     * inline content; the bare "this/it" forms refer to earlier context).
     * Matched anywhere in the message so "can you remember that…" works.
     */
    private val EXPLICIT_PATTERNS = listOf(
        Regex("(?i)\\b(?:remember|note)\\s+that\\s+(?<content>.+)"),
        Regex("(?i)\\bremember\\s*:\\s*(?<content>.+)"),
        Regex("(?i)\\bkeep\\s+in\\s+mind\\s+(?:that\\s+)?(?<content>.+)"),
        Regex("(?i)\\bdon'?t\\s+forget\\s+(?:that\\s+)?(?<content>.+)"),
        Regex("(?i)\\b(?:save|add|put|store)\\s+(?:this|that|it)?\\s*(?:in|into|to)\\s+(?:your\\s+)?memory\\b(?<content>.*)"),
        Regex("(?i)\\bremember\\s+(?:this|that|it)\\b\\s*$(?<content>)"),
    )

    private val LEADING_NUMBERING = Regex("^\\d+[.)]\\s*")
    private val NONE_MARKERS = setOf("none", "no facts", "nothing", "n/a", "nothing worth remembering")

    private const val MIN_FACT_CHARS = 8
    private const val MAX_FACT_CHARS = 200
    private const val INPUT_CHAR_CAP = 600
}
