package com.example.keynest.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Coroutine-friendly wrapper around AndroidX [BiometricPrompt].
 *
 * Requirements: 5.2, 5.3, 5.4
 *
 * Authenticator selection follows the design.md decision:
 *   `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`
 *
 * This gives users a path forward on devices without a biometric sensor
 * (they can use the PIN / pattern / password lock screen) while still
 * preferring strong biometrics where available.
 *
 * Results are surfaced as the sealed [AuthResult] so callers can branch
 * deterministically without catching exceptions.
 */
class BiometricAuthenticator(
    private val activity: FragmentActivity,
) {

    /** Returns a description of whether biometric / device auth is even possible. */
    fun availability(): Availability {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.Ready
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> Availability.NotSupported
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> Availability.UpdateRequired
            else -> Availability.NotSupported
        }
    }

    /**
     * Launches the BiometricPrompt and suspends until the user resolves it.
     *
     * The coroutine is cancellable - if cancelled, the prompt is dismissed
     * via [BiometricPrompt.cancelAuthentication] (Req 5.4 transparency).
     */
    suspend fun authenticate(title: String, subtitle: String? = null): AuthResult {
        val availability = availability()
        if (availability != Availability.Ready) {
            return AuthResult.Unavailable(availability)
        }

        return suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(AuthResult.Succeeded)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!cont.isActive) return
                        cont.resume(
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_CANCELED
                            ) AuthResult.Cancelled
                            else AuthResult.Failed(errorCode, errString.toString()),
                        )
                    }

                    override fun onAuthenticationFailed() {
                        // Single biometric attempt failed but the prompt is
                        // still active. Do NOT resolve the coroutine here -
                        // the user may retry.
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()

            cont.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(info)
        }
    }

    enum class Availability {
        Ready,
        NotSupported,
        NotEnrolled,
        UpdateRequired,
    }

    companion object {
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

/** Result returned by [BiometricAuthenticator.authenticate]. Requirements: 5.2, 5.3, 5.4. */
sealed class AuthResult {
    /** Biometric or device auth succeeded - caller may proceed with decryption. */
    object Succeeded : AuthResult()

    /** User explicitly dismissed the prompt or hit the negative button. */
    object Cancelled : AuthResult()

    /** Auth failed in an unrecoverable way (e.g. too many attempts, lockout). */
    data class Failed(val errorCode: Int, val message: String) : AuthResult()

    /** Device has no biometric hardware or no enrolled credential / lock screen. */
    data class Unavailable(val availability: BiometricAuthenticator.Availability) : AuthResult()
}
