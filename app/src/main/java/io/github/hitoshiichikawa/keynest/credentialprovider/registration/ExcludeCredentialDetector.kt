package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository

/**
 * `excludeCredentials` の一致判定 helper（Issue #99 で導入、#136 で移設）。
 *
 * Issue #136: 以前は
 * [io.github.hitoshiichikawa.keynest.credentialprovider.KeyNestCredentialProviderService]
 * が **ユーザー認証より前に** 本判定を実行し、一致時に
 * `CreateCredentialNoCreateOptionException` を同期返却していた。これは
 * W3C WebAuthn Level 2 §6.3.2 step 5（excludeCredentials 一致のエラーは
 * user presence 取得後に返す）に反し、悪意ある RP / ローカルアプリが
 * ユーザー操作なしにクレデンシャル保有状況を列挙できる存在オラクルに
 * なっていた。現在は [PasskeyCreateActivity] が **生体認証成功後に** 呼ぶ。
 *
 * 判定は rpId でスコープする（WebAuthn §6.3.2 step 5 は
 * "credential.rpId equals rpEntity.id" の一致まで要求する。別 RP の
 * credentialId と偶然一致しても除外理由にならない）。
 *
 * suspend 化により旧実装の `runBlocking(Dispatchers.IO)`（Credential
 * Manager service callback スレッドのブロック要因）も撤去済み。呼び出し側
 * （Activity の lifecycleScope）が dispatcher を選択する。
 */
internal class ExcludeCredentialDetector(
    private val repository: PasskeyRepository,
) {
    /**
     * [credentialIds] のいずれかが、KeyNest vault 内の **同一 [rpId]** の
     * PassKey として既に存在するとき true。
     */
    suspend fun containsAny(rpId: String, credentialIds: List<String>): Boolean {
        if (credentialIds.isEmpty()) return false
        return credentialIds.any { id ->
            repository.findByCredentialId(id)?.rpId == rpId
        }
    }
}
