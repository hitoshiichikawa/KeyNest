package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.VaultMetadata
import com.example.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes [VaultMetadata] (credential count + most recent updated_at).
 *
 * Issue #10 Req 4.1, 4.2, 4.3, 4.5, 4.6. Thin wrapper around
 * [CredentialRepository.observeMetadata]; the use-case exists to (a)
 * keep ViewModels off the repository interface, mirroring the
 * ListCredentials / ObserveRecentlyUsed convention from Issue #9, and
 * (b) provide a single seam to mock from
 * [com.example.keynest.ui.settings.SettingsViewModelTest].
 */
class ObserveVaultMetadataUseCase(
    private val repository: CredentialRepository,
) {
    operator fun invoke(): Flow<VaultMetadata> = repository.observeMetadata()
}
