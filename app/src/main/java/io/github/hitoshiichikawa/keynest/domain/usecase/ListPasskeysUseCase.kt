package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.github.hitoshiichikawa.keynest.ui.list.PasskeyDisplayModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Issue #101 (Phase 4 of umbrella #89) — Flow of all KeyNest-stored
 * PassKeys, projected into the UI-safe [PasskeyDisplayModel].
 *
 * Why a UseCase instead of letting [io.github.hitoshiichikawa.keynest.ui.list.CredentialListViewModel]
 * read the Repository directly:
 *
 *  - The Entity → DisplayModel mapping (which drops `userHandle`,
 *    `encryptedPrivateKey`, `privateKeyIv`, `keyAlias`, `signCount`)
 *    needs a single home. Pinning it here means the ViewModel never
 *    even sees a `PasskeyEntity`, so a future "log the list contents
 *    for diagnostics" line cannot accidentally leak the sensitive
 *    columns (NFR 2.1 / NFR 2.2).
 *  - Matches the existing project convention where one-method observe
 *    flows live in `domain/usecase/` (`ListCredentialsUseCase`,
 *    `ObserveRecentlyUsedUseCase`).
 *
 * The Flow is wired straight to Room's invalidation tracker via
 * [PasskeyRepository.listAll], so the credential list refreshes
 * automatically when the registration ceremony (#99) or the
 * authentication ceremony (#100) writes to the `passkeys` table.
 */
class ListPasskeysUseCase(
    private val repo: PasskeyRepository,
) {
    operator fun invoke(): Flow<List<PasskeyDisplayModel>> =
        repo.listAll().map { entities ->
            entities.map(PasskeyDisplayModel.Companion::fromEntity)
        }
}
