package io.github.hitoshiichikawa.keynest.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [DetectedFieldEntity]. Issue #67 Phase 2 (design.md §5).
 *
 * Two surface areas:
 *   - Writes: [upsertWithLruCap] is the canonical entry point — it upserts
 *     a row AND prunes the LRU tail to keep at most [LRU_CAPACITY] rows
 *     per packageName in a single transaction.
 *   - Reads: [observeRecentByPackage] feeds the "recently detected fields"
 *     suggestion chip group on the credential edit screen.
 *
 * The clear-by-package and clear-all helpers exist so the Vault clear
 * flow can wipe detected_fields atomically with the credentials it
 * relates to (design.md §12.4 / T14).
 */
@Dao
interface DetectedFieldDao {

    /**
     * REPLACE-on-conflict insert backing [upsertWithLruCap]. Not intended
     * to be called directly by repositories — LRU capacity invariants are
     * only maintained when the upsert + count + delete sequence runs as a
     * single transaction.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceInternal(entity: DetectedFieldEntity)

    /**
     * Returns the number of rows for [pkg]. Used inside
     * [upsertWithLruCap] to decide whether the LRU tail must be pruned.
     */
    @Query("SELECT COUNT(*) FROM detected_fields WHERE package_name = :pkg")
    suspend fun countByPackage(pkg: String): Int

    /**
     * Deletes the [count] oldest rows for [pkg] (smallest
     * `last_detected_at`). Tie-breaking by `rowid` keeps the deletion
     * stable but is otherwise irrelevant — same-ms collisions are rare
     * and benign (requirements §10 R3).
     */
    @Query(
        """
        DELETE FROM detected_fields
        WHERE rowid IN (
            SELECT rowid FROM detected_fields
            WHERE package_name = :pkg
            ORDER BY last_detected_at ASC, rowid ASC
            LIMIT :count
        )
        """,
    )
    suspend fun deleteOldestByPackage(pkg: String, count: Int)

    /**
     * Reactive view of the most-recent [limit] rows for [pkg], ordered by
     * `last_detected_at DESC`. Backs the suggestion chip group emitted by
     * [io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentDetectedFieldsUseCase].
     */
    @Query(
        """
        SELECT * FROM detected_fields
        WHERE package_name = :pkg
        ORDER BY last_detected_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecentByPackage(pkg: String, limit: Int): Flow<List<DetectedFieldEntity>>

    /**
     * Atomic upsert that also maintains the per-package LRU cap.
     *
     * Sequence:
     *   1. [insertOrReplaceInternal] writes [entity] (updates
     *      `last_detected_at` if the composite PK already exists).
     *   2. [countByPackage] returns the post-write row count for the
     *      [entity]'s packageName.
     *   3. If count > [capacity], [deleteOldestByPackage] drops the
     *      excess from the LRU tail.
     *
     * Room wraps the function body in a single SQLite transaction
     * because of the [Transaction] annotation, which (combined with WAL
     * mode) keeps concurrent FillRequests serializable.
     */
    @Transaction
    suspend fun upsertWithLruCap(entity: DetectedFieldEntity, capacity: Int = LRU_CAPACITY) {
        insertOrReplaceInternal(entity)
        val count = countByPackage(entity.packageName)
        val excess = count - capacity
        if (excess > 0) {
            deleteOldestByPackage(entity.packageName, excess)
        }
    }

    /**
     * Removes every row for [pkg]. Provided for symmetry / future use
     * (e.g. credential delete cascade); not currently called from
     * production code in Phase 2.
     */
    @Query("DELETE FROM detected_fields WHERE package_name = :pkg")
    suspend fun deleteByPackage(pkg: String)

    /**
     * Removes every row in the table. Called from
     * [io.github.hitoshiichikawa.keynest.domain.usecase.ClearVaultUseCase]
     * so detected_fields is wiped along with credentials during a Danger
     * Zone "Vault clear" (design.md §12.4 / T14).
     */
    @Query("DELETE FROM detected_fields")
    suspend fun deleteAll()

    companion object {
        /**
         * Per-package row cap. Bounds the table size at
         * `LRU_CAPACITY * #packages` and keeps the suggestion UI query
         * cheap (requirements §4 Q3).
         */
        const val LRU_CAPACITY: Int = 50
    }
}
