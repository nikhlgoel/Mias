package dev.mias.mobile.bootstrap

import dev.mias.core.data.preferences.MiasPreferences
import dev.mias.core.modelhub.auth.HuggingFaceAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies persisted preferences to in-memory runtime holders at startup and
 * keeps them in sync. Trimmed at the S7 cutover: only the HuggingFace token
 * needs a native holder (model downloads use it). The desktop-offload endpoint
 * is read directly from prefs by the RN app's TypeScript MCP client, so the
 * legacy `core/network` McpClient/DesktopOffloadAuth sync is gone.
 *
 * Without this, the HuggingFace token set in Settings would only take effect on
 * the next process launch after Settings is visited.
 */
@Singleton
class StartupPreferences @Inject constructor(
    private val preferences: MiasPreferences,
    private val huggingFaceAuth: HuggingFaceAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            preferences.prefsFlow.distinctUntilChanged().collect { prefs ->
                huggingFaceAuth.set(prefs.huggingFaceToken)
            }
        }
    }
}
