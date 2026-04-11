package dev.kid.core.network

import dev.kid.core.common.KidResult

/**
 * Contract for Tailscale P2P mesh communication.
 * All connections are local-network only (WireGuard tunnel).
 * No external/cloud endpoints may be contacted.
 */
interface MeshClient {
    suspend fun discoverPeers(): KidResult<List<PeerNode>>
    suspend fun sendRequest(peerId: String, payload: ByteArray): KidResult<ByteArray>
    fun isConnected(): Boolean
}

data class PeerNode(
    val id: String,
    val hostname: String,
    val tailscaleIp: String,
    val isOnline: Boolean,
)
