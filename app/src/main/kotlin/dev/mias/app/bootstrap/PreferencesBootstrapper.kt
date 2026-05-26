package dev.mias.app.bootstrap

import dev.mias.core.data.preferences.MiasPreferences
import dev.mias.core.modelhub.auth.HuggingFaceAuth
import dev.mias.core.network.auth.DesktopOffloadAuth
import dev.mias.core.network.mcp.McpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies persisted user preferences to the in-memory runtime holders at
 * app startup, and keeps them in sync with subsequent changes.
 *
 * Without this, the HuggingFace token and desktop offload endpoint set in
 * Settings would only take effect on the next process launch *after*
 * Settings is visited.
 */
@Singleton
class PreferencesBootstrapper @Inject constructor(
    private val preferences: MiasPreferences,
    private val huggingFaceAuth: HuggingFaceAuth,
    private val desktopOffloadAuth: DesktopOffloadAuth,
    private val mcpClient: McpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            preferences.prefsFlow.distinctUntilChanged().collect { prefs ->
                huggingFaceAuth.set(prefs.huggingFaceToken)
                desktopOffloadAuth.set(prefs.desktopToken)
                if (prefs.desktopHost.isNotBlank()) {
                    mcpClient.configure(prefs.desktopHost, prefs.desktopPort)
                }
            }
        }
    }
}
