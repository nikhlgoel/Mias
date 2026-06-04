package dev.mias.core.inference.react

/**
 * Per-model prompt formatting.
 *
 * Instruct models are trained with a specific turn-delimiter format. Feeding a
 * Qwen model a raw `"System… User… Assistant:"` prompt (the [PLAIN] fallback)
 * produces noticeably worse, ramble-prone replies than wrapping the turns in
 * the ChatML control tokens it actually expects. The native stop-marker list
 * already halts on `<|im_end|>`, `<|end|>`, `<|user|>`, etc., so these formats
 * also stop cleanly instead of running into the next turn.
 */
enum class ChatTemplateKind { CHATML, PHI, PLAIN }

object ChatTemplate {

    /** Pick a template from the model's name. Unknown families get [PLAIN]. */
    fun forModel(modelName: String): ChatTemplateKind {
        val n = modelName.lowercase()
        return when {
            "qwen" in n -> ChatTemplateKind.CHATML
            "phi" in n -> ChatTemplateKind.PHI
            else -> ChatTemplateKind.PLAIN
        }
    }

    /**
     * Wrap a system block and a user message into the model's turn format,
     * ending with the open assistant turn so generation continues as the reply.
     */
    fun build(kind: ChatTemplateKind, system: String, user: String): String = when (kind) {
        ChatTemplateKind.CHATML ->
            "<|im_start|>system\n$system<|im_end|>\n" +
                "<|im_start|>user\n$user<|im_end|>\n" +
                "<|im_start|>assistant\n"
        ChatTemplateKind.PHI ->
            "<|system|>\n$system<|end|>\n" +
                "<|user|>\n$user<|end|>\n" +
                "<|assistant|>\n"
        ChatTemplateKind.PLAIN ->
            "$system\n\nUser: $user\n\nAssistant:"
    }
}
