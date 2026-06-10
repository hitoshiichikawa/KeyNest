package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.github.hitoshiichikawa.keynest.R
import io.github.hitoshiichikawa.keynest.auth.AuthResult
import io.github.hitoshiichikawa.keynest.auth.BiometricAuthenticator
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity launched by the [CreateEntry][androidx.credentials.provider.CreateEntry]
 * pending intent (see [CreateEntryBuilder]).
 *
 * Issue #99 / parent #89 / design §4.8 / §5.2 / §9.3.
 *
 * Flow:
 *  1. `PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)`
 *     retrieves the OS-side request (or hands back null → unknown).
 *  2. Confirmation screen (`activity_passkey_create.xml`) shows the RP name
 *     and user display name with Save / Cancel buttons.
 *  3. On Save: `BiometricAuthenticator.authenticate(...)` is launched. The
 *     authenticator is `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` so PIN /
 *     pattern fallback works on devices without a sensor (#89 確定事項).
 *  4. On `AuthResult.Succeeded`: まず `excludeCredentials` を照合する
 *     (Issue #136)。一致（同一 rpId）なら InvalidStateError の DOM 例外で
 *     セレモニーを終了する — WebAuthn §6.3.2 step 5 は除外一致のエラーを
 *     user presence 取得 **後** に返すことを要求するため、Service 側
 *     （認証前）ではなくここで判定する。続いて `PasskeyCreator.create(...)`
 *     runs on `Dispatchers.IO`. Existing `(rpId, userHandle)` row is
 *     deleted first (overwrite path, §5.3.2), then the new row is INSERTed
 *     via `PasskeyRepository.save(...)`.
 *  5. The plaintext PKCS#8 private key in [SavePasskeyRequest.privateKey]
 *     is `fill(0)`-wiped in the `finally` block (NFR 1.3 / req 1.7).
 *  6. `PendingIntentHandler.setCreateCredentialResponse(...)` or
 *     `setCreateCredentialException(...)` writes the OS reply into the
 *     result intent before `setResult(RESULT_OK) + finish()`.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyCreateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Defensive — the AutofillService process / CredentialProviderService
        // process is the same JVM as KeyNestApp, but the framework may bind
        // a fresh process before Application.onCreate completes.
        ServiceLocator.initialize(applicationContext)

        setContentView(R.layout.activity_passkey_create)

        val providerRequest = try {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        } catch (t: Throwable) {
            SafeLogger.warn(tag = TAG, message = "retrieveProviderCreateCredentialRequest failed", throwable = t)
            null
        }
        if (providerRequest == null) {
            finishWithException(
                CreateCredentialUnknownException("retrieveProviderCreateCredentialRequest returned null"),
            )
            return
        }

        val publicKeyRequest = providerRequest.callingRequest as? CreatePublicKeyCredentialRequest
        if (publicKeyRequest == null) {
            // Non-publicKey requests should never reach this Activity (Service
            // returns empty CreateEntry list for them), but be defensive.
            finishWithException(
                CreateCredentialUnknownException("Non-publicKey create request reached PasskeyCreateActivity"),
            )
            return
        }

        val parsed = try {
            parseCreationOptions(publicKeyRequest.requestJson)
        } catch (t: Throwable) {
            SafeLogger.warn(tag = TAG, message = "parseCreationOptions failed", throwable = t)
            finishWithException(
                CreateCredentialUnknownException("Failed to parse PublicKeyCredentialCreationOptions"),
            )
            return
        }

        bindConfirmationUi(parsed)

        findViewById<Button>(R.id.passkey_create_cancel_button).setOnClickListener {
            finishWithException(
                CreateCredentialCancellationException("User cancelled the PassKey confirmation"),
            )
        }
        findViewById<Button>(R.id.passkey_create_save_button).setOnClickListener { btn ->
            // Disable the buttons immediately so a double-tap can't reenter
            // the suspending flow.
            btn.isEnabled = false
            findViewById<View>(R.id.passkey_create_cancel_button).isEnabled = false
            runRegistrationFlow(parsed)
        }
    }

    private fun bindConfirmationUi(parsed: CreationOptions) {
        findViewById<TextView>(R.id.passkey_create_rp_label).text =
            parsed.rpDisplayName?.takeIf { it.isNotBlank() } ?: parsed.rpId
        findViewById<TextView>(R.id.passkey_create_user_label).text =
            parsed.userDisplayName ?: parsed.userName ?: ""
    }

    @VisibleForTesting
    internal fun runRegistrationFlow(parsed: CreationOptions) {
        lifecycleScope.launch {
            val wipeQueue = mutableListOf<ByteArray>()
            val resultIntent = Intent()
            try {
                val authResult = BiometricAuthenticator(this@PasskeyCreateActivity as FragmentActivity)
                    .authenticate(
                        title = getString(R.string.passkey_biometric_prompt_title),
                        subtitle = getString(R.string.passkey_biometric_prompt_subtitle),
                    )
                when (authResult) {
                    AuthResult.Succeeded -> Unit
                    AuthResult.Cancelled ->
                        throw CreateCredentialCancellationException("Biometric prompt cancelled")
                    is AuthResult.Failed -> throw CreateCredentialUnknownException(
                        "Biometric auth failed (code=${authResult.errorCode})",
                    )
                    is AuthResult.Unavailable -> throw CreateCredentialUnknownException(
                        "Biometric unavailable: ${authResult.availability.name}",
                    )
                }

                // Issue #136: excludeCredentials は生体認証成功後にのみ照合
                // する（WebAuthn §6.3.2 step 5 — user presence 前に一致を
                // 漏らさない）。一致時の InvalidStateError は RP が
                // 「既に登録済み」と解釈する spec 上の正規シグナル。
                val excluded = withContext(Dispatchers.IO) {
                    ServiceLocator.excludeCredentialDetector.containsAny(
                        rpId = parsed.rpId,
                        credentialIds = parsed.excludeCredentialIds,
                    )
                }
                if (excluded) {
                    throw CreatePublicKeyCredentialDomException(
                        InvalidStateError(),
                        "A PassKey for one of the excluded credential ids already exists for this RP",
                    )
                }

                val passkeyCreator = ServiceLocator.passkeyCreator
                val repository = ServiceLocator.passkeyRepository

                val input = PasskeyCreateInput(
                    rpId = parsed.rpId,
                    rpDisplayName = parsed.rpDisplayName,
                    userHandle = parsed.userHandle,
                    userName = parsed.userName,
                    userDisplayName = parsed.userDisplayName,
                    isDiscoverable = parsed.isDiscoverable,
                )

                val created = withContext(Dispatchers.IO) {
                    passkeyCreator.create(applicationContext, input)
                }
                // Plaintext private key must be wiped no matter what happens next.
                wipeQueue.add(created.savePasskeyRequest.privateKey)

                // Overwrite the existing (rpId, userHandle) row first (req 5.1).
                val existing = withContext(Dispatchers.IO) {
                    repository.findByRpIdAndUserHandle(parsed.rpId, parsed.userHandle)
                }
                if (existing != null) {
                    val deleteResult = withContext(Dispatchers.IO) {
                        repository.delete(existing.credentialId)
                    }
                    if (deleteResult is DeletePasskeyResult.KeystoreCleanupFailed) {
                        // Best-effort recovery: row already removed, alias
                        // orphaned but new INSERT will register a fresh alias
                        // (design §5.3.2 chose to proceed).
                        SafeLogger.warn(
                            tag = TAG,
                            message = "delete returned KeystoreCleanupFailed; proceeding with INSERT",
                            throwable = deleteResult.cause,
                        )
                    }
                }

                withContext(Dispatchers.IO) {
                    repository.save(created.savePasskeyRequest)
                }

                PendingIntentHandler.setCreateCredentialResponse(
                    resultIntent,
                    CreatePublicKeyCredentialResponse(created.registrationResponseJson),
                )
                setResult(RESULT_OK, resultIntent)
            } catch (e: CreateCredentialException) {
                PendingIntentHandler.setCreateCredentialException(resultIntent, e)
                setResult(RESULT_OK, resultIntent)
            } catch (t: Throwable) {
                SafeLogger.error(tag = TAG, message = "passkey create failed", throwable = t)
                PendingIntentHandler.setCreateCredentialException(
                    resultIntent,
                    CreateCredentialUnknownException(
                        "PassKey creation failed: ${t.javaClass.simpleName}",
                    ),
                )
                setResult(RESULT_OK, resultIntent)
            } finally {
                wipeQueue.forEach { it.fill(0) }
                wipeQueue.clear()
                finish()
            }
        }
    }

    private fun finishWithException(exception: CreateCredentialException) {
        val resultIntent = Intent()
        PendingIntentHandler.setCreateCredentialException(resultIntent, exception)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Minimal WebAuthn `PublicKeyCredentialCreationOptions` JSON parsing.
     * Only the fields needed for [PasskeyCreator] are extracted; defaults
     * follow WebAuthn Level 2 §5.4.6 ("preferred" when `residentKey` is
     * omitted → `isDiscoverable = true`, req 3.3).
     */
    @VisibleForTesting
    internal fun parseCreationOptions(requestJson: String): CreationOptions {
        val root = jsonParser.parseToJsonElement(requestJson).jsonObject
        val rp = root["rp"]?.jsonObject
        val user = root["user"]?.jsonObject
        val authSel = root["authenticatorSelection"]?.jsonObject

        val rpId = rp?.get("id")?.jsonPrimitive?.contentOrNull
            ?: error("PublicKeyCredentialCreationOptions.rp.id missing")
        val rpName = rp["name"]?.jsonPrimitive?.contentOrNull

        val userIdB64 = user?.get("id")?.jsonPrimitive?.contentOrNull
            ?: error("PublicKeyCredentialCreationOptions.user.id missing")
        val userName = user["name"]?.jsonPrimitive?.contentOrNull
        val userDisplayName = user["displayName"]?.jsonPrimitive?.contentOrNull

        val residentKey = authSel?.get("residentKey")?.jsonPrimitive?.contentOrNull
        val isDiscoverable = when (residentKey?.lowercase()) {
            "discouraged" -> false
            // "required", "preferred", or omitted (default = preferred per
            // WebAuthn §5.4.6).
            else -> true
        }

        val userHandle = Base64.decode(userIdB64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        // Issue #136: excludeCredentials[].id（base64url 文字列）。欠落 /
        // 形不正の要素は無視する（除外照合は best-effort で、欠けても
        // 登録自体は WebAuthn 的に有効なため）。
        val excludeCredentialIds = root["excludeCredentials"]?.jsonArray
            ?.mapNotNull { element ->
                runCatching {
                    element.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
            }
            .orEmpty()

        return CreationOptions(
            rpId = rpId,
            rpDisplayName = rpName,
            userHandle = userHandle,
            userName = userName,
            userDisplayName = userDisplayName,
            isDiscoverable = isDiscoverable,
            excludeCredentialIds = excludeCredentialIds,
        )
    }

    /**
     * Subset of WebAuthn `PublicKeyCredentialCreationOptions` carried into
     * [PasskeyCreator]. Internal data class so test fixtures can construct
     * it directly without hitting the JSON parser.
     */
    @VisibleForTesting
    internal data class CreationOptions(
        val rpId: String,
        val rpDisplayName: String?,
        val userHandle: ByteArray,
        val userName: String?,
        val userDisplayName: String?,
        val isDiscoverable: Boolean,
        /** Issue #136: 認証成功後の除外照合に使う excludeCredentials[].id。 */
        val excludeCredentialIds: List<String> = emptyList(),
    )

    companion object {
        private const val TAG = "KeyNest.PasskeyCreate"

        internal const val INTENT_DATA_SCHEME = "keynest"
        internal const val INTENT_DATA_AUTHORITY = "passkey"
        internal const val INTENT_DATA_PATH_PREFIX = "/create/"

        internal fun intent(context: Context, requestToken: Int): Intent =
            Intent(context, PasskeyCreateActivity::class.java).apply {
                data = Uri.Builder()
                    .scheme(INTENT_DATA_SCHEME)
                    .authority(INTENT_DATA_AUTHORITY)
                    .path("$INTENT_DATA_PATH_PREFIX$requestToken")
                    .build()
            }

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
