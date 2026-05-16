package inc.goodanswers.keynest.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import inc.goodanswers.keynest.data.entity.CredentialEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [CredentialEntity]. Requirements: 1.1, 1.4, 1.5, 2.1, 2.2,
 * 2.3 (MVP); Issue #9 Req 3.1, 3.2, 3.7, 4.1, 4.2, 4.3.
 *
 * Ordering on [observeAll] follows the OQ-3 decision (updated_at DESC, label
 * ASC as a tiebreaker) so the credential list and the AutofillService
 * candidate ordering are consistent.
 *
 * Issue #9 adds three sort-order-specific observers
 * ([observeByUpdatedAtDesc] / [observeByLabelAsc] / [observeByPackageAsc])
 * so the credential list can switch sort order without re-implementing
 * comparator logic in Kotlin, plus [observeRecentlyUsed] for the carousel
 * and [updateLastUsedAt] called from the autofill unlock flow.
 */
@Dao
interface CredentialDao {

    @Insert
    suspend fun insert(entity: CredentialEntity): Long

    @Update
    suspend fun update(entity: CredentialEntity)

    @Delete
    suspend fun delete(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM credentials WHERE package_name = :pkg ORDER BY updated_at DESC, label ASC")
    suspend fun findByPackage(pkg: String): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CredentialEntity?

    @Query("SELECT * FROM credentials ORDER BY updated_at DESC, label ASC")
    fun observeAll(): Flow<List<CredentialEntity>>

    // ---- Issue #9 ---------------------------------------------------------

    /**
     * Most-recently-updated first; label as a tiebreaker (same ordering as
     * the pre-#9 [observeAll] alias). Req 4.1(a), 4.2.
     */
    @Query("SELECT * FROM credentials ORDER BY updated_at DESC, label ASC")
    fun observeByUpdatedAtDesc(): Flow<List<CredentialEntity>>

    /**
     * Alphabetical by label (case-insensitive); `updated_at DESC` as a
     * tiebreaker so two credentials with the same label still have stable
     * ordering. Req 4.1(b).
     */
    @Query("SELECT * FROM credentials ORDER BY label COLLATE NOCASE ASC, updated_at DESC")
    fun observeByLabelAsc(): Flow<List<CredentialEntity>>

    /**
     * Alphabetical by package_name (case-insensitive); `updated_at DESC`
     * tiebreaker. Req 4.1(c).
     */
    @Query("SELECT * FROM credentials ORDER BY package_name COLLATE NOCASE ASC, updated_at DESC")
    fun observeByPackageAsc(): Flow<List<CredentialEntity>>

    /**
     * Top-N credentials by recency of autofill use. NULL `last_used_at`
     * rows are excluded so the carousel never shows a "never used"
     * credential (Req 3.1, 3.3, 3.4).
     */
    @Query(
        "SELECT * FROM credentials WHERE last_used_at IS NOT NULL " +
            "ORDER BY last_used_at DESC LIMIT :limit"
    )
    fun observeRecentlyUsed(limit: Int): Flow<List<CredentialEntity>>

    /**
     * Stamps [timestamp] onto the `last_used_at` column of the row with
     * [id]. If the row does not exist (race with delete) the UPDATE is a
     * silent no-op -- callers do not need to check existence first (Req
     * 3.2).
     */
    @Query("UPDATE credentials SET last_used_at = :timestamp WHERE id = :id")
    suspend fun updateLastUsedAt(id: Long, timestamp: Long)

    // ---- Issue #10: vault metadata + clear-all -------------------------

    /**
     * Reactive total credential count. Used by the Settings screen's
     * "Vault" section. Returns 0 when the table is empty. Issue #10 Req
     * 4.1, 4.5, 4.6.
     */
    @Query("SELECT COUNT(*) FROM credentials")
    fun observeCount(): Flow<Int>

    /**
     * Reactive max `updated_at` across all credentials. Emits `null` when
     * the table is empty (SQLite's MAX() on no rows returns NULL). The
     * Settings screen treats null as "no credentials saved yet" and
     * shows a placeholder instead of a date. Issue #10 Req 4.2, 4.3.
     */
    @Query("SELECT MAX(updated_at) FROM credentials")
    fun observeLatestUpdatedAt(): Flow<Long?>

    /**
     * Removes every row from the `credentials` table in a single SQL
     * statement. Used by the Danger Zone "Vault clear" flow (Issue #10
     * Req 7.5). The statement is atomic at the SQLite transaction
     * boundary, so callers do not need to wrap it in `runInTransaction`.
     *
     * Idempotent: deleting an already-empty table is a no-op.
     */
    @Query("DELETE FROM credentials")
    suspend fun deleteAll()
}
