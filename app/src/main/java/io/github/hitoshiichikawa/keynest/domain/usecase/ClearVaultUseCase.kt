package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.ClearVaultFailure
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.domain.repository.DetectedFieldRepository
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import io.github.hitoshiichikawa.keynest.util.SafeLogger

/**
 * Atomic Vault clear -- empties the credential table and drops the
 * AES-GCM master key alias.
 *
 * Issue #10 Req 7.5, 7.7, 7.8, NFR 1.3, NFR 1.4.
 *
 * Processing order (see design.md "処理順の根拠"):
 *
 *   1. `repository.clearAll()` issues `DELETE FROM credentials` in a
 *      single SQLite statement (atomic).
 *   2. `detectedFieldRepository.deleteAll()` wipes the Phase 2
 *      detected_fields table. Best-effort: any failure is swallowed
 *      and logged because the credentials table — the actual sensitive
 *      data — has already been removed, and `detected_fields` only
 *      contains developer-authored fieldKey labels (no PII).
 *   3. `keystoreKeyProvider.deleteKey()` removes the `keynest_aead_v1`
 *      alias.
 *
 * The DB-first order avoids the state where ciphertext rows exist but
 * the decryption key has been dropped (which would surface to users as
 * "credentials I can't open" on the next launch).
 *
 * Failure modes:
 *
 * - Step (1) throws -> `Result.failure(ClearVaultFailure.Storage)`. The
 *   detected_fields table and the Keystore alias are NOT touched, so
 *   the Vault is unchanged.
 * - Step (1) succeeds, step (2) throws -> swallowed via SafeLogger.warn.
 *   The clear flow continues so the user's "delete everything"
 *   intent still removes the canonical sensitive store. The retry path
 *   may re-run the use case to clean up the leftover detected_fields.
 * - Step (3) throws -> `Result.failure(ClearVaultFailure.KeystoreAlias)`.
 *   The DB is empty at this point; the user's intent has already been
 *   honoured. The retry path simply re-runs the use-case (`deleteAll`
 *   on the empty table is a no-op, `deleteKey` retries).
 *
 * No plaintext is ever materialised during this flow (ciphertext rows
 * are deleted column-wise by SQLite), so NFR 1.4 is satisfied
 * structurally.
 */
class ClearVaultUseCase(
    private val repository: CredentialRepository,
    private val keystoreKeyProvider: KeystoreKeyProvider,
    private val detectedFieldRepository: DetectedFieldRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        try {
            repository.clearAll()
        } catch (t: Throwable) {
            return Result.failure(ClearVaultFailure.Storage(reason = t.javaClass.simpleName))
        }
        // Issue #67 Phase 2 (design.md §12.4): wipe detected_fields
        // alongside credentials so the "delete everything" UX
        // contract is honoured for the new Phase 2 table too. Failure
        // is non-fatal — see kdoc for rationale.
        try {
            detectedFieldRepository.deleteAll()
        } catch (t: Throwable) {
            SafeLogger.warn(
                message = "detected_fields clear failed; continuing Vault clear",
                throwable = t,
            )
        }
        try {
            keystoreKeyProvider.deleteKey()
        } catch (t: Throwable) {
            return Result.failure(ClearVaultFailure.KeystoreAlias(reason = t.javaClass.simpleName))
        }
        return Result.success(Unit)
    }
}
