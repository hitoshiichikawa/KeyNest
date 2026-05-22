package io.github.hitoshiichikawa.keynest.credentialprovider

import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * KeyNest の PassKey プロバイダ実装。
 *
 * Issue #99 (parent #89) — registration ceremony implementation. The
 * `onBeginCreateCredentialRequest` callback now:
 *  1. returns an empty response for non-PublicKey requests (password etc.
 *     stay on the autofill route);
 *  2. parses `excludeCredentials` from `requestJson` and answers
 *     `CreateCredentialNoCreateOptionException` if any id is already in
 *     the KeyNest vault (req 5.2 / 5.3 / 決定 3);
 *  3. otherwise builds a single [androidx.credentials.provider.CreateEntry]
 *     whose pending intent launches `PasskeyCreateActivity`.
 *
 * Issue #100 (parent #89) — authentication ceremony implementation.
 * `onBeginGetCredentialRequest` is now wired to
 * [io.github.hitoshiichikawa.keynest.credentialprovider.authentication.GetEntryBuilder]
 * which dispatches the `allowCredentials` empty / specified branches
 * (R1.1 / R1.2) and returns 0..N `PublicKeyCredentialEntry`. Empty results
 * surface as an empty `BeginGetCredentialResponse` so the OS sheet does
 * NOT show KeyNest at all (R1.3 / R1.5). `onClearCredentialStateRequest`
 * is left at the #90 stub — that API belongs to the settings Issue.
 *
 * NFR 5.3 / 5.1: the callback returns synchronously without blocking on
 * long crypto operations — heavy lifting (keypair generation / signature /
 * decrypt) lives in `PasskeyCreateActivity` and `PasskeyAuthActivity`.
 * The repository lookups that back `excludeCredentials` (#99) and
 * `allowCredentials` / `listDiscoverableByRpId` (#100) are bounded
 * (point lookup × small N) so the `runBlocking(IO)` inside the helpers
 * stays well within ANR limits (design §4.1.1).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class KeyNestCredentialProviderService : CredentialProviderService() {

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        // (a) Defensive ServiceLocator init — the framework may bind the
        // Service before Application.onCreate completes on a hostile boot.
        ServiceLocator.initialize(applicationContext)

        // (b) PublicKey 以外は本 Issue では非対応 — 空応答で返す
        val publicKeyRequest = request as? BeginCreatePublicKeyCredentialRequest
            ?: return callback.onResult(BeginCreateCredentialResponse())

        // (c) excludeCredentials を生体認証より前にチェック (Req 5.3)
        val excludeIds = extractExcludeCredentialIds(publicKeyRequest.requestJson)
        if (excludeIds.isNotEmpty() &&
            ServiceLocator.excludeCredentialDetector.containsAny(excludeIds)
        ) {
            callback.onError(
                CreateCredentialNoCreateOptionException(
                    "PassKey for one of the supplied credential ids already exists in KeyNest",
                ),
            )
            return
        }

        // (d) CreateEntry を 1 件構築して返す
        val response = BeginCreateCredentialResponse.Builder()
            .addCreateEntry(ServiceLocator.createEntryBuilder.build(publicKeyRequest))
            .build()
        callback.onResult(response)
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        // (a) Defensive ServiceLocator init — same pattern as
        // onBeginCreateCredentialRequest above.
        ServiceLocator.initialize(applicationContext)

        // (b) Filter for PublicKey options only. Password / other options
        // fall back to the empty response per R1.5 (the autofill route
        // owns password credentials).
        val publicKeyOptions: List<BeginGetPublicKeyCredentialOption> = request
            .beginGetCredentialOptions
            .filterIsInstance<BeginGetPublicKeyCredentialOption>()
        if (publicKeyOptions.isEmpty()) {
            callback.onResult(BeginGetCredentialResponse.Builder().build())
            return
        }

        // (c) Build candidates for each PublicKey option. GetEntryBuilder
        // returns an empty list when no candidates match — those are
        // simply omitted from the response (R1.3).
        val entries = publicKeyOptions.flatMap { option ->
            ServiceLocator.getEntryBuilder.build(option)
        }

        val responseBuilder = BeginGetCredentialResponse.Builder()
        entries.forEach(responseBuilder::addCredentialEntry)
        callback.onResult(responseBuilder.build())
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        // #90 stub retained — clear-state is owned by the settings Issue.
        callback.onResult(null)
    }

    private fun extractExcludeCredentialIds(requestJson: String): List<String> {
        return try {
            val root = json.parseToJsonElement(requestJson).jsonObject
            val excludeArr = root["excludeCredentials"]?.jsonArray ?: return emptyList()
            excludeArr.mapNotNull { element ->
                element.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }
        } catch (_: Throwable) {
            // Defensive: a malformed requestJson should NOT block registration.
            // Fall through and let the rest of the callback proceed.
            emptyList()
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
