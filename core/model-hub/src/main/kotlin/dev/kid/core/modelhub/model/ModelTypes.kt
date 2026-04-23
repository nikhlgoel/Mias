package dev.kid.core.modelhub.model

import kotlinx.serialization.Serializable

/** A downloadable AI model from a registry (HuggingFace, etc.). */
@Serializable
data class ModelCard(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val sizeBytes: Long,
    val quantization: String,
    val format: ModelFormat,
    val roles: List<ModelRole>,
    val contextLength: Int,
    val parameterCount: String,
    val downloadUrl: String,
    val sha256: String,
    val license: String,
    val tags: List<String> = emptyList(),
    val minRamMb: Int = 2048,
    val npuCompatible: Boolean = false,
    val isRecommendedDefault: Boolean = false,
)

/** Supported model file formats. */
@Serializable
enum class ModelFormat {
    GGUF,
    ONNX,
    LITERT,
    SAFETENSORS,
}

/** What role a model can serve — one model may fill multiple roles. */
@Serializable
enum class ModelRole {
    CHAT,
    CODE,
    RESEARCH,
    CREATIVE,
    SURVIVAL,
    REASONING,
    VISION,
    EMBEDDING,
}

/** A model that has been downloaded and is available locally. */
data class InstalledModel(
    val id: String,
    val card: ModelCard,
    val localPath: String,
    val installedAt: Long,
    val lastUsedAt: Long,
    val sizeOnDisk: Long,
    val isActive: Boolean = false,
    val assignedRole: ModelRole? = null,
)

/** Current download state for a model. */
data class DownloadState(
    val modelId: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val error: String? = null,
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    EXTRACTING,
    COMPLETE,
    FAILED,
    CANCELLED,
}

/** Role assignment — which model is assigned to which brain role. */
data class RoleAssignment(
    val role: ModelRole,
    val modelId: String?,
    val isAutoSelected: Boolean = true,
)
