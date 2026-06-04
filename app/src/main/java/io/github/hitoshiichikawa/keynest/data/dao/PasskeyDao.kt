package io.github.hitoshiichikawa.keynest.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [PasskeyEntity]. Issue #91 (parent #89) — Phase 1 data layer
 * for the Credential Manager-based PassKey provider.
 *
 * All public methods are `suspend`; observer-style (`Flow<...>`) queries are
 * intentionally out of scope for Issue #91 because the management UI that
 * would consume them lives in a later sub-Issue (#89 分割案 5).
 *
 * Ordering policy (design.md §5.2):
 * `ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC` so
 * PassKeys that have been used surface first (most-recently-used → least),
 * and entries that have never been used (NULL `lastUsedAt`) fall to the tail
 * with `createdAt DESC` as a deterministic tiebreaker.
 *
 * Mutation contracts:
 * - [delete] takes the primary key (not an entity) so the registration /
 *   authentication ceremonies do not need a prior `findByCredentialId`
 *   round-trip. Rows that do not exist trigger a silent no-op (SQLite's
 *   `DELETE` behaviour on zero matches).
 * - [incrementSignCount] performs a single atomic `UPDATE`, bumping
 *   `signCount` and stamping `lastUsedAt` in one round-trip. SQLite handles
 *   the read+write as an implicit transaction so no `runInTransaction`
 *   wrapper is required. Rows that do not exist are silent no-ops.
 */
@Dao
interface PasskeyDao {

    /**
     * Insert a freshly created PassKey row. The caller is the registration
     * ceremony (#89 分割案 3) which knows the `(rpId, userHandle)` pair is
     * new (or has handled the duplicate beforehand via
     * [findByRpIdAndUserHandle]). If the UNIQUE constraint or PK is violated
     * SQLite raises `SQLiteConstraintException`, which propagates to the
     * caller.
     */
    @Insert
    suspend fun insert(entity: PasskeyEntity)

    /**
     * Overwrite an existing row in full. Primary use case is metadata
     * updates from the management UI (e.g. renaming [PasskeyEntity.displayName]).
     */
    @Update
    suspend fun update(entity: PasskeyEntity)

    /**
     * Delete the row identified by [credentialId]. Silent no-op when the row
     * is absent — callers do not need to verify existence beforehand
     * (req 3.7).
     */
    @Query("DELETE FROM passkeys WHERE credentialId = :credentialId")
    suspend fun delete(credentialId: String)

    /**
     * Look up a single PassKey by credentialId. Used by the authentication
     * ceremony on the `allowCredentials` path where the RP supplied a
     * specific credentialId.
     */
    @Query("SELECT * FROM passkeys WHERE credentialId = :credentialId LIMIT 1")
    suspend fun findByCredentialId(credentialId: String): PasskeyEntity?

    /**
     * Look up by `(rpId, userHandle)`. Returns 0 or 1 row because the
     * `(rpId, userHandle)` pair carries a UNIQUE index (Issue #91 決定 2).
     * Used during registration to detect duplicates and during certain
     * authentication paths that key off the user handle.
     */
    @Query(
        "SELECT * FROM passkeys " +
            "WHERE rpId = :rpId AND userHandle = :userHandle LIMIT 1",
    )
    suspend fun findByRpIdAndUserHandle(
        rpId: String,
        userHandle: ByteArray,
    ): PasskeyEntity?

    /**
     * List discoverable PassKeys for a given RP. Powers the "usernameless"
     * login path where Credential Manager asks for resident keys without
     * `allowCredentials`. Non-discoverable entries are filtered out by the
     * `isDiscoverable = 1` clause.
     */
    @Query(
        "SELECT * FROM passkeys WHERE rpId = :rpId AND isDiscoverable = 1 " +
            "ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC",
    )
    suspend fun listDiscoverableByRpId(rpId: String): List<PasskeyEntity>

    /**
     * List every PassKey (discoverable + non-discoverable) for the RP, for
     * the in-app management UI. Order matches [listDiscoverableByRpId] so
     * the two surfaces share a stable comparator.
     */
    @Query(
        "SELECT * FROM passkeys WHERE rpId = :rpId " +
            "ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC",
    )
    suspend fun listAllByRpId(rpId: String): List<PasskeyEntity>

    /**
     * Atomically increment `signCount` by 1 and stamp `lastUsedAt` with the
     * supplied [timestamp]. Single `UPDATE` (SQLite implicit transaction)
     * so authentication ceremonies stay on a single round-trip. Silent
     * no-op for absent rows (`WHERE credentialId = :credentialId` matches
     * zero rows → SQLite returns 0 changes without raising).
     */
    @Query(
        "UPDATE passkeys SET signCount = signCount + 1, lastUsedAt = :timestamp " +
            "WHERE credentialId = :credentialId",
    )
    suspend fun incrementSignCount(credentialId: String, timestamp: Long)

    /**
     * Observer-style list of every PassKey stored in KeyNest, ordered to match
     * the existing per-RP queries (`listAllByRpId` / `listDiscoverableByRpId`):
     * `lastUsedAt DESC` with NULL values pushed to the tail, then `createdAt
     * DESC` as a stable tiebreaker.
     *
     * Issue #101 (Phase 4 of umbrella #89) — backs the merged
     * password+PassKey RecyclerView in `CredentialListActivity`. Returning a
     * [Flow] (instead of a `suspend` snapshot) means Room's invalidation
     * tracker re-emits the list whenever the registration ceremony (#99) or
     * the authentication ceremony (#100) writes to the `passkeys` table, so
     * the credential list refreshes automatically without the Activity
     * having to re-query.
     *
     * The result includes both discoverable (`isDiscoverable = 1`) and
     * non-discoverable rows — the credential list shows every stored
     * PassKey regardless of resident-key flag (umbrella #89 確定事項 D-7).
     */
    @Query(
        "SELECT * FROM passkeys " +
            "ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC",
    )
    fun listAll(): Flow<List<PasskeyEntity>>
}
