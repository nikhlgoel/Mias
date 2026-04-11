package dev.kid.core.modelhub.registry

import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import dev.kid.core.common.runCatchingKid
import dev.kid.core.modelhub.model.ModelCard
import dev.kid.core.modelhub.model.ModelFormat
import dev.kid.core.modelhub.model.ModelRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineDispatcher
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
    private val httpClient: HttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Search HuggingFace for GGUF models matching a query.
     * Results are transformed into [ModelCard] objects.
     */
    suspend fun search(
        query: String,
        limit: Int = 20,
    ): KidResult<List<ModelCard>> = withContext(ioDispatcher) {
        runCatchingKid {
            val url = "$HF_API_BASE/models?search=$query&filter=gguf&sort=downloads&direction=-1&limit=$limit"
            val responseStr: String = httpClient.get(url).body()
            val hfModels = json.decodeFromString<List<HfModelInfo>>(responseStr)

            hfModels.mapNotNull { hf ->
                // Only include models with GGUF files
                val ggufSibling = hf.siblings?.find { it.rfilename?.endsWith(".gguf") == true }
                if (ggufSibling == null) return@mapNotNull null

                val filename = ggufSibling.rfilename ?: return@mapNotNull null
                val quant = inferQuantization(filename)

                ModelCard(
                    id = hf.id.replace("/", "--"),
                    name = hf.id.substringAfter("/"),
                    author = hf.author ?: hf.id.substringBefore("/"),
                    description = hf.description ?: "Model from HuggingFace",
                    sizeBytes = ggufSibling.size ?: 0L,
                    quantization = quant,
                    format = ModelFormat.GGUF,
                    roles = inferRoles(hf.tags ?: emptyList(), hf.id),
                    contextLength = 4096,
                    parameterCount = inferParamCount(hf.id),
                    downloadUrl = "$HF_BASE/${hf.id}/resolve/main/$filename",
                    sha256 = "",
                    license = hf.license ?: "unknown",
                    tags = hf.tags ?: emptyList(),
                    minRamMb = estimateRamNeeded(ggufSibling.size ?: 0L),
                )
            }
        }
    }

    /** Get details for a specific model repo. */
    suspend fun getModelInfo(repoId: String): KidResult<ModelCard?> = withContext(ioDispatcher) {
        runCatchingKid {
            val url = "$HF_API_BASE/models/$repoId"
            val responseStr: String = httpClient.get(url).body()
            val hf = json.decodeFromString<HfModelInfo>(responseStr)

            val ggufFiles = hf.siblings?.filter { it.rfilename?.endsWith(".gguf") == true }
            val bestFile = ggufFiles
                ?.sortedByDescending { it.size ?: 0L }
                ?.find { inferQuantization(it.rfilename ?: "") in PREFERRED_QUANTS }
                ?: ggufFiles?.firstOrNull()
                ?: return@runCatchingKid null

            val filename = bestFile.rfilename ?: return@runCatchingKid null

            ModelCard(
                id = hf.id.replace("/", "--"),
                name = hf.id.substringAfter("/"),
                author = hf.author ?: hf.id.substringBefore("/"),
                description = hf.description ?: "",
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
            )
        }
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
