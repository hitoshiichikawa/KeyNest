package com.example.keynest.data.repository

import com.example.keynest.data.dao.CredentialDao
import com.example.keynest.data.entity.CredentialEntity
import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.model.SigningHash
import com.example.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Default [CredentialRepository] implementation backed by Room.
 *
 * Maps between [CredentialEntity] (storage) and the domain types
 * [EncryptedCredentialRecord] / [Credential]. The domain layer never sees
 * `passwordCiphertext` directly except via [EncryptedCredentialRecord], which
 * is the input/output type for save / update / find* methods.
 *
 * The lighter-weight [Credential] aggregate is emitted by [observeAll] so
 * that the credential list UI does not pull encrypted bytes into Activity
 * scope unnecessarily (NFR 1.3).
 */
class CredentialRepositoryImpl(
    private val dao: CredentialDao,
) : CredentialRepository {

    override suspend fun save(record: EncryptedCredentialRecord): CredentialId {
        val id = dao.insert(record.toEntity(idOverride = 0L))
        return CredentialId(id)
    }

    override suspend fun update(record: EncryptedCredentialRecord) {
        dao.update(record.toEntity())
    }

    override suspend fun delete(id: CredentialId) {
        dao.deleteById(id.value)
    }

    override suspend fun findByPackage(packageName: String): List<EncryptedCredentialRecord> {
        return dao.findByPackage(packageName).map { it.toEncryptedRecord() }
    }

    override suspend fun findById(id: CredentialId): EncryptedCredentialRecord? {
        return dao.findById(id.value)?.toEncryptedRecord()
    }

    override fun observeAll(): Flow<List<Credential>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    // ---- mapping helpers --------------------------------------------------

    private fun EncryptedCredentialRecord.toEntity(idOverride: Long? = null): CredentialEntity =
        CredentialEntity(
            id = idOverride ?: id.value,
            packageName = packageName,
            username = username,
            label = label,
            passwordCiphertext = passwordCiphertext,
            passwordIv = passwordIv,
            signatureSha256 = signatureSha256?.value,
            signatureCapturedAt = signatureCapturedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun CredentialEntity.toEncryptedRecord(): EncryptedCredentialRecord =
        EncryptedCredentialRecord(
            id = CredentialId(id),
            packageName = packageName,
            username = username,
            label = label,
            passwordCiphertext = passwordCiphertext,
            passwordIv = passwordIv,
            signatureSha256 = signatureSha256?.let { SigningHash(it) },
            signatureCapturedAt = signatureCapturedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun CredentialEntity.toDomain(): Credential = Credential(
        id = CredentialId(id),
        packageName = packageName,
        username = username,
        label = label,
        signatureSha256 = signatureSha256?.let { SigningHash(it) },
        signatureCapturedAt = signatureCapturedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
