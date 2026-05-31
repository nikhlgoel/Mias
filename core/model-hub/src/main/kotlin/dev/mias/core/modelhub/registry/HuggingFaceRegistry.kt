package dev.mias.core.modelhub.registry

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.runCatchingMias
import dev.mias.core.modelhub.auth.HuggingFaceAuth
import dev.mias.core.modelhub.di.ModelHubHttpClient
import dev.mias.core.modelhub.model.ModelCard
import dev.mias.core.modelhub.model.ModelFormat
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.modelhub.model.ModelRuntime
import dev.mias.core.modelhub.model.ModelSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches model listings from HuggingFace API for browsing.
 *
 * This is the ONLY component that makes internet calls for model discovery.
 * All actual inference remains 100% local.
 */
@Singleton
class HuggingFaceRegistry @Inject constructor(
    @ModelHubHttpClient private val httpClient: HttpClient,
    private val auth: HuggingFaceAuth,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * What artifact family the user is searching for. Text/GGUF flows through
     * llama.cpp; Vision/Task flows through MediaPipe GenAI.
     */
    enum class Kind { GGUF, TASK }

    /**
     * Search HuggingFace for models of the given [kind].
     * Results are transformed into [ModelCard] objects.
     */
    suspend fun search(
        query: String,
        limit: Int = 12,
        kind: Kind = Kind.GGUF,
    ): MiasResult<List<ModelCard>> = withContext(ioDispatcher) {
        runCatchingMias {
            // /api/models?search=… doesn't include `siblings`, so the search
            // pass only gives us repo ids. Then resolve each in parallel to
            // pull the actual filename + size.
            val filter = when (kind) {
                Kind.GGUF -> "&filter=gguf"
                Kind.TASK -> "" // No HF-side filter; we'll match on siblings client-side.
            }
            val searchUrl = "$HF_API_BASE/models?search=$query$filter&sort=downloads&direction=-1&limit=$limit"
            val searchBody: String = httpClient.get(searchUrl) { applyAuth() }.body()
            val searchResults = json.decodeFromString<List<HfModelInfo>>(searchBody)

            coroutineScope {
                searchResults.map { hit ->
                    async { runCatching { resolveCard(hit.id, kind) }.getOrNull() }
                }.awaitAll().filterNotNull()
            }
        }
    }

    private suspend fun resolveCard(repoId: String, kind: Kind = Kind.GGUF): ModelCard? {
        val infoUrl = "$HF_API_BASE/models/$repoId"
        val body: String = httpClient.get(infoUrl) { applyAuth() }.body()
        val hf = json.decodeFromString<HfModelInfo>(body)

        return when (kind) {
            Kind.GGUF -> resolveGguf(hf)
            Kind.TASK -> resolveTask(hf)
        }
    }

    private fun resolveGguf(hf: HfModelInfo): ModelCard? {
        val ggufFiles = hf.siblings?.filter { it.rfilename?.endsWith(".gguf") == true }
            ?: return null
        val bestFile = ggufFiles
            .sortedByDescending { it.size ?: 0L }
            .find { inferQuantization(it.rfilename ?: "") in PREFERRED_QUANTS }
            ?: ggufFiles.firstOrNull()
            ?: return null

        val filename = bestFile.rfilename ?: return null
        return ModelCard(
            id = hf.id.replace("/", "--"),
            name = hf.id.substringAfter("/"),
            author = hf.author ?: hf.id.substringBefore("/"),
            description = hf.description ?: "Model from HuggingFace",
            sizeBytes = bestFile.size ?: 0L,
            quantization = inferQuantization(filename),
            format = ModelFormat.GGUF,
            roles = inferRoles(hf.tags ?: emptyList(), hf.id),
            contextLength = 4096,
            parameterCount = inferParamCount(hf.id),
            downloadUrl = "$HF_BASE/${hf.id}/resolve/main/$filename",
            sha256 = "",
            license = hf.license ?: "unknown",
            tags = hf.tags ?: emptyList(),
            minRamMb = estimateRamNeeded(bestFile.size ?: 0L),
            source = ModelSource.HUGGING_FACE,
            runtime = ModelRuntime.LLAMA_CPP,
        )
    }

    private fun resolveTask(hf: HfModelInfo): ModelCard? {
        val taskFiles = hf.siblings?.filter { it.rfilename?.endsWith(".task") == true }
            ?: return null
        val bestFile = taskFiles.sortedByDescending { it.size ?: 0L }.firstOrNull() ?: return null
        val filename = bestFile.rfilename ?: return null
        // Task bundles for Gemma 3n are vision-capable.
        val roles = mutableListOf(ModelRole.VISION, ModelRole.CHAT)
        return ModelCard(
            id = hf.id.replace("/", "--"),
            name = hf.id.substringAfter("/"),
            author = hf.author ?: hf.id.substringBefore("/"),
            description = hf.description ?: "Vision model from HuggingFace",
            sizeBytes = bestFile.size ?: 0L,
            quantization = "TASK",
            format = ModelFormat.LITERT,
            roles = roles,
            contextLength = 4096,
            parameterCount = inferParamCount(hf.id),
            downloadUrl = "$HF_BASE/${hf.id}/resolve/main/$filename",
            sha256 = "",
            license = hf.license ?: "unknown",
            tags = hf.tags ?: emptyList(),
            minRamMb = estimateRamNeeded(bestFile.size ?: 0L),
            source = ModelSource.HUGGING_FACE,
            runtime = ModelRuntime.GOOGLE_AI_EDGE,
        )
    }

    private fun HttpRequestBuilder.applyAuth() {
        val token = auth.token
        if (token.isNotBlank()) header("Authorization", "Bearer $token")
    }

    /** Get details for a specific model repo. */
    suspend fun getModelInfo(repoId: String): MiasResult<ModelCard?> = withContext(ioDispatcher) {
        runCatchingMias { resolveCard(repoId) }
    }

    private fun inferQuantization(filename: String): String {
        val lower = filename.lowercase()
        return when {
            "q4_k_m" in lower -> "Q4_K_M"
            "q4_k_s" in lower -> "Q4_K_S"
            "q5_k_m" in lower -> "Q5_K_M"
            "q8_0" in lower -> "Q8_0"
            "q6_k" in lower -> "Q6_K"
            "q3_k_m" in lower -> "Q3_K_M"
            "q2_k" in lower -> "Q2_K"
            "f16" in lower -> "F16"
            else -> "unknown"
        }
    }

    private fun inferRoles(tags: List<String>, modelId: String): List<ModelRole> {
        val roles = mutableListOf<ModelRole>()
        val lower = (tags.joinToString(" ") + " " + modelId).lowercase()

        if ("code" in lower || "coder" in lower) roles.add(ModelRole.CODE)
        if ("chat" in lower || "instruct" in lower || "it" in lower) roles.add(ModelRole.CHAT)
        if ("embed" in lower) roles.add(ModelRole.EMBEDDING)
        if ("vision" in lower || "vl" in lower) roles.add(ModelRole.VISION)

        if (roles.isEmpty()) roles.add(ModelRole.CHAT)
        return roles
    }

    private fun inferParamCount(modelId: String): String {
        val lower = modelId.lowercase()
        val regex = """(\d+\.?\d*)\s*[bm]""".toRegex()
        val match = regex.find(lower) ?: return "unknown"
        return match.value.uppercase()
    }

    private fun estimateRamNeeded(sizeBytes: Long): Int {
        // Model in memory is roughly 1.2x file size for GGUF
        return ((sizeBytes * 1.2) / (1024 * 1024)).toInt().coerceAtLeast(512)
    }

    @Serializable
    private data class HfModelInfo(
        val id: String,
        val author: String? = null,
        val description: String? = null,
        val license: String? = null,
        val tags: List<String>? = null,
        val siblings: List<HfSibling>? = null,
    )

    @Serializable
    private data class HfSibling(
        val rfilename: String? = null,
        val size: Long? = null,
    )

    companion object {
        private const val HF_API_BASE = "https://huggingface.co/api"
        private const val HF_BASE = "https://huggingface.co"
        private val PREFERRED_QUANTS = setOf("Q4_K_M", "Q4_K_S", "Q5_K_M", "Q8_0")
    }
}
