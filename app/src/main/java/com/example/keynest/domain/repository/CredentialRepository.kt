package com.example.keynest.domain.repository

import com.example.keynest.domain.model.Credential
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.CredentialSortOrder
import com.example.keynest.domain.model.DuplicateFailure
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.model.VaultMetadata
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
 * Requirements: 1.1, 1.4, 1.5, 2.1, 2.2, 2.3 (MVP); Issue #9 Req 3.1, 3.2,
 * 4.1, 4.3, 5.3, 5.4, NFR 1.4 (sort variants / recently-used / markUsed /
 * duplicate).
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
     *
     * Equivalent to [observeBySort] with [CredentialSortOrder.UpdatedAtDesc];
     * preserved for source compatibility with pre-#9 callers.
     */
    fun observeAll(): Flow<List<Credential>>

    /**
     * Observes the credential set sorted by [order]. Issue #9 Req 4.1, 4.3.
     */
    fun observeBySort(order: CredentialSortOrder): Flow<List<Credential>>

    /**
     * Observes the most-recently-used credentials (top [limit] rows by
     * `lastUsedAt DESC`). Never-used credentials (NULL `lastUsedAt`) are
     * excluded. Issue #9 Req 3.1, 3.3, 3.4.
     */
    fun observeRecentlyUsed(limit: Int): Flow<List<Credential>>

    /**
     * Stamps [timestamp] onto the `lastUsedAt` of the credential with [id].
     * Silent no-op if the row was deleted in a race. Issue #9 Req 3.2.
     */
    suspend fun markUsed(id: CredentialId, timestamp: Long)

    /**
     * Creates a copy of the credential at [sourceId]. The new row inherits
     * label / username / packageName / passwordCiphertext / passwordIv /
     * signatureSha256 / signatureCapturedAt unchanged (avoids a
     * decrypt -> re-encrypt cycle on plaintext per NFR 1.3 / 1.4). The
     * timestamps are reset to [timestamp] and `lastUsedAt` is null.
     *
     * Issue #9 Req 5.3, 5.4.
     */
    suspend fun duplicate(sourceId: CredentialId, timestamp: Long): Result<CredentialId>

    /**
     * Reactive aggregate metadata about the saved credential set. Emits a
     * fresh [VaultMetadata] whenever credentials are inserted / updated /
     * deleted. The returned values are aggregate-only (count, max
     * updated_at) and never carry individual credential fields, so they
     * are safe to expose on the Settings screen (NFR 1.2).
     *
     * Issue #10 Req 4.1, 4.2, 4.3, 4.5, 4.6.
     */
    fun observeMetadata(): Flow<VaultMetadata>

    /**
     * Removes every saved credential from persistent storage. Used by the
     * Danger Zone "Vault clear" flow. Implementations must delete the
     * rows atomically (single SQL DELETE) and must not decrypt or
     * otherwise materialise any credential plaintext during the call
     * (Issue #10 Req 7.5, 7.8, NFR 1.4).
     *
     * Idempotent: clearing an already-empty vault is a no-op.
     */
    suspend fun clearAll()
}
