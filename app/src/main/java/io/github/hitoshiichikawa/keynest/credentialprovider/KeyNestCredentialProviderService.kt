package io.github.hitoshiichikawa.keynest.credentialprovider

import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
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

/**
 * KeyNest の PassKey プロバイダ実装。
 *
 * Issue #99 (parent #89) — registration ceremony implementation. The
 * `onBeginCreateCredentialRequest` callback now:
 *  1. returns an empty response for non-PublicKey requests (password etc.
 *     stay on the autofill route);
 *  2. builds a single [androidx.credentials.provider.CreateEntry]
 *     whose pending intent launches `PasskeyCreateActivity`.
 *
 * Issue #136: `excludeCredentials` の照合は本 Service では行わない。
 * 旧実装（#99 req 5.3）は生体認証より前に一致を
 * `CreateCredentialNoCreateOptionException` で返しており、ユーザー操作
 * なしにクレデンシャル保有状況を観測できる存在オラクルになっていた
 * （W3C WebAuthn Level 2 §6.3.2 step 5 違反）。照合は
 * `PasskeyCreateActivity` が生体認証成功後に行い、一致時は
 * InvalidStateError 系 DOM 例外を返す。
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
 * The repository lookups that back `allowCredentials` /
 * `listDiscoverableByRpId` (#100) are bounded (point lookup × small N) so
 * the `runBlocking(IO)` inside the helpers stays well within ANR limits
 * (design §4.1.1).
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

        // (c) CreateEntry を 1 件構築して返す。
        // excludeCredentials はここでは見ない（#136）: 認証前に応答を
        // 分岐させると保有状況が呼び出し元へ漏れるため、照合と
        // InvalidStateError 返却は PasskeyCreateActivity の生体認証成功後
        // に行う（WebAuthn §6.3.2 step 5）。
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

}
