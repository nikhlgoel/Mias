package dev.mias.core.modelhub.security

import android.net.Uri
import dev.mias.core.modelhub.model.ModelCard
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelSourcePolicy @Inject constructor() {
    private val allowedHosts = setOf(
        "huggingface.co",
        "cdn-lfs.huggingface.co",
        "github.com",
        "objects.githubusercontent.com",
    )

    fun validate(card: ModelCard): ModelValidationResult {
        val uri = runCatching { Uri.parse(card.downloadUrl) }.getOrNull()
            ?: return ModelValidationResult.Rejected("Invalid model URL")

        if (uri.scheme != "https") {
            return ModelValidationResult.Rejected("Only HTTPS model downloads are allowed")
        }

        if (uri.host !in allowedHosts) {
            return ModelValidationResult.Rejected("Untrusted model host: ${uri.host}")
        }

        if (card.sha256.isNotBlank() && !SHA_256_REGEX.matches(card.sha256)) {
            return ModelValidationResult.Rejected("Invalid SHA-256 checksum metadata")
        }

        if (card.sizeBytes <= 0) {
            return ModelValidationResult.Rejected("Model size is missing")
        }

        return ModelValidationResult.Accepted
    }

    fun verifySha256(bytes: ByteArray, expected: String): Boolean {
        if (expected.isBlank()) return true
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    companion object {
        private val SHA_256_REGEX = Regex("^[a-fA-F0-9]{64}$")
    }
}

sealed class ModelValidationResult {
    data object Accepted : ModelValidationResult()
    data class Rejected(val reason: String) : ModelValidationResult()
}
