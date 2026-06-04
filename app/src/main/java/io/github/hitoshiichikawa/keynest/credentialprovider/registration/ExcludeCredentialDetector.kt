package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Synchronous wrapper over [PasskeyRepository.findByCredentialId] used by
 * [io.github.hitoshiichikawa.keynest.credentialprovider.KeyNestCredentialProviderService.onBeginCreateCredentialRequest]
 * to answer `excludeCredentials` hits before the OS sheet appears
 * (Issue #99 / parent #89 / design §4.1.1 / §5.2).
 *
 * `runBlocking(Dispatchers.IO)` is used here because the Credential
 * Manager `OutcomeReceiver` contract is fundamentally synchronous from
 * the Service callback's perspective (NFR 5.3). `findByCredentialId` is
 * a SQLite point lookup taking < 1ms; `excludeCredentials` is typically
 * 0–few entries (W3C WebAuthn §5.4 usage) so the worst-case ANR
 * pressure is well under the 5-second window.
 */
internal class ExcludeCredentialDetector(
    private val repository: PasskeyRepository,
) {
    fun containsAny(credentialIds: List<String>): Boolean {
        if (credentialIds.isEmpty()) return false
        return runBlocking(Dispatchers.IO) {
            credentialIds.any { id -> repository.findByCredentialId(id) != null }
        }
    }
}
