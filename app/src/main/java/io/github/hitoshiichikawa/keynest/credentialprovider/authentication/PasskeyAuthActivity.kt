package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderGetCredentialRequest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.auth.AuthResult
import io.github.hitoshiichikawa.keynest.auth.BiometricAuthenticator
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Activity launched by the [PublicKeyCredentialEntry][androidx.credentials.provider.PublicKeyCredentialEntry]
 * pending intent (see [GetEntryBuilder]) for the authentication ceremony
 * (Issue #100 / parent #89 / design §4.6 / §5.2 / §9.3).
 *
 * Flow (no confirmation screen — design §4.6 chooses to surface the OS
 * sheet UX as the consent signal so this Activity goes straight to the
 * BiometricPrompt):
 *  1. `PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)`
 *     retrieves the OS-side request (or hands back null → unknown).
 *  2. credentialId is extracted from the Intent data Uri
 *     (`keynest://passkey/auth/<credentialId>`).
 *  3. BiometricPrompt fires via `BiometricAuthenticator`
 *     (`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`, #99 same pattern).
 *  4. On `AuthResult.Succeeded`: `PasskeyRepository.signWithIncrement` opens
 *     a Room transaction, bumps `signCount`, then the signer block runs
 *     `loadPrivateKey` → `PasskeyAssertion.sign` → builds the
 *     AuthenticationResponseJSON. Plaintext PKCS#8 is appended to
 *     `wipeQueue` and `fill(0)`-wiped in the `finally` block (NFR 1.1).
 *  5. `PendingIntentHandler.setGetCredentialResponse` / `setGetCredentialException`
 *     writes the OS reply into the result intent before
 *     `setResult(RESULT_OK) + finish()`.
 *  6. Option A rollback: any throw inside the signer block aborts the
 *     Room transaction so `signCount` reverts to its pre-increment value
 *     (design §6.1 / req 決定 3).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyAuthActivity : AppCompatActivity() {

    /**
     * Test seam (Issue #100 T-06) — Robolectric can swap in a fake
     * authenticator without standing up the full BiometricPrompt machinery.
     * Production code uses the default which wraps the real
     * [BiometricAuthenticator].
     */
    @VisibleForTesting
    internal var biometricAuthenticatorFactory: (FragmentActivity) -> BiometricAuthenticator =
        { BiometricAuthenticator(it) }

    /**
     * Test seam (Issue #100 T-06) — Robolectric can swap in the
     * Repository under test directly. Production code uses
     * [ServiceLocator.passkeyRepository].
     */
    @VisibleForTesting
    internal var passkeyRepositoryProvider: () -> PasskeyRepository =
        { ServiceLocator.passkeyRepository }

    @VisibleForTesting
    internal var authJob: Job? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Defensive — the CredentialProviderService process is the same JVM
        // as KeyNestApp, but the framework may bind a fresh process before
        // Application.onCreate completes (mirrors PasskeyCreateActivity).
        ServiceLocator.initialize(applicationContext)

        val providerRequest = try {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        } catch (t: Throwable) {
            SafeLogger.warn(
                tag = TAG,
                message = "retrieveProviderGetCredentialRequest failed",
                throwable = t,
            )
            null
        }
        if (providerRequest == null) {
            finishWithException(
                GetCredentialUnknownException("retrieveProviderGetCredentialRequest returned null"),
            )
            return
        }

        val publicKeyOption = providerRequest.credentialOptions
            .filterIsInstance<GetPublicKeyCredentialOption>()
            .firstOrNull()
        if (publicKeyOption == null) {
            finishWithException(
                GetCredentialUnknownException("No GetPublicKeyCredentialOption present in request"),
            )
            return
        }

        val credentialId = credentialIdFromIntent(intent)
        if (credentialId.isNullOrBlank()) {
            finishWithException(
                GetCredentialUnknownException("credentialId missing from PasskeyAuthActivity intent"),
            )
            return
        }

        val parsedRpId = try {
            AllowCredentialsParser.parseRpId(publicKeyOption.requestJson)
        } catch (t: IllegalArgumentException) {
            SafeLogger.warn(tag = TAG, message = "parseRpId failed", throwable = t)
            finishWithException(
                GetCredentialUnknownException("rpId missing from PublicKeyCredentialRequestOptions"),
            )
            return
        }

        authJob = runAuthenticationFlow(
            credentialId = credentialId,
            rpId = parsedRpId,
            clientDataJson = publicKeyOption.requestJson,
            providerRequest = providerRequest,
        )
    }

    @VisibleForTesting
    internal fun runAuthenticationFlow(
        credentialId: String,
        rpId: String,
        clientDataJson: String,
        @Suppress("UNUSED_PARAMETER") providerRequest: ProviderGetCredentialRequest,
    ): Job = lifecycleScope.launch {
        val wipeQueue = mutableListOf<ByteArray>()
        val resultIntent = Intent()
        try {
            val authenticator = biometricAuthenticatorFactory(this@PasskeyAuthActivity)
            val authResult = authenticator.authenticate(
                title = getString(R.string.passkey_auth_prompt_title),
                subtitle = getString(R.string.passkey_auth_prompt_subtitle),
            )
            when (authResult) {
                AuthResult.Succeeded -> Unit
                AuthResult.Cancelled ->
                    throw GetCredentialCancellationException("Biometric prompt cancelled")
                is AuthResult.Failed -> throw GetCredentialUnknownException(
                    "Biometric auth failed (code=${authResult.errorCode})",
                )
                is AuthResult.Unavailable -> throw GetCredentialUnknownException(
                    "Biometric unavailable: ${authResult.availability.name}",
                )
            }

            val repository = passkeyRepositoryProvider()

            // Lookup userHandle eagerly so we can include it in the response
            // JSON regardless of the in-block ordering.
            val entity = repository.findByCredentialId(credentialId)
                ?: throw GetCredentialUnknownException(
                    "PasskeyEntity not found for credentialId during assertion",
                )
            val userHandle = entity.userHandle

            val responseJson = repository.signWithIncrement(credentialId) { newSignCount ->
                val plaintext = repository.loadPrivateKey(credentialId)
                wipeQueue.add(plaintext)
                val assertion = PasskeyAssertion.sign(
                    PasskeyAssertionInput(
                        rpId = rpId,
                        clientDataJson = clientDataJson,
                        signCount = newSignCount,
                        privateKeyPkcs8 = plaintext,
                    ),
                )
                buildAuthenticationResponseJson(
                    credentialId = credentialId,
                    clientDataJson = clientDataJson,
                    userHandle = userHandle,
                    authenticatorData = assertion.authenticatorData,
                    signature = assertion.signature,
                )
            }

            PendingIntentHandler.setGetCredentialResponse(
                resultIntent,
                androidx.credentials.GetCredentialResponse(
                    PublicKeyCredential(responseJson),
                ),
            )
            setResult(RESULT_OK, resultIntent)
        } catch (e: GetCredentialException) {
            PendingIntentHandler.setGetCredentialException(resultIntent, e)
            setResult(RESULT_OK, resultIntent)
        } catch (t: Throwable) {
            SafeLogger.error(tag = TAG, message = "passkey assertion failed", throwable = t)
            PendingIntentHandler.setGetCredentialException(
                resultIntent,
                GetCredentialUnknownException(
                    "PassKey assertion failed: ${t.javaClass.simpleName}",
                ),
            )
            setResult(RESULT_OK, resultIntent)
        } finally {
            wipeQueue.forEach { it.fill(0) }
            wipeQueue.clear()
            finish()
        }
    }

    private fun finishWithException(exception: GetCredentialException) {
        val resultIntent = Intent()
        PendingIntentHandler.setGetCredentialException(resultIntent, exception)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Builds the WebAuthn AuthenticationResponseJSON (§5.1.4) by hand
     * using a [StringBuilder] — mirrors the registration ceremony's
     * `PasskeyCreator.buildRegistrationResponseJson` style (no new
     * dependency added / NFR 4.x). Base64url is unpadded
     * (design §7.3 / req 7 closure).
     */
    @VisibleForTesting
    internal fun buildAuthenticationResponseJson(
        credentialId: String,
        clientDataJson: String,
        userHandle: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
    ): String {
        val authenticatorDataB64 = base64UrlNoPad(authenticatorData)
        val signatureB64 = base64UrlNoPad(signature)
        val userHandleB64 = base64UrlNoPad(userHandle)
        val clientDataJsonB64 = base64UrlNoPad(clientDataJson.toByteArray(Charsets.UTF_8))

        val sb = StringBuilder(384)
        sb.append('{')
        sb.append("\"id\":\"").append(escapeJson(credentialId)).append('\"')
        sb.append(",\"rawId\":\"").append(escapeJson(credentialId)).append('\"')
        sb.append(",\"type\":\"public-key\"")
        sb.append(",\"authenticatorAttachment\":\"platform\"")
        sb.append(",\"response\":{")
        sb.append("\"clientDataJSON\":\"").append(escapeJson(clientDataJsonB64)).append('\"')
        sb.append(",\"authenticatorData\":\"").append(escapeJson(authenticatorDataB64)).append('\"')
        sb.append(",\"signature\":\"").append(escapeJson(signatureB64)).append('\"')
        sb.append(",\"userHandle\":\"").append(escapeJson(userHandleB64)).append('\"')
        sb.append('}')
        sb.append(",\"clientExtensionResults\":{}")
        sb.append('}')
        return sb.toString()
    }

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun escapeJson(s: String): String {
        // Base64url alphabet (A-Z a-z 0-9 - _) needs no escaping; keep the
        // helper around to harden against future field-shape drift.
        val needsEscape = s.any { it == '"' || it == '\\' || it < ' ' }
        if (!needsEscape) return s
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "KeyNest.PasskeyAuth"

        internal const val INTENT_DATA_SCHEME = "keynest"
        internal const val INTENT_DATA_AUTHORITY = "passkey"
        internal const val INTENT_DATA_PATH_PREFIX = "/auth/"

        private const val PENDING_INTENT_REQUEST_CODE = 0xCE9A

        /**
         * Builds the Intent that targets this Activity with the credentialId
         * encoded in the data Uri (`keynest://passkey/auth/<credentialId>`).
         * Mirrors `PasskeyCreateActivity.intent(...)` so concurrent entries
         * stay binder-distinct under `FLAG_UPDATE_CURRENT`.
         */
        @VisibleForTesting
        internal fun intent(context: Context, credentialId: String): Intent =
            Intent(context, PasskeyAuthActivity::class.java).apply {
                data = Uri.Builder()
                    .scheme(INTENT_DATA_SCHEME)
                    .authority(INTENT_DATA_AUTHORITY)
                    .path("$INTENT_DATA_PATH_PREFIX$credentialId")
                    .build()
            }

        /** PendingIntent factory used by [GetEntryBuilder]. */
        @VisibleForTesting
        internal fun pendingIntent(context: Context, credentialId: String): PendingIntent =
            PendingIntent.getActivity(
                context,
                PENDING_INTENT_REQUEST_CODE,
                intent(context, credentialId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Extracts the credentialId from an Intent built by [intent]. */
        @VisibleForTesting
        internal fun credentialIdFromIntent(intent: Intent): String? {
            val data = intent.data ?: return null
            if (data.scheme != INTENT_DATA_SCHEME) return null
            if (data.authority != INTENT_DATA_AUTHORITY) return null
            val path = data.path ?: return null
            if (!path.startsWith(INTENT_DATA_PATH_PREFIX)) return null
            val suffix = path.removePrefix(INTENT_DATA_PATH_PREFIX)
            return suffix.takeIf { it.isNotEmpty() }
        }
    }
}
