package inc.goodanswers.keynest.domain.usecase

import inc.goodanswers.keynest.domain.model.ClearVaultFailure
import inc.goodanswers.keynest.domain.repository.CredentialRepository
import inc.goodanswers.keynest.security.KeystoreKeyProvider

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
 *   2. `keystoreKeyProvider.deleteKey()` removes the `keynest_aead_v1`
 *      alias.
 *
 * The DB-first order avoids the state where ciphertext rows exist but
 * the decryption key has been dropped (which would surface to users as
 * "credentials I can't open" on the next launch).
 *
 * Failure modes:
 *
 * - Step (1) throws -> `Result.failure(ClearVaultFailure.Storage)`. The
 *   Keystore alias is NOT touched, so the Vault is unchanged.
 * - Step (1) succeeds, step (2) throws ->
 *   `Result.failure(ClearVaultFailure.KeystoreAlias)`. The DB is empty
 *   at this point; the user's "delete everything" intent has already
 *   been honoured. The retry path simply re-runs the use-case
 *   (`deleteAll` on the empty table is a no-op, `deleteKey` retries).
 *
 * No plaintext is ever materialised during this flow (ciphertext rows
 * are deleted column-wise by SQLite), so NFR 1.4 is satisfied
 * structurally.
 */
class ClearVaultUseCase(
    private val repository: CredentialRepository,
    private val keystoreKeyProvider: KeystoreKeyProvider,
) {
    suspend operator fun invoke(): Result<Unit> {
        try {
            repository.clearAll()
        } catch (t: Throwable) {
            return Result.failure(ClearVaultFailure.Storage(reason = t.javaClass.simpleName))
        }
        try {
            keystoreKeyProvider.deleteKey()
        } catch (t: Throwable) {
            return Result.failure(ClearVaultFailure.KeystoreAlias(reason = t.javaClass.simpleName))
        }
        return Result.success(Unit)
    }
}
