package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes the credential list. Requirements: 1.5.
 *
 * Returns the lightweight [Credential] form (without ciphertext) so list
 * UIs cannot accidentally surface encrypted bytes - structurally enforced
 * by [CredentialRepository.observeAll].
 */
class ListCredentialsUseCase(
    private val repo: CredentialRepository,
) {
    operator fun invoke(): Flow<List<Credential>> = repo.observeAll()
}
