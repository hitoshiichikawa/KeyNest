package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.repository.CredentialRepository

/**
 * Stamps the current time onto a credential's `lastUsedAt`. Called from
 * the autofill unlock flow once decryption succeeds and a Dataset has
 * been produced for the framework. Issue #9 Req 3.2.
 *
 * Failure model: storage failures are wrapped in a [MarkUsedFailure] so
 * the caller can decide whether to log + ignore (the autofill happy path)
 * or surface to the user. The failure object's reason is the exception
 * class name only (NFR 1.3 -- no plaintext / metadata leakage).
 */
class MarkCredentialUsedUseCase(
    private val repo: CredentialRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(id: CredentialId): Result<Unit> = try {
        repo.markUsed(id, timestamp = now())
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(MarkUsedFailure.Storage(reason = t.javaClass.simpleName))
    }
}

/** Failure surface for [MarkCredentialUsedUseCase]. */
sealed class MarkUsedFailure(message: String) : Exception(message) {
    data class Storage(val reason: String) : MarkUsedFailure("storage error: $reason")
}
