package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository

/**
 * Creates a copy of an existing credential. Backs the row overflow
 * "duplicate" action. Issue #9 Req 5.3, 5.4, NFR 1.3, NFR 1.4.
 *
 * The duplicate inherits the source credential's
 *   label / username / packageName / passwordCiphertext / passwordIv /
 *   signatureSha256 / signatureCapturedAt
 * unchanged. `createdAt` and `updatedAt` are reset to `now()`; `lastUsedAt`
 * is null (a duplicate is treated as "never used" so it does NOT
 * immediately surface in the recently-used carousel).
 *
 * Critically: this use case never decrypts the password. The ciphertext +
 * IV are passed through verbatim, so plaintext never materialises during
 * the duplication (NFR 1.3 / 1.4).
 *
 * Failures are returned as [io.github.hitoshiichikawa.keynest.domain.model.DuplicateFailure]
 * via `Result.failure`. The most common failure is `NotFound` (source row
 * was deleted in a race), which the UI surfaces as a Snackbar.
 */
class DuplicateCredentialUseCase(
    private val repo: CredentialRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(sourceId: CredentialId): Result<CredentialId> =
        repo.duplicate(sourceId = sourceId, timestamp = now())
}
