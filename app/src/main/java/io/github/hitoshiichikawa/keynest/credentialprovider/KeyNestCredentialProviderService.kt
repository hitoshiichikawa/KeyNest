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
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest

/**
 * KeyNest の PassKey プロバイダ実装 (Phase 1 骨格 / Issue #90).
 *
 * 本 Issue (#90) ではすべての callback がエントリ 0 件の成功応答を返す。
 * 登録 / 認証セレモニーの実体は後続 Issue (#89 分割案 3 / 4) で
 * `onBeginCreateCredentialRequest` / `onBeginGetCredentialRequest` を差し替える形で
 * 実装される。
 *
 * OS は Android 14 (API 34) 以降でのみ本 Service を bind する。class 全体に
 * `@RequiresApi(34)` を付与し、API 33 以下での誤参照を lint で検知可能にする
 * (design §6.2 / NFR 4.2)。runtime SDK_INT ガードは追加しない (design §6.2 で
 * dead-branch を作らない方針を確定)。
 *
 * NFR 1.3: callback が受け取る `BeginCreateCredentialRequest` /
 * `BeginGetCredentialRequest` / `ProviderClearCredentialStateRequest` 内の
 * 呼び出し元 package / origin を Logcat に出力しない。本実装はそもそも一切ログ
 * 出力しないため自動的に成立する。
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class KeyNestCredentialProviderService : CredentialProviderService() {

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        // Phase 1 (req 3.2): エントリ 0 件の成功応答を返す。
        // 後続 Issue (#89 分割案 3) で BeginCreateCredentialResponse.Builder().addCreateEntry(...)
        // 経由の応答に差し替える。
        callback.onResult(BeginCreateCredentialResponse())
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        // Phase 1 (req 3.3): credentialEntries / authenticationActions / actions / remoteEntry
        // をすべて空のまま build。後続 Issue (#89 分割案 4) で addCredentialEntry(...) 経由の
        // 応答に差し替える。
        callback.onResult(BeginGetCredentialResponse.Builder().build())
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        // Phase 1 (req 3.4): 状態を持たないため何も変更せず正常終了応答を返す。
        callback.onResult(null)
    }
}
