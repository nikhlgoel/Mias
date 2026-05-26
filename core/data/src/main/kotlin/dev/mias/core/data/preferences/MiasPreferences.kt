package dev.mias.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "mias_prefs")

/**
 * Snapshot of all user-configurable settings persisted via DataStore.
 *
 * Empty strings indicate "not configured" and consumers should treat them
 * as such (e.g. send no Authorization header, do not configure McpClient).
 */
data class MiasPrefs(
    val huggingFaceToken: String = "",
    val desktopHost: String = "",
    val desktopPort: Int = DEFAULT_DESKTOP_PORT,
    val desktopToken: String = "",
) {
    companion object {
        const val DEFAULT_DESKTOP_PORT: Int = 8401
    }
}

@Singleton
class MiasPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val HF_TOKEN = stringPreferencesKey("hf_token")
        val DESKTOP_HOST = stringPreferencesKey("desktop_host")
        val DESKTOP_PORT = intPreferencesKey("desktop_port")
        val DESKTOP_TOKEN = stringPreferencesKey("desktop_token")
    }

    val prefsFlow: Flow<MiasPrefs> = context.dataStore.data.map { it.toMiasPrefs() }

    suspend fun setHuggingFaceToken(token: String) {
        context.dataStore.edit { it[Keys.HF_TOKEN] = token.trim() }
    }

    suspend fun setDesktopEndpoint(host: String, port: Int, token: String) {
        context.dataStore.edit {
            it[Keys.DESKTOP_HOST] = host.trim()
            it[Keys.DESKTOP_PORT] = port
            it[Keys.DESKTOP_TOKEN] = token.trim()
        }
    }

    private fun Preferences.toMiasPrefs(): MiasPrefs = MiasPrefs(
        huggingFaceToken = this[Keys.HF_TOKEN].orEmpty(),
        desktopHost = this[Keys.DESKTOP_HOST].orEmpty(),
        desktopPort = this[Keys.DESKTOP_PORT] ?: MiasPrefs.DEFAULT_DESKTOP_PORT,
        desktopToken = this[Keys.DESKTOP_TOKEN].orEmpty(),
    )
}
