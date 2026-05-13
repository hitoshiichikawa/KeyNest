package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.CredentialSortOrder
import com.example.keynest.domain.model.DuplicateFailure
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.model.VaultMetadata
import com.example.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake for [CredentialRepository] used by use-case unit tests.
 *
 * Lives in test-only scope because the production data layer goes through
 * Room (which is already exercised by [CredentialRepositoryImplTest]).
 */
internal class FakeCredentialRepository : CredentialRepository {
    private val storage = mutableMapOf<Long, EncryptedCredentialRecord>()
    private var nextId: Long = 1L

    // Backing flow so observeAll / observeBySort / observeRecentlyUsed
    // emit updated values when callers mutate via save / update / delete /
    // markUsed / duplicate.
    private val tick = MutableStateFlow(0L)

    private fun bump() {
        tick.value = tick.value + 1L
    }

    override suspend fun save(record: EncryptedCredentialRecord): CredentialId {
        val id = CredentialId(nextId++)
        storage[id.value] = record.copy(id = id)
        bump()
        return id
    }

    override suspend fun update(record: EncryptedCredentialRecord) {
        storage[record.id.value] = record
        bump()
    }

    override suspend fun delete(id: CredentialId) {
        storage.remove(id.value)
        bump()
    }

    override suspend fun findByPackage(packageName: String): List<EncryptedCredentialRecord> {
        return storage.values.filter { it.packageName == packageName }
            .sortedWith(compareByDescending<EncryptedCredentialRecord> { it.updatedAt }.thenBy { it.label })
    }

    override suspend fun findById(id: CredentialId): EncryptedCredentialRecord? = storage[id.value]

    override fun observeAll(): Flow<List<Credential>> = observeBySort(CredentialSortOrder.UpdatedAtDesc)

    override fun observeBySort(order: CredentialSortOrder): Flow<List<Credential>> =
        tick.asStateFlow().map { snapshotSorted(order) }

    override fun observeRecentlyUsed(limit: Int): Flow<List<Credential>> {
        require(limit > 0)
        return tick.asStateFlow().map {
            storage.values
                .filter { it.lastUsedAt != null }
                .sortedByDescending { it.lastUsedAt!! }
                .take(limit)
                .map { it.toDomain() }
        }
    }

    override suspend fun markUsed(id: CredentialId, timestamp: Long) {
        val existing = storage[id.value] ?: return
        storage[id.value] = existing.copy(lastUsedAt = timestamp)
        bump()
    }

    override suspend fun duplicate(
        sourceId: CredentialId,
        timestamp: Long,
    ): Result<CredentialId> {
        val source = storage[sourceId.value]
            ?: return Result.failure(DuplicateFailure.NotFound)
        val newId = CredentialId(nextId++)
        storage[newId.value] = source.copy(
            id = newId,
            createdAt = timestamp,
            updatedAt = timestamp,
            lastUsedAt = null,
        )
        bump()
        return Result.success(newId)
    }

    override fun observeMetadata(): Flow<VaultMetadata> =
        // Mirror CredentialRepositoryImpl: combine count + max(updated_at)
        // into VaultMetadata. Returns latestUpdatedAt=null on empty
        // storage so the UI path that branches on Req 4.3 is exercised.
        tick.asStateFlow().map {
            VaultMetadata(
                count = storage.size,
                latestUpdatedAt = storage.values.maxOfOrNull { it.updatedAt },
            )
        }

    override suspend fun clearAll() {
        storage.clear()
        bump()
    }

    /** Test-only: bypass-save (e.g. to pre-populate). */
    fun put(record: EncryptedCredentialRecord) {
        if (record.id.value == 0L) {
            val id = CredentialId(nextId++)
            storage[id.value] = record.copy(id = id)
        } else {
            storage[record.id.value] = record
            if (record.id.value >= nextId) nextId = record.id.value + 1
        }
        bump()
    }

    fun snapshot(): List<EncryptedCredentialRecord> = storage.values.toList()

    private fun snapshotSorted(order: CredentialSortOrder): List<Credential> {
        val comparator = when (order) {
            CredentialSortOrder.UpdatedAtDesc ->
                compareByDescending<EncryptedCredentialRecord> { it.updatedAt }.thenBy { it.label.lowercase() }
            CredentialSortOrder.LabelAsc ->
                compareBy<EncryptedCredentialRecord> { it.label.lowercase() }
                    .thenByDescending { it.updatedAt }
            CredentialSortOrder.PackageAsc ->
                compareBy<EncryptedCredentialRecord> { it.packageName.lowercase() }
                    .thenByDescending { it.updatedAt }
        }
        return storage.values.sortedWith(comparator).map { it.toDomain() }
    }

    private fun EncryptedCredentialRecord.toDomain(): Credential = Credential(
        id = id,
        packageName = packageName,
        username = username,
        label = label,
        signatureSha256 = signatureSha256,
        signatureCapturedAt = signatureCapturedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
    )
}
