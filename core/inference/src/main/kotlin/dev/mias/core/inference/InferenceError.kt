package dev.mias.core.inference

/**
 * Typed inference failures.
 *
 * [MiasResult.Error] only carries a string, which is fine for display but loses
 * the *kind* of failure — so callers can't tell "the model file is corrupt"
 * (re-download) from "out of memory" (retry/close apps) from "model not loaded"
 * (a programming error). We keep [MiasResult] string-based for compatibility but
 * attach one of these as its `cause`, so a caller that cares can branch on it:
 *
 * ```
 * (result.cause as? InferenceError)?.let { when (it) { … } }
 * ```
 *
 * Each carries [recoverable]: whether retrying the *same* operation could
 * plausibly succeed (transient), versus a permanent condition that needs user
 * action (re-download, free memory, install a model).
 */
sealed class InferenceError(
    override val message: String,
    val recoverable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Generation/inference was requested before a model finished loading. */
    data object ModelNotLoaded :
        InferenceError("No model is loaded yet.", recoverable = false)

    /** The model file is absent or smaller than any valid GGUF — failed download. */
    data class ModelFileInvalid(val path: String, val bytes: Long) : InferenceError(
        "The model file is missing or incomplete ($bytes bytes). " +
            "The download likely failed — re-download it from Models.",
        recoverable = false,
    )

    /**
     * The native loader rejected the file. Often a corrupt/partial download or
     * an unsupported quant. Treated as permanent (re-download), but the caller
     * may retry once in case it was a transient allocation hiccup.
     */
    data class ModelLoadFailed(val reason: String, val retryable: Boolean = true) : InferenceError(
        reason,
        recoverable = retryable,
    )

    /** Ran out of device memory loading or running the model. Transient-ish. */
    data object OutOfMemory : InferenceError(
        "Not enough memory to run this model. Close other apps or pick a smaller model.",
        recoverable = true,
    )

    /** A failure during the decode/sampling loop. */
    data class GenerationFailed(val reason: String) :
        InferenceError(reason, recoverable = true)

    companion object {
        /**
         * Best-effort classification of an arbitrary throwable/native message
         * into a typed error, so even failures thrown deep in JNI get a useful
         * category.
         */
        fun classify(t: Throwable): InferenceError {
            if (t is InferenceError) return t
            val raw = (t.message ?: "").lowercase()
            return when {
                "out of memory" in raw || "oom" in raw || "alloc" in raw || "bad_alloc" in raw ->
                    OutOfMemory
                "not loaded" in raw -> ModelNotLoaded
                "missing" in raw || "incomplete" in raw || "corrupt" in raw ||
                    "unsupported" in raw || "failed to load" in raw ->
                    ModelLoadFailed(t.message ?: "Model load failed.", retryable = false)
                else -> GenerationFailed(t.message ?: "Inference failed.")
            }
        }
    }
}
