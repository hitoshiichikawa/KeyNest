package io.github.hitoshiichikawa.keynest.data.repository

import io.github.hitoshiichikawa.keynest.data.dao.CredentialDao
import io.github.hitoshiichikawa.keynest.data.entity.CredentialEntity
import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder
import io.github.hitoshiichikawa.keynest.domain.model.DuplicateFailure
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import io.github.hitoshiichikawa.keynest.domain.model.VaultMetadata
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    override fun observeBySort(order: CredentialSortOrder): Flow<List<Credential>> {
        val source = when (order) {
            CredentialSortOrder.UpdatedAtDesc -> dao.observeByUpdatedAtDesc()
            CredentialSortOrder.LabelAsc -> dao.observeByLabelAsc()
            CredentialSortOrder.PackageAsc -> dao.observeByPackageAsc()
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    override fun observeRecentlyUsed(limit: Int): Flow<List<Credential>> {
        require(limit > 0) { "limit must be positive" }
        return dao.observeRecentlyUsed(limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun markUsed(id: CredentialId, timestamp: Long) {
        dao.updateLastUsedAt(id = id.value, timestamp = timestamp)
    }

    override fun observeMetadata(): Flow<VaultMetadata> =
        // Issue #10 Req 4.1 / 4.2 / 4.3: combine COUNT and MAX(updated_at)
        // into a single aggregate. SQLite's MAX() over an empty table
        // returns NULL, which we propagate so the UI can render the
        // "未登録" placeholder instead of an unsafe timestamp.
        combine(
            dao.observeCount(),
            dao.observeLatestUpdatedAt(),
        ) { count, latest ->
            VaultMetadata(count = count, latestUpdatedAt = latest)
        }

    override suspend fun clearAll() {
        // Issue #10 Req 7.5 / 7.8: single SQL DELETE so the operation is
        // atomic at the SQLite transaction boundary. The DAO performs
        // no decryption, so no credential plaintext is touched here
        // (NFR 1.4).
        dao.deleteAll()
    }

    override suspend fun duplicate(
        sourceId: CredentialId,
        timestamp: Long,
    ): Result<CredentialId> {
        val source = dao.findById(sourceId.value)
            ?: return Result.failure(DuplicateFailure.NotFound)
        return try {
            // Inherit ciphertext / IV / signature unchanged: this avoids
            // decrypt -> re-encrypt and so never materialises plaintext
            // during a duplicate (NFR 1.3 / 1.4).
            val copy = source.copy(
                id = 0L,            // autoGenerate
                createdAt = timestamp,
                updatedAt = timestamp,
                lastUsedAt = null,  // duplicate is "fresh / never used"
            )
            val newId = dao.insert(copy)
            Result.success(CredentialId(newId))
        } catch (t: Throwable) {
            Result.failure(DuplicateFailure.Storage(reason = t.javaClass.simpleName))
        }
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
            lastUsedAt = lastUsedAt,
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
            lastUsedAt = lastUsedAt,
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
        lastUsedAt = lastUsedAt,
    )
}
