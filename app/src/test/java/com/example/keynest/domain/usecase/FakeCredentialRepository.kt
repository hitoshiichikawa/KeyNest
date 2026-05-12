package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory fake for [CredentialRepository] used by use-case unit tests.
 *
 * Lives in test-only scope because the production data layer goes through
 * Room (which is already exercised by [CredentialRepositoryImplTest]).
 */
internal class FakeCredentialRepository : CredentialRepository {
    private val storage = mutableMapOf<Long, EncryptedCredentialRecord>()
    private var nextId: Long = 1L

    override suspend fun save(record: EncryptedCredentialRecord): CredentialId {
        val id = CredentialId(nextId++)
        storage[id.value] = record.copy(id = id)
        return id
    }

    override suspend fun update(record: EncryptedCredentialRecord) {
        storage[record.id.value] = record
    }

    override suspend fun delete(id: CredentialId) {
        storage.remove(id.value)
    }

    override suspend fun findByPackage(packageName: String): List<EncryptedCredentialRecord> {
        return storage.values.filter { it.packageName == packageName }
            .sortedWith(compareByDescending<EncryptedCredentialRecord> { it.updatedAt }.thenBy { it.label })
    }

    override suspend fun findById(id: CredentialId): EncryptedCredentialRecord? = storage[id.value]

    override fun observeAll(): Flow<List<Credential>> = flowOf(
        storage.values
            .sortedWith(compareByDescending<EncryptedCredentialRecord> { it.updatedAt }.thenBy { it.label })
            .map {
                Credential(
                    id = it.id,
                    packageName = it.packageName,
                    username = it.username,
                    label = it.label,
                    signatureSha256 = it.signatureSha256,
                    signatureCapturedAt = it.signatureCapturedAt,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
    )

    /** Test-only: bypass-save (e.g. to pre-populate). */
    fun put(record: EncryptedCredentialRecord) {
        if (record.id.value == 0L) {
            val id = CredentialId(nextId++)
            storage[id.value] = record.copy(id = id)
        } else {
            storage[record.id.value] = record
            if (record.id.value >= nextId) nextId = record.id.value + 1
        }
    }

    fun snapshot(): List<EncryptedCredentialRecord> = storage.values.toList()
}
