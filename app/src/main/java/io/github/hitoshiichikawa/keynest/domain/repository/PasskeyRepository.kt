package io.github.hitoshiichikawa.keynest.domain.repository

import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.Passkey
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import kotlinx.coroutines.flow.Flow

/**
 * Domain port for PassKey persistence (Issue #91 design §6.1 / #107).
 *
 * The interface is aligned with the shared design.md §6.1 shape:
 *  - lookup / list APIs return [Passkey] domain aggregates rather than
 *    persistence-layer entities so that the AES-GCM ciphertext, GCM IV
 *    and AndroidKeyStore wrapping-key alias never leak across the
 *    repository boundary (NFR 2.2).
 *  - [incrementSignCount] is exposed directly to mirror design §6.1.
 *  - [loadPrivateKey] returns `ByteArray?` — `null` when the underlying
 *    row is absent. Decryption failure is propagated as an exception
 *    (no silent fail / NFR 2.5).
 *  - [listAllByRpId] is added so the future management UI can list
 *    non-discoverable rows alongside discoverable ones.
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

    /**
     * Look up a PassKey by its WebAuthn credentialId. Returns `null` when
     * no row matches. Used by excludeCredentials checks and by the
     * authentication ceremony's `allowCredentials` path.
     */
    suspend fun findByCredentialId(credentialId: String): Passkey?

    /**
     * Look up a PassKey by `(rpId, userHandle)`. Returns 0 or 1 row
     * because the pair carries a UNIQUE index (Issue #91 決定 2).
     */
    suspend fun findByRpIdAndUserHandle(rpId: String, userHandle: ByteArray): Passkey?

    /**
     * Discoverable PassKeys for the given `rpId` (usernameless login
     * route / Issue #100 R1.1 / design §4.5.1). Ordering follows the DAO
     * contract (`lastUsedAt DESC nulls last`, then `createdAt DESC`).
     */
    suspend fun listDiscoverableByRpId(rpId: String): List<Passkey>

    /**
     * All PassKeys (discoverable + non-discoverable) for the given
     * `rpId`. Powers the in-app management UI; design §6.1 / Req 3.5.
     * Ordering matches [listDiscoverableByRpId].
     */
    suspend fun listAllByRpId(rpId: String): List<Passkey>

    /**
     * Atomically increment the persisted `signCount` by one and stamp
     * `lastUsedAt` with the supplied [timestamp]. Direct expose of the
     * DAO contract (design §6.1). Silent no-op when [credentialId] does
     * not match a row (mirrors `PasskeyDao.incrementSignCount`).
     *
     * Most authentication ceremonies should prefer [signWithIncrement]
     * which wraps the increment in a `withTransaction { ... }` so the
     * counter rolls back if the signer block throws.
     */
    suspend fun incrementSignCount(credentialId: String, timestamp: Long)

    /**
     * Decrypts `(privateKeyIv, encryptedPrivateKey)` for [credentialId]
     * with the `passkey_<credentialId>` wrapping-key alias and returns
     * the **plaintext PKCS#8** private-key bytes (Issue #100 R3.1).
     *
     * Returns `null` when no row matches the credentialId. Decryption
     * failures (e.g. ciphertext tampering) propagate as exceptions —
     * the AES-GCM auth tag mismatch surfaces as
     * `javax.crypto.AEADBadTagException` per design §6.4 / NFR 2.5
     * (silent fail forbidden).
     *
     * The caller (`PasskeyAuthActivity`) wipes the returned array via
     * `ByteArray.fill(0)` immediately after use (NFR 1.1).
     *
     * @throws javax.crypto.AEADBadTagException ciphertext / IV tampering detected
     */
    suspend fun loadPrivateKey(credentialId: String): ByteArray?

    /**
     * Delete the PassKey row and its wrapping key alias. Surfaces a
     * [DeletePasskeyResult.KeystoreCleanupFailed] when the row delete
     * succeeded but the AndroidKeyStore delete raised. DB exceptions
     * (`SQLiteException`) propagate to the caller.
     */
    suspend fun delete(credentialId: String): DeletePasskeyResult

    // ---- Issue #100 (authentication ceremony) compatibility API --------
    //
    // NOTE: [signWithIncrement] is **not** part of design §6.1; it was
    // added by Issue #100 to atomize the `incrementSignCount + signer`
    // sequence under a single Room transaction so the counter rolls back
    // automatically when the signer throws or the coroutine is
    // cancelled. We keep it on the interface (rather than dropping it in
    // favour of the pure §6.1 shape) so existing callers
    // (`PasskeyAuthActivity`) continue to compile without rewrite — see
    // impl-notes "Reviewer round=2 引き継ぎ事項" for the suggestion to
    // remove this method via a follow-up Issue once the call site can
    // wrap the transaction itself.

    /**
     * Atomized variant of [incrementSignCount] (Issue #100 決定 3 /
     * design §6.1 案 C). Opens a Room transaction, bumps `signCount`,
     * passes the new value to [signer], commits on success, rolls back
     * on throw / cancellation.
     *
     * Failure-rollback in [signer] is the reason this method exists —
     * call sites would otherwise have to remember to undo the increment
     * by hand, which is hazardous (#100 review feedback).
     *
     * @param signer Receives the freshly-incremented signCount and
     *   produces an arbitrary result (typically the assertion JSON).
     * @return the value [signer] returns. Exceptions propagate verbatim.
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
