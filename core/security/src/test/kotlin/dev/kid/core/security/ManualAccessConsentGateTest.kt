package dev.kid.core.security

import com.google.common.truth.Truth.assertThat
import dev.kid.core.common.KidResult
import org.junit.jupiter.api.Test

class ManualAccessConsentGateTest {

    @Test
    fun consumeConsent_success_onceOnly() {
        val gate = ManualAccessConsentGate()
        val token = gate.issueConsent(
            operation = DataAccessOperation.EXPORT_FEED_DATA,
            reason = "Owner confirmed export",
            ttlMs = 5_000L,
        )

        val first = gate.consumeConsent(token, DataAccessOperation.EXPORT_FEED_DATA)
        val second = gate.consumeConsent(token, DataAccessOperation.EXPORT_FEED_DATA)

        assertThat(first is KidResult.Success).isTrue()
        assertThat(second is KidResult.Error).isTrue()
    }

    @Test
    fun consumeConsent_fails_on_wrong_operation() {
        val gate = ManualAccessConsentGate()
        val token = gate.issueConsent(
            operation = DataAccessOperation.SHARE_FILE_TO_OTHER_APP,
            reason = "Owner confirmed share",
            ttlMs = 5_000L,
        )

        val result = gate.consumeConsent(token, DataAccessOperation.EXTERNAL_BACKUP)

        assertThat(result is KidResult.Error).isTrue()
    }

    @Test
    fun consumeConsent_fails_on_expiry() {
        val gate = ManualAccessConsentGate()
        val token = gate.issueConsent(
            operation = DataAccessOperation.DESKTOP_TRANSFER,
            reason = "Owner confirmed transfer",
            ttlMs = 1L,
        )

        Thread.sleep(5)
        val result = gate.consumeConsent(token, DataAccessOperation.DESKTOP_TRANSFER)

        assertThat(result is KidResult.Error).isTrue()
    }
}
