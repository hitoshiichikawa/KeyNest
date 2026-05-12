package com.example.keynest.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.keynest.data.entity.CredentialEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [CredentialEntity]. Requirements: 1.1, 1.4, 1.5, 2.1, 2.2,
 * 2.3.
 *
 * Ordering on [observeAll] follows the OQ-3 decision (updated_at DESC, label
 * ASC as a tiebreaker) so the credential list and the AutofillService
 * candidate ordering are consistent.
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
}
