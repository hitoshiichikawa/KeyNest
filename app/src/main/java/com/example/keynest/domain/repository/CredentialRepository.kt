package com.example.keynest.domain.repository

import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import kotlinx.coroutines.flow.Flow

/**
 * Domain-side abstraction over credential persistence.
 *
 * The implementation lives in the data layer
 * ([com.example.keynest.data.repository.CredentialRepositoryImpl]) and maps
 * Room entities to / from [EncryptedCredentialRecord]. The domain layer
 * intentionally never sees plaintext passwords: encryption / decryption is
 * the cipher's responsibility.
 *
 * Requirements: 1.1, 1.4, 1.5, 2.1, 2.2, 2.3
 */
interface CredentialRepository {

    /** Inserts a new credential. Returns the newly assigned [CredentialId]. */
    suspend fun save(record: EncryptedCredentialRecord): CredentialId

    /** Updates an existing credential identified by [EncryptedCredentialRecord.id]. */
    suspend fun update(record: EncryptedCredentialRecord)

    /** Deletes the credential with [id], if present. */
    suspend fun delete(id: CredentialId)

    /** Returns all credentials matching [packageName] (may be empty). */
    suspend fun findByPackage(packageName: String): List<EncryptedCredentialRecord>

    /** Returns the credential matching [id], or null. */
    suspend fun findById(id: CredentialId): EncryptedCredentialRecord?

    /**
     * Observes the entire credential set. The emitted [Credential] objects
     * intentionally omit ciphertext / IV because callers only need
     * non-sensitive metadata to render the list (NFR 1.3).
     */
    fun observeAll(): Flow<List<Credential>>
}
