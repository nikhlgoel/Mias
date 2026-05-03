package dev.kid.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PeerNode model")
class PeerNodeTest {

    @Test
    fun `data class equality`() {
        val a = PeerNode("1", "desktop", "100.64.0.1", true)
        val b = PeerNode("1", "desktop", "100.64.0.1", true)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `copy with modified field`() {
        val peer = PeerNode("1", "desktop", "100.64.0.1", true)
        val offline = peer.copy(isOnline = false)
        assertThat(offline.isOnline).isFalse()
        assertThat(offline.id).isEqualTo("1")
    }

    @Test
    fun `tailscaleIp format`() {
        val peer = PeerNode("1", "host", "100.64.0.1", true)
        assertThat(peer.tailscaleIp).matches("^100\\..*")
    }
}
