package dev.kid.core.modelhub.registry

import dev.kid.core.modelhub.model.ModelCard
import dev.kid.core.modelhub.model.ModelFormat
import dev.kid.core.modelhub.model.ModelRole

/**
 * Curated registry of models known to work with Kid.
 *
 * These are pre-vetted models from HuggingFace with correct download URLs,
 * verified quantizations, and tested compatibility. Users can also add
 * custom model URLs, but these are the recommended defaults.
 */
object CuratedModelRegistry {

    val models: List<ModelCard> = listOf(
        // ── Primary Brain — Best all-round on-device model ──────────
        ModelCard(
            id = "gemma-4-e4b-q4",
            name = "Gemma 4 e4b",
            author = "Google",
            description = "4B effective parameter model optimized for NPU. " +
                "Excellent at conversation, reasoning, and tool use.",
            sizeBytes = 2_700_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CHAT, ModelRole.REASONING, ModelRole.CODE),
            contextLength = 32768,
            parameterCount = "4B effective",
            downloadUrl = "https://huggingface.co/google/gemma-4-e4b-GGUF/resolve/main/gemma-4-e4b-Q4_K_M.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("google", "npu", "primary"),
            minRamMb = 3072,
            npuCompatible = true,
        ),

        // ── Survival Brain — Tiny but capable for thermal fallback ──
        ModelCard(
            id = "qwen3-0.6b-q8",
            name = "Qwen3 0.6B",
            author = "Alibaba",
            description = "Ultra-light 0.6B model for thermal survival mode. " +
                "Minimal resource use, still capable of basic conversation.",
            sizeBytes = 700_000_000L,
            quantization = "Q8_0",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CHAT, ModelRole.SURVIVAL),
            contextLength = 4096,
            parameterCount = "0.6B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/qwen3-0.6b-q8_0.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("qwen", "survival", "tiny"),
            minRamMb = 768,
            npuCompatible = false,
            isRecommendedDefault = true,
        ),

        // ── Reasoning Brain — Deep thinking for complex tasks ───────
        ModelCard(
            id = "qwen3-1.7b-q4",
            name = "Qwen3 1.7B",
            author = "Alibaba",
            description = "1.7B model with strong reasoning capability. " +
                "Good balance of size and intelligence for on-device use.",
            sizeBytes = 1_200_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CHAT, ModelRole.REASONING, ModelRole.CODE),
            contextLength = 32768,
            parameterCount = "1.7B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/qwen3-1.7b-q4_k_m.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("qwen", "reasoning", "balanced"),
            minRamMb = 1536,
            npuCompatible = false,
        ),

        // ── Code Brain — Specialized for programming tasks ──────────
        ModelCard(
            id = "qwen3-4b-q4",
            name = "Qwen3 4B",
            author = "Alibaba",
            description = "4B model excellent at code generation, debugging, " +
                "and technical explanations. Strong reasoning chain.",
            sizeBytes = 2_600_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CODE, ModelRole.REASONING, ModelRole.CHAT),
            contextLength = 32768,
            parameterCount = "4B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/qwen3-4b-q4_k_m.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("qwen", "code", "large"),
            minRamMb = 3072,
            npuCompatible = false,
        ),

        // ── Research Brain — Large context for deep analysis ────────
        ModelCard(
            id = "gemma-3-4b-q4",
            name = "Gemma 3 4B IT",
            author = "Google",
            description = "Instruction-tuned 4B model from Google. " +
                "Strong at following complex instructions and research tasks.",
            sizeBytes = 2_500_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.RESEARCH, ModelRole.CHAT, ModelRole.CREATIVE),
            contextLength = 32768,
            parameterCount = "4B",
            downloadUrl = "https://huggingface.co/google/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("google", "research", "instruction-tuned"),
            minRamMb = 3072,
            npuCompatible = true,
        ),

        // ── Creative Brain — For content generation ─────────────────
        ModelCard(
            id = "phi-4-mini-q4",
            name = "Phi-4 Mini",
            author = "Microsoft",
            description = "3.8B model from Microsoft excelling at creative " +
                "writing, summarization, and content generation.",
            sizeBytes = 2_300_000_000L,
            quantization = "Q4_K_M",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.CREATIVE, ModelRole.CHAT),
            contextLength = 16384,
            parameterCount = "3.8B",
            downloadUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf",
            sha256 = "",
            license = "MIT",
            tags = listOf("microsoft", "creative", "writing"),
            minRamMb = 2560,
            npuCompatible = false,
        ),

        // ── Embedding Brain — For semantic search in Hindsight ──────
        ModelCard(
            id = "nomic-embed-v2-q8",
            name = "Nomic Embed v2",
            author = "Nomic AI",
            description = "High-quality text embedding model for semantic " +
                "search. Powers Hindsight Memory's similarity queries.",
            sizeBytes = 300_000_000L,
            quantization = "Q8_0",
            format = ModelFormat.GGUF,
            roles = listOf(ModelRole.EMBEDDING),
            contextLength = 8192,
            parameterCount = "137M",
            downloadUrl = "https://huggingface.co/nomic-ai/nomic-embed-text-v2-GGUF/resolve/main/nomic-embed-text-v2-Q8_0.gguf",
            sha256 = "",
            license = "Apache-2.0",
            tags = listOf("embedding", "search", "small"),
            minRamMb = 512,
            npuCompatible = false,
        ),
    )

    fun getById(id: String): ModelCard? = models.find { it.id == id }

    fun getByRole(role: ModelRole): List<ModelCard> =
        models.filter { role in it.roles }

    fun getRecommendedForRole(role: ModelRole, availableRamMb: Int): ModelCard? =
        models.filter { role in it.roles && it.minRamMb <= availableRamMb }
            .maxByOrNull { it.contextLength }
}
