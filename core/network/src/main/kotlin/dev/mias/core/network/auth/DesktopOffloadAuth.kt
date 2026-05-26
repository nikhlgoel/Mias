package dev.mias.core.network.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared-secret token sent on every desktop-offload request.
 *
 * Both [dev.mias.core.network.mcp.McpClient] (direct LAN/loopback) and
 * [dev.mias.core.network.mesh.TailscaleMeshClient] (mesh peer) read from
 * this holder so the token is configured in one place.
 *
 * Empty token means no header is sent — useful for local trusted networks.
 * In-memory only; the Settings screen is responsible for persisting and
 * calling [set] at startup.
 */
@Singleton
class DesktopOffloadAuth @Inject constructor() {

    @Volatile
    var token: String = ""
        private set

    fun set(newToken: String) {
        token = newToken.trim()
    }

    fun clear() {
        token = ""
    }
}
