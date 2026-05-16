package inc.goodanswers.keynest.domain.usecase

import inc.goodanswers.keynest.domain.model.CredentialId
import inc.goodanswers.keynest.domain.repository.CredentialRepository

/**
 * Deletes a credential by id. Requirements: 1.5.
 *
 * Always returns Result.success(Unit) on completion, including when the id
 * does not exist (delete is idempotent at the repository layer).
 */
class DeleteCredentialUseCase(
    private val repo: CredentialRepository,
) {
    suspend operator fun invoke(id: CredentialId): Result<Unit> =
        try {
            repo.delete(id)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(DeleteFailure(reason = t.javaClass.simpleName))
        }
}

data class DeleteFailure(val reason: String) : Exception("storage error: $reason")
