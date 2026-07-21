package dev.mias.mobile.bridge

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mias.core.security.ZkVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Secrets + biometric re-auth for the RN app.
 *
 * Secrets live in the native `ZkVault` (EncryptedSharedPreferences, StrongBox
 * MasterKey) — they cross the bridge only transiently on request and must never
 * be cached in JS-accessible storage (AsyncStorage etc.).
 *
 * Note: the Bridge pairing device keys (stage S5) additionally get
 * `setUserAuthenticationRequired` Keystore keys unlocked via a
 * `BiometricPrompt.CryptoObject` — created where those keys are minted, not here.
 */
class SecurityBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SecurityEntryPoint {
        fun vault(): ZkVault
    }

    private val vault by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            SecurityEntryPoint::class.java,
        ).vault()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getName(): String = NAME

    @ReactMethod
    fun secureGet(key: String, promise: Promise) {
        scope.launch { promise.resolve(runCatching { vault.getSecret(key) }.getOrNull()) }
    }

    @ReactMethod
    fun secureSet(key: String, value: String, promise: Promise) {
        scope.launch {
            runCatching { vault.putSecret(key, value) }
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("vault_set", it.message, it) }
        }
    }

    @ReactMethod
    fun secureRemove(key: String, promise: Promise) {
        scope.launch {
            runCatching { vault.removeSecret(key) }
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun hasSecret(key: String, promise: Promise) {
        scope.launch { promise.resolve(runCatching { vault.hasSecret(key) }.getOrDefault(false)) }
    }

    /**
     * Strong-biometric re-auth for sensitive in-app actions (viewing secrets,
     * scope escalation, future Bridge approvals). Resolves true on success,
     * false on user cancel/lockout; rejects only on setup problems.
     */
    @ReactMethod
    fun authenticate(title: String, subtitle: String, promise: Promise) {
        val activity = reactContext.currentActivity as? FragmentActivity
        if (activity == null) {
            promise.reject("auth_no_activity", "No foreground activity for the biometric prompt")
            return
        }
        val can = BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // No usable strong biometric — report false rather than blocking flows.
            promise.resolve(false)
            return
        }
        activity.runOnUiThread {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        promise.resolve(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        promise.resolve(false)
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title.ifBlank { "Verify identity" })
                    .setSubtitle(subtitle.ifBlank { "Only you can access Mias" })
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setConfirmationRequired(true)
                    .build(),
            )
        }
    }

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    companion object {
        const val NAME = "MiasSecurity"
    }
}
