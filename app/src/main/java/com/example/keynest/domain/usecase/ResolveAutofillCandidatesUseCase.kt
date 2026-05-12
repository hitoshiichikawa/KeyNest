package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.repository.CredentialRepository
import com.example.keynest.util.PackageSignatureResolver

/**
 * Returns Autofill candidates for the caller package.
 *
 * Requirements: 3.1, 3.5, 4.1, 4.2, 4.3, 4.4, NFR 2.2, NFR 3.2
 *
 * Critical constraints:
 * - DOES NOT decrypt passwords. The Autofill response in the lock state
 *   contains only metadata + Authentication IntentSender. Decryption is
 *   delegated to [UnlockVaultUseCase], which is only invoked AFTER the user
 *   completes biometric/device auth (Req 5.1 / 5.3).
 * - Signature mismatch / null signature credentials are filtered out
 *   (Req 4.2 / 4.3).
 * - On exception the candidate list is empty (NFR 3.2).
 */
class ResolveAutofillCandidatesUseCase(
    private val repo: CredentialRepository,
    private val sigResolver: PackageSignatureResolver,
) {

    suspend operator fun invoke(callerPackage: String): List<AutofillCandidate> {
        if (callerPackage.isBlank()) return emptyList()

        val callerHash = sigResolver.resolveSha256(callerPackage)
            ?: return emptyList()  // If we cannot resolve current signature, we never match (Req 4.3 spirit).

        return try {
            repo.findByPackage(callerPackage)
                .asSequence()
                // Req 4.3: drop credentials saved without a signing hash.
                .filter { it.signatureSha256 != null }
                // Req 4.2: drop credentials whose stored hash differs from the caller's current hash.
                .filter { it.signatureSha256 == callerHash }
                .map {
                    AutofillCandidate(
                        id = it.id,
                        label = it.label,
                        username = it.username,
                        packageName = it.packageName,
                    )
                }
                .toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

/**
 * Projection used by the Autofill response builder. Notably omits ciphertext
 * and IV - the caller cannot accidentally surface an encrypted payload at
 * this stage, and there is no plaintext at any point on this path
 * (NFR 1.4 / NFR 2.2).
 */
data class AutofillCandidate(
    val id: CredentialId,
    val label: String,
    val username: String,
    val packageName: String,
)
