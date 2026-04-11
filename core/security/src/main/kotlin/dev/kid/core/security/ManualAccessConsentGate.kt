package dev.kid.core.security

import dev.kid.core.common.KidResult
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ManualAccessConsentGate enforces explicit owner approval before any operation
 * that could expose private data outside app-private storage.
 *
 * Flow:
 * 1) UI asks owner for manual consent (biometric + explicit confirmation).
 * 2) UI calls issueConsent(...) and receives a short-lived token.
 * 3) Export/share path must call consumeConsent(...) with that token.
 * 4) Token is one-time-use and expires quickly.
 */
@Singleton
class ManualAccessConsentGate @Inject constructor() {

    fun issueConsent(
        operation: DataAccessOperation,
        reason: String,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): String {
        require(ttlMs > 0) { "ttlMs must be > 0" }
        val token = randomToken()
        val now = System.currentTimeMillis()
        approvals[token] = Approval(
            operation = operation,
            reason = reason,
            issuedAtMs = now,
            expiresAtMs = now + ttlMs,
        )
        return token
    }

    fun consumeConsent(
        token: String,
        operation: DataAccessOperation,
    ): KidResult<Unit> {
        val approval = approvals.remove(token)
            ?: return KidResult.Error("Manual approval missing or already used")

        if (approval.operation != operation) {
            return KidResult.Error("Approval operation mismatch: expected ${approval.operation}, got $operation")
        }

        if (System.currentTimeMillis() > approval.expiresAtMs) {
            return KidResult.Error("Manual approval expired")
        }

        return KidResult.Success(Unit)
    }

    fun revokeAll() {
        approvals.clear()
    }

    private fun randomToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private data class Approval(
        val operation: DataAccessOperation,
        val reason: String,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
    )

    companion object {
        private val secureRandom = SecureRandom()
        private val approvals = ConcurrentHashMap<String, Approval>()
        private const val TOKEN_BYTES = 16
        private const val DEFAULT_TTL_MS = 120_000L
    }
}

enum class DataAccessOperation {
    EXPORT_FEED_DATA,
    SHARE_FILE_TO_OTHER_APP,
    DESKTOP_TRANSFER,
    EXTERNAL_BACKUP,
}
