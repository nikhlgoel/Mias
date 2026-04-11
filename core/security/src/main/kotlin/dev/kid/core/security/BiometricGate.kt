package dev.kid.core.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Biometric Gate — guards access to {Kid} with the owner's biometrics.
 *
 * Uses Class 3 (BIOMETRIC_STRONG) only — fingerprint, face, or iris
 * backed by hardware security module. No screen-lock fallback unless
 * the owner explicitly configures it.
 */
@Singleton
class BiometricGate @Inject constructor() {

    /** Check if the device supports strong biometric authentication. */
    fun canAuthenticate(activity: FragmentActivity): BiometricStatus {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNKNOWN
        }
    }

    /**
     * Request biometric authentication.
     * Returns [AuthResult.Success] or [AuthResult.Failure] with reason.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Verify Identity",
        subtitle: String = "Only you can access {Kid}",
        negativeButtonText: String = "Cancel",
    ): AuthResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) {
                    continuation.resume(
                        AuthResult.Success(
                            authenticationType = result.authenticationType,
                        ),
                    )
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) {
                    continuation.resume(
                        AuthResult.Failure(
                            errorCode = errorCode,
                            message = errString.toString(),
                        ),
                    )
                }
            }

            override fun onAuthenticationFailed() {
                // Called on each failed attempt, but the system keeps the dialog open.
                // We don't resume here — we wait for onAuthenticationError or onAuthenticationSucceeded.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()

        prompt.authenticate(promptInfo)

        continuation.invokeOnCancellation {
            prompt.cancelAuthentication()
        }
    }

    sealed interface AuthResult {
        data class Success(val authenticationType: Int) : AuthResult
        data class Failure(val errorCode: Int, val message: String) : AuthResult
    }

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NOT_ENROLLED,
        UNKNOWN,
    }
}
