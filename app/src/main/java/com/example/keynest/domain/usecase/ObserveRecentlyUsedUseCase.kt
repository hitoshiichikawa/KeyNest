package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes the top-N most-recently-used credentials. Issue #9 Req 3.1,
 * 3.3, 3.6, 3.7.
 *
 * The carousel feed is intentionally independent of the search / filter
 * state held by the list ViewModel (Req 3.6) -- callers should keep this
 * flow on its own combine input rather than weaving it into the filtered
 * list pipeline.
 *
 * Limit is hard-coded to 5 (Req 3.1) and is not configurable; per
 * requirements "Out of Scope", there is no user-facing setting for this.
 */
class ObserveRecentlyUsedUseCase(
    private val repo: CredentialRepository,
) {
    operator fun invoke(): Flow<List<Credential>> = repo.observeRecentlyUsed(limit = LIMIT)

    private companion object {
        const val LIMIT = 5
    }
}
