package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.DetectedField
import io.github.hitoshiichikawa.keynest.domain.repository.DetectedFieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake for [DetectedFieldRepository] used by use-case and
 * ViewModel unit tests. Mirrors [FakeCredentialRepository]'s style — a
 * single `tick` StateFlow drives the observe* paths so test code can
 * upsert and assert in the same coroutine without juggling shared
 * subjects.
 *
 * Per-package LRU cap is intentionally NOT enforced here; the production
 * cap lives in
 * [io.github.hitoshiichikawa.keynest.data.dao.DetectedFieldDao.upsertWithLruCap]
 * and is exercised by [io.github.hitoshiichikawa.keynest.data.DetectedFieldDaoTest].
 * Tests that need the cap behaviour should use the real DAO.
 */
internal class FakeDetectedFieldRepository : DetectedFieldRepository {

    private val storage = mutableListOf<DetectedField>()
    private val tick = MutableStateFlow(0L)

    val upsertCalls: List<DetectedField> get() = storage.toList()

    private fun bump() {
        tick.value = tick.value + 1L
    }

    override suspend fun upsert(field: DetectedField) {
        // Match the DAO's REPLACE semantics on the composite PK: if a
        // row with the same (packageName, fieldKey, source) already
        // exists, update its lastDetectedAt in place.
        val existingIndex = storage.indexOfFirst {
            it.packageName == field.packageName &&
                it.fieldKey == field.fieldKey &&
                it.source == field.source
        }
        if (existingIndex >= 0) {
            storage[existingIndex] = field
        } else {
            storage += field
        }
        bump()
    }

    override fun observeRecentByPackage(
        packageName: String,
        limit: Int,
    ): Flow<List<DetectedField>> {
        require(limit > 0)
        return tick.asStateFlow().map {
            storage.filter { it.packageName == packageName }
                .sortedByDescending { it.lastDetectedAt }
                .take(limit)
        }
    }

    override suspend fun deleteByPackage(packageName: String) {
        storage.removeAll { it.packageName == packageName }
        bump()
    }

    override suspend fun deleteAll() {
        storage.clear()
        bump()
    }

    /** Test-only: bypass `upsert` (e.g. to pre-populate). */
    fun put(field: DetectedField) {
        storage += field
        bump()
    }
}
