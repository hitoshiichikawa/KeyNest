package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.util.VaultStorageMeasurer

/**
 * Returns the total on-disk byte size occupied by the credential vault.
 *
 * Issue #10 Req 4.4, 4.5, 4.6. Delegates to [VaultStorageMeasurer] which
 * performs `File.length()` on `Dispatchers.IO`; the human-readable
 * formatting (e.g. "123 kB") is left to the UI layer so the domain
 * surface stays free of Android Context dependencies.
 */
class GetVaultStorageUsageUseCase(
    private val measurer: VaultStorageMeasurer,
) {
    suspend operator fun invoke(): Long = measurer.measureBytes()
}
