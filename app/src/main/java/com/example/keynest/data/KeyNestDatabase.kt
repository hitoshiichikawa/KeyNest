package com.example.keynest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.keynest.data.dao.CredentialDao
import com.example.keynest.data.entity.CredentialEntity
import com.example.keynest.data.migration.Migration_1_2

/**
 * Room database holding all KeyNest persistent state.
 *
 * Requirements: 1.1 (MVP); Issue #9 Req 3.1, 3.2, 3.4 (schema v2 adds
 * `last_used_at`).
 *
 * Migration policy: any schema change MUST ship with an explicit Migration.
 * `fallbackToDestructiveMigration` is NOT enabled because losing credentials
 * silently would be a worse UX than a startup crash that prompts
 * re-installation.
 *
 * Schema history:
 *   v1 -> v2 ([Migration_1_2]): adds `last_used_at INTEGER NULL`.
 */
@Database(
    entities = [CredentialEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class KeyNestDatabase : RoomDatabase() {

    abstract fun credentialDao(): CredentialDao

    companion object {
        const val DB_NAME = "keynest.db"

        fun create(context: Context): KeyNestDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                KeyNestDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(Migration_1_2)
                .build()
        }
    }
}
