package dev.mias.core.modelhub.registry

import dev.mias.core.modelhub.model.ModelCard
import dev.mias.core.modelhub.model.ModelFormat
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.modelhub.model.ModelRuntime
import dev.mias.core.modelhub.model.ModelSource

object CuratedModelRegistry {

    /**
     * Curated, hand-verified model URLs.
     *
     * Rules for adding entries:
     *  - The HuggingFace repo and exact filename must exist (check in a browser).
     *  - Prefer official org repos (`Qwen/…`, `nomic-ai/…`) when they ship GGUF.
     *    Otherwise use a long-standing community quantizer (`bartowski/…`).
     *  - Leave `sha256 = ""` only if you've not yet recorded the digest.
     *    When set, ModelDownloadManager will refuse the file if it doesn't match.
     */
    val models: List<ModelCard> = listOf(
        // ── Survival Brain — Tiny, always-available fallback ────────
        ModelCard(
            id = "qwen2.5-0.5b-instruct-q8",
            name = "Qwen2.5 0.5B Instruct",
            author = "Alibaba",
            description = "Ultra-light 0.5B model for thermal-survival mode. " +
                "Minimal resource use, still capable of basic conversation and tool calls.",
            sizeBytes = 531_000_000L,
            quantization = "Q8_0",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CHAT, ModelRole.SURVIVAL),
            contextLength = 32768,
            parameterCount = "0.5B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("qwen", "survival", "tiny", "official"),
            minRamMb = 768,
            npuCompatible = false,
            isRecommendedDefault = true,
            defaultRolePriority = 100,
        ),

        // ── Primary Brain — Balanced default for chat / reasoning ───
        ModelCard(
            id = "qwen2.5-1.5b-instruct-q4",
            name = "Qwen2.5 1.5B Instruct",
            author = "Alibaba",
            description = "1.5B parameter model with strong instruction following " +
                "and reasoning. Good default for on-device chat on most phones.",
            sizeBytes = 986_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CHAT, ModelRole.REASONING, ModelRole.RESEARCH),
            contextLength = 32768,
            parameterCount = "1.5B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("qwen", "primary", "balanced", "official"),
            minRamMb = 1536,
            npuCompatible = false,
            defaultRolePriority = 90,
        ),

        // ── Code Brain — Programmer-tuned ───────────────────────────
        ModelCard(
            id = "qwen2.5-coder-3b-instruct-q4",
            name = "Qwen2.5 Coder 3B Instruct",
            author = "Alibaba",
            description = "Code-specialized 3B model. Strong at generation, " +
                "debugging, and explanation across mainstream languages.",
            sizeBytes = 1_930_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CODE, ModelRole.REASONING, ModelRole.CHAT),
            contextLength = 32768,
            parameterCount = "3B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("qwen", "code", "official"),
            minRamMb = 2560,
            npuCompatible = false,
            defaultRolePriority = 85,
        ),

        // ── Vision-capable / multimodal placeholder ─────────────────
        // (Curated vision entries pending verified GGUF builds. Users can
        //  add others via HF search.)

        // ── Creative Brain — Writing-tuned ──────────────────────────
        ModelCard(
            id = "phi-3.5-mini-instruct-q4",
            name = "Phi-3.5 Mini Instruct",
            author = "Microsoft (community quant)",
            description = "3.8B Microsoft model strong at creative writing, " +
                "summarization, and long-form generation. Quantized by bartowski.",
            sizeBytes = 2_390_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CREATIVE, ModelRole.CHAT, ModelRole.RESEARCH),
            contextLength = 128000,
            parameterCount = "3.8B",
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            sha256 = "",
            license = "MIT",
            tags = listOf("microsoft", "creative", "long-context"),
            minRamMb = 2560,
            npuCompatible = false,
            defaultRolePriority = 70,
        ),

        // ── Embedding Brain — Semantic search for Hindsight ─────────
        ModelCard(
            id = "nomic-embed-text-v1.5-q8",
            name = "Nomic Embed Text v1.5",
            author = "Nomic AI",
            description = "High-quality text embedding model (768-dim) for " +
                "semantic search. Powers Hindsight Memory's similarity queries.",
            sizeBytes = 146_000_000L,
            quantization = "Q8_0",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.EMBEDDING),
            contextLength = 8192,
            parameterCount = "137M",
            downloadUrl = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q8_0.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("embedding", "search", "small", "official"),
            minRamMb = 512,
            npuCompatible = false,
            runtime = ModelRuntime.EMBEDDING,
            defaultRolePriority = 100,
        ),
    )

    val trustedSources: Set<ModelSource> = setOf(ModelSource.CURATED, ModelSource.HUGGING_FACE)

    fun getById(id: String): ModelCard? = models.find { it.id == id }

    fun getByRole(role: ModelRole): List<ModelCard> =
        models.filter { role in it.roles }

    fun getRecommendedForRole(role: ModelRole, availableRamMb: Int): ModelCard? =
        models.filter { role in it.roles && it.minRamMb <= availableRamMb }
            .maxWithOrNull(compareBy<ModelCard> { it.defaultRolePriority }.thenBy { it.contextLength })
}
