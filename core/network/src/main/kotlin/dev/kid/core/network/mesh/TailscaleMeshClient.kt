package dev.kid.core.network.mesh

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import dev.kid.core.common.runCatchingKid
import dev.kid.core.network.MeshClient
import dev.kid.core.network.PeerNode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tailscale mesh client implementation.
 *
 * Queries the Tailscale local API (http://127.0.0.1:41112) to discover
 * peers on the WireGuard mesh. All traffic is encrypted peer-to-peer.
 */
@Singleton
class TailscaleMeshClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MeshClient {

    @Volatile
    private var connected = false

    /** Check if the Tailscale VPN app is installed on the device. */
    private fun isTailscaleInstalled(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override suspend fun discoverPeers(): KidResult<List<PeerNode>> =
        withContext(ioDispatcher) {
            if (!isTailscaleInstalled()) {
                return@withContext KidResult.Error(
                    "Tailscale not installed — desktop offload unavailable"
                )
            }

            runCatchingKid {
                val response: String = httpClient.get("$TAILSCALE_API/localapi/v0/status")
                    .body()
                val json = Json { ignoreUnknownKeys = true }
                val status = json.parseToJsonElement(response).jsonObject
                val peerMap = status["Peer"]?.jsonObject ?: JsonObject(emptyMap())

                peerMap.values.mapNotNull { peerElement ->
                    val peer = peerElement.jsonObject
                    val hostname = peer["HostName"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val ips = peer["TailscaleIPs"]
                    val ip = ips?.toString()?.removeSurrounding("[\"", "\"]")?.split("\",\"")
                        ?.firstOrNull() ?: return@mapNotNull null
                    val online = peer["Online"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                        ?: false

                    PeerNode(
                        id = peer["ID"]?.jsonPrimitive?.content ?: hostname,
                        hostname = hostname,
                        tailscaleIp = ip,
                        isOnline = online,
                    )
                }.also {
                    connected = true
                }
            }
        }

    override suspend fun sendRequest(peerId: String, payload: ByteArray): KidResult<ByteArray> =
        withContext(ioDispatcher) {
            runCatchingKid {
                // Find peer by ID and send HTTP request to their Tailscale IP
                val peers = (discoverPeers() as? KidResult.Success)?.data ?: emptyList()
                val peer = peers.find { it.id == peerId || it.hostname == peerId }
                    ?: throw NoSuchElementException("Peer $peerId not found on mesh")

                val response: ByteArray = httpClient.post(
                    "http://${peer.tailscaleIp}:8401/rpc",
                ) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }.body()

                response
            }
        }

    override fun isConnected(): Boolean = connected

    /** Check if a specific peer (by hostname) is currently online. */
    suspend fun isPeerOnline(hostname: String): Boolean {
        val result = discoverPeers()
        return when (result) {
            is KidResult.Success -> result.data.any {
                it.hostname == hostname && it.isOnline
            }
            is KidResult.Error -> false
        }
    }

    companion object {
        private const val TAILSCALE_API = "http://127.0.0.1:41112"
        private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    }
}
