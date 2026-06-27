package dev.mias.mobile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Cold-start biometric gate (Phase R0.4). This is the launcher activity: it
 * authenticates the owner with a strong (Class 3) biometric BEFORE the React root
 * (MainActivity) mounts, so no app content renders pre-auth.
 *
 * Standalone on purpose — uses androidx.biometric directly, with no Hilt or
 * :core dependency yet (those arrive in R1 with the first native module). R1/R2
 * replace this with the :core `SecurityModule`/`BiometricGate` backed by a
 * Keystore key bound via `BiometricPrompt.CryptoObject` (closes the "UI gate is
 * not a key binding" gap, bridge/docs/07 M15).
 *
 * If the device has no enrolled strong biometric we currently degrade to proceed;
 * enforcement policy (and the secure-key binding) lands with the SecurityModule.
 */
class BiometricGateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> promptBiometric()
            // No usable strong biometric (no hardware / none enrolled / unavailable):
            // degrade to proceed for R0. R2 decides the strict-enforcement policy.
            else -> proceed()
        }
    }

    private fun promptBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    proceed()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancel / lockout / unrecoverable error: close without opening
                    // the app (don't leave a half-gated session in the back stack).
                    finishAndRemoveTask()
                }
                // onAuthenticationFailed (a single bad read) keeps the system dialog
                // open for a retry — nothing to do here.
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Mias")
            .setSubtitle("Only you can access Mias")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()
        prompt.authenticate(info)
    }

    private fun proceed() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
