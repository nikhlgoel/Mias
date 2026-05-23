package dev.mias.core.modelhub.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the user's optional HuggingFace personal access token.
 *
 * Required for any gated repo (most official Google / Meta releases). Empty
 * token means no Authorization header is sent — public repos still work.
 *
 * This is intentionally in-memory only at this layer. The Settings screen
 * is responsible for persisting the token via EncryptedSharedPreferences and
 * calling [set] at startup; we do not store it here to keep this module free
 * of Android storage dependencies.
 */
@Singleton
class HuggingFaceAuth @Inject constructor() {

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
