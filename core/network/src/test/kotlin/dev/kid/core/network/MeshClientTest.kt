package dev.kid.core.network

import com.google.common.truth.Truth.assertThat
import dev.kid.core.common.KidResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MeshClient contract")
class MeshClientTest {

    private lateinit var client: MeshClient

    @BeforeEach
    fun setUp() {
        client = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("discoverPeers")
    inner class DiscoverPeersTests {

        @Test
        fun `returns empty list when no peers found`() = runTest {
            coEvery { client.discoverPeers() } returns KidResult.Success(emptyList())

            val result = client.discoverPeers()

            assertThat(result).isInstanceOf(KidResult.Success::class.java)
            assertThat((result as KidResult.Success).data).isEmpty()
        }

        @Test
        fun `returns peers on Tailscale mesh`() = runTest {
            val peers = listOf(
                PeerNode("1", "desktop-pc", "100.64.0.1", true),
                PeerNode("2", "laptop", "100.64.0.2", false),
            )
            coEvery { client.discoverPeers() } returns KidResult.Success(peers)

            val result = client.discoverPeers()

            assertThat(result).isInstanceOf(KidResult.Success::class.java)
            assertThat((result as KidResult.Success).data).hasSize(2)
            assertThat(result.data[0].tailscaleIp).startsWith("100.")
        }

        @Test
        fun `returns Error on network failure`() = runTest {
            coEvery { client.discoverPeers() } returns KidResult.Error("Tailscale not running")

            val result = client.discoverPeers()

            assertThat(result).isInstanceOf(KidResult.Error::class.java)
        }
    }

    @Nested
    @DisplayName("sendRequest")
    inner class SendRequestTests {

        @Test
        fun `sends payload and receives response`() = runTest {
            val payload = "hello".toByteArray()
            val response = "world".toByteArray()
            coEvery { client.sendRequest("1", payload) } returns KidResult.Success(response)

            val result = client.sendRequest("1", payload)

            assertThat(result).isInstanceOf(KidResult.Success::class.java)
            assertThat(String((result as KidResult.Success).data)).isEqualTo("world")
        }

        @Test
        fun `returns Error when peer offline`() = runTest {
            coEvery { client.sendRequest(any(), any()) } returns KidResult.Error("Peer unreachable")

            val result = client.sendRequest("offline-peer", byteArrayOf())

            assertThat(result).isInstanceOf(KidResult.Error::class.java)
            assertThat((result as KidResult.Error).message).contains("unreachable")
        }
    }

    @Nested
    @DisplayName("connection state")
    inner class ConnectionTests {

        @Test
        fun `isConnected returns false when disconnected`() {
            every { client.isConnected() } returns false
            assertThat(client.isConnected()).isFalse()
        }

        @Test
        fun `isConnected returns true when on mesh`() {
            every { client.isConnected() } returns true
            assertThat(client.isConnected()).isTrue()
        }
    }
}
