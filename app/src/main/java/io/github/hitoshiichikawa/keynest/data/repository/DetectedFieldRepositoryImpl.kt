package io.github.hitoshiichikawa.keynest.data.repository

import io.github.hitoshiichikawa.keynest.data.dao.DetectedFieldDao
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldEntity
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource
import io.github.hitoshiichikawa.keynest.domain.model.DetectedField
import io.github.hitoshiichikawa.keynest.domain.repository.DetectedFieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Default [DetectedFieldRepository] backed by Room. Issue #67 Phase 2
 * (design.md §6.2).
 *
 * Maps between [DetectedFieldEntity] (storage) and [DetectedField]
 * (domain). The entity's `source` column is a free-form TEXT so the
 * forward-compatibility decode at [toDomainOrNull] silently drops rows
 * with unknown sources rather than throwing — this lets a future
 * Phase 3 source addition land without breaking Phase 2 readers.
 */
class DetectedFieldRepositoryImpl(
    private val dao: DetectedFieldDao,
) : DetectedFieldRepository {

    override suspend fun upsert(field: DetectedField) {
        dao.upsertWithLruCap(field.toEntity())
    }

    override fun observeRecentByPackage(
        packageName: String,
        limit: Int,
    ): Flow<List<DetectedField>> =
        dao.observeRecentByPackage(packageName, limit)
            .map { list -> list.mapNotNull { it.toDomainOrNull() } }

    override suspend fun deleteByPackage(packageName: String) {
        dao.deleteByPackage(packageName)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

// ---- mapping helpers (file-private) ----------------------------------------

private fun DetectedField.toEntity(): DetectedFieldEntity =
    DetectedFieldEntity(
        packageName = packageName,
        fieldKey = fieldKey,
        source = source.storageKey,
        lastDetectedAt = lastDetectedAt,
    )

private fun DetectedFieldEntity.toDomainOrNull(): DetectedField? {
    val src = DetectedFieldSource.fromStorageKey(source) ?: return null
    return DetectedField(
        packageName = packageName,
        fieldKey = fieldKey,
        source = src,
        lastDetectedAt = lastDetectedAt,
    )
}
