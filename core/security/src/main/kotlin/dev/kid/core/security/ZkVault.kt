package dev.kid.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-Knowledge Vault — AES-256-GCM encrypted key-value store.
 *
 * All secrets (API tokens for local services, safe word hash, device keys)
 * live here. Backed by AndroidX Security Crypto with hardware-backed
 * MasterKey (StrongBox when available).
 *
 * Nothing leaves the device. Nothing is recoverable if hardware key is wiped.
 */
@Singleton
class ZkVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            VAULT_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Store a secret string. */
    fun putSecret(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /** Retrieve a secret string, or null if not present. */
    fun getSecret(key: String): String? = prefs.getString(key, null)

    /** Remove a secret. */
    fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** Check if a secret exists. */
    fun hasSecret(key: String): Boolean = prefs.contains(key)

    /** Store the hashed safe word for verification. */
    fun storeSafeWordHash(hash: String) {
        putSecret(KEY_SAFE_WORD_HASH, hash)
    }

    /** Verify a safe word attempt against the stored hash. */
    fun verifySafeWord(attemptHash: String): Boolean {
        val stored = getSecret(KEY_SAFE_WORD_HASH) ?: return false
        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(stored, attemptHash)
    }

    /** Store the Tailscale auth key for mesh reconnection. */
    fun storeTailscaleAuthKey(key: String) {
        putSecret(KEY_TAILSCALE_AUTH, key)
    }

    fun getTailscaleAuthKey(): String? = getSecret(KEY_TAILSCALE_AUTH)

    /** Wipe all vault contents. Irreversible. */
    fun wipeAll() {
        prefs.edit().clear().apply()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    companion object {
        private const val VAULT_FILE = "kid_zk_vault"
        const val KEY_SAFE_WORD_HASH = "safe_word_hash"
        const val KEY_TAILSCALE_AUTH = "tailscale_auth_key"
        const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
        const val KEY_OWNER_TOKEN = "owner_token"
    }
}
