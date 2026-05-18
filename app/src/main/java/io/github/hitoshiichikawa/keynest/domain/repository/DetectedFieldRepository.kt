package io.github.hitoshiichikawa.keynest.domain.repository

import io.github.hitoshiichikawa.keynest.domain.model.DetectedField
import kotlinx.coroutines.flow.Flow

/**
 * Domain-side abstraction over the `detected_fields` table. Issue #67
 * Phase 2 (design.md §6.1).
 *
 * Implementation lives at
 * [io.github.hitoshiichikawa.keynest.data.repository.DetectedFieldRepositoryImpl];
 * the domain layer never sees [io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldEntity].
 *
 * Concurrency: all suspend methods are safe to call from any dispatcher.
 * Room delegates to its own background executor internally.
 */
interface DetectedFieldRepository {

    /**
     * Records [field] in the table. If the composite PK
     * `(packageName, fieldKey, source)` already exists, the row's
     * `lastDetectedAt` is bumped to [field]'s value (and the existing
     * entry slides to the head of the LRU). Triggers per-package LRU
     * pruning via
     * [io.github.hitoshiichikawa.keynest.data.dao.DetectedFieldDao.upsertWithLruCap].
     *
     * Fire-and-forget: the autofill service calls this from a detached
     * coroutine in [io.github.hitoshiichikawa.keynest.autofill.KeyNestAutofillService.onFillRequest]
     * so the upsert does NOT contribute to the autofill response latency.
     */
    suspend fun upsert(field: DetectedField)

    /**
     * Reactive view of the [limit] most recently detected fields for
     * [packageName], ordered by `lastDetectedAt DESC`. Used by the
     * credential edit screen suggestion chips.
     *
     * Rows whose `source` column does not decode to a known
     * [io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource]
     * (forward-compat with a future Phase 3) are silently skipped at the
     * mapping layer.
     */
    fun observeRecentByPackage(packageName: String, limit: Int): Flow<List<DetectedField>>

    /**
     * Removes every row whose `packageName` matches. Provided for
     * symmetry / future use; not currently exercised by production code.
     */
    suspend fun deleteByPackage(packageName: String)

    /**
     * Removes every row in the table. Called from the Vault clear flow
     * so detected_fields is wiped alongside credentials (Issue #10
     * Danger Zone integration, design.md §12.4).
     */
    suspend fun deleteAll()
}
