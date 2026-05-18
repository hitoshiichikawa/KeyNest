package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes the credential list. Requirements: 1.5 (MVP); Issue #9 Req
 * 4.1, 4.2, 4.3 (sort-order parameter).
 *
 * Returns the lightweight [Credential] form (without ciphertext) so list
 * UIs cannot accidentally surface encrypted bytes - structurally enforced
 * by [CredentialRepository.observeBySort].
 *
 * The default [CredentialSortOrder.UpdatedAtDesc] preserves the pre-#9
 * behaviour (most-recently-updated first) for any caller that does not
 * supply a sort order.
 */
class ListCredentialsUseCase(
    private val repo: CredentialRepository,
) {
    operator fun invoke(
        order: CredentialSortOrder = CredentialSortOrder.UpdatedAtDesc,
    ): Flow<List<Credential>> = repo.observeBySort(order)
}
