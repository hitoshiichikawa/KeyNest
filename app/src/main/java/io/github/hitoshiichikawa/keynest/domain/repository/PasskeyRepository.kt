package io.github.hitoshiichikawa.keynest.domain.repository

import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest

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
}
