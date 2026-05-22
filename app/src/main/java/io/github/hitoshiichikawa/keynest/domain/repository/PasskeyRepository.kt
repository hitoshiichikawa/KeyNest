package io.github.hitoshiichikawa.keynest.domain.repository

import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import kotlinx.coroutines.flow.Flow

/**
 * Domain port for PassKey persistence (Issue #99 / parent #89).
 *
 * Inlined into this PR ahead of the umbrella `PasskeyRepository` Issue
 * (#107) so that Issue #99 (registration ceremony) can be reviewed
 * end-to-end. Shape mirrors what #107 design.md §6.x specifies so a
 * later #107 merge can collapse to a no-op rename.
 *
 * Encryption boundary: implementations own the AES-GCM encryption of
 * [SavePasskeyRequest.privateKey] (the plaintext PKCS#8 byte array)
 * before INSERT; the caller never persists the plaintext itself.
 */
interface PasskeyRepository {

    /**
     * Encrypt [request.privateKey], INSERT the PassKey row, and create
     * the corresponding AndroidKeyStore wrapping key on first use. The
     * caller is responsible for zero-filling [request.privateKey] after
     * the call returns (NFR 1.3).
     */
    suspend fun save(request: SavePasskeyRequest)

    /** Look up a PassKey by its WebAuthn credentialId. Used by excludeCredentials checks. */
    suspend fun findByCredentialId(credentialId: String): PasskeyEntity?

    /** Look up a PassKey by `(rpId, userHandle)`. Returns 0 or 1 row (UNIQUE). */
    suspend fun findByRpIdAndUserHandle(rpId: String, userHandle: ByteArray): PasskeyEntity?

    /**
     * Delete the PassKey row and its wrapping key alias. Surfaces a
     * [DeletePasskeyResult.KeystoreCleanupFailed] when the row delete
     * succeeded but the AndroidKeyStore delete raised. DB exceptions
     * (`SQLiteException`) propagate to the caller.
     */
    suspend fun delete(credentialId: String): DeletePasskeyResult

    // ---- Issue #100 (authentication ceremony) additions ----------------
    //
    // Backward-compatible — existing call sites (PasskeyCreateActivity /
    // KeyNestCredentialProviderService.onBeginCreateCredentialRequest) are
    // unaffected. design §4.5 / §6 covers the contract; the implementations
    // live in PasskeyRepositoryImpl.

    /**
     * Discoverable PassKey の一覧を rpId で抽出する (usernameless login 経路 /
     * Issue #100 R1.1 / design §4.5.1). 並び順は DAO の `listDiscoverableByRpId`
     * の契約 (lastUsedAt DESC nulls last, createdAt DESC) に従う。
     */
    suspend fun listDiscoverableByRpId(rpId: String): List<PasskeyEntity>

    /**
     * `(privateKeyIv, encryptedPrivateKey)` を `keynest_passkey_<credentialId>`
     * alias の `KeystoreKeyProvider` + `AesGcmCipher` で AES-GCM 復号して
     * **平文 PKCS#8** byte 配列を返す (Issue #100 R3.1 / design §4.5.1).
     *
     * 呼び出し側 (`PasskeyAuthActivity`) は使用直後に `ByteArray.fill(0)` で
     * wipe する責務を負う (NFR 1.1).
     *
     * @throws IllegalStateException 該当 credentialId が存在しない場合
     * @throws javax.crypto.AEADBadTagException ciphertext / IV の改竄を GCM auth tag が検出した場合
     */
    suspend fun loadPrivateKey(credentialId: String): ByteArray

    /**
     * Option A (Issue #100 決定 3) を **Repository 内で原子化** するための
     * 高階関数 API (design §4.5.1 / §6.1 案 C).
     *
     * フロー:
     *  1. Room transaction を開始する。
     *  2. DAO の `incrementSignCount(credentialId, nowMillis())` を呼んで signCount を +1。
     *  3. 新 signCount を SELECT で取得し [signer] に渡す。
     *  4. [signer] が結果を返したら transaction を commit してその戻り値を返す。
     *  5. [signer] が throw / cancel したら transaction を rollback して例外を伝播する
     *     (signCount は元値に戻る)。
     *
     * 失敗時ロールバックを呼び出し側で書き忘れるリスクをなくすため、高階関数
     * 形式 (案 C) を採用した (design §6.1)。
     *
     * @param signer 新 signCount を受け取って assertion bytes (任意の戻り値) を生成するブロック。
     * @return [signer] の戻り値。例外時は [signer] が投げた例外をそのまま伝播。
     */
    suspend fun <T> signWithIncrement(
        credentialId: String,
        signer: suspend (newSignCount: Long) -> T,
    ): T

    // ---- Issue #101 (Phase 4 of umbrella #89) -------------------------
    //
    // Backs the merged password+PassKey credential list. Pure additive
    // change — existing call sites are unaffected.

    /**
     * Observer-style list of every PassKey stored in KeyNest. The returned
     * Flow is wired straight to Room's invalidation tracker so the
     * credential list refreshes whenever the registration / authentication
     * ceremonies (#99 / #100) write to the `passkeys` table.
     *
     * Ordering matches the existing per-RP DAO queries:
     * `lastUsedAt DESC` (NULL last), then `createdAt DESC` as a stable
     * tiebreaker. Discoverable / non-discoverable rows are both included
     * (umbrella #89 確定事項 D-7).
     */
    fun listAll(): Flow<List<PasskeyEntity>>
}
