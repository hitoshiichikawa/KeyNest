package com.example.keynest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.keynest.data.dao.CredentialDao
import com.example.keynest.data.entity.CredentialEntity

/**
 * Room database holding all KeyNest persistent state.
 *
 * Requirements: 1.1
 *
 * Migration policy: version 1 is the starting schema; any future schema
 * change MUST ship with an explicit Migration. `fallbackToDestructiveMigration`
 * is NOT enabled because losing credentials silently would be a worse UX
 * than a startup crash that prompts re-installation.
 */
@Database(
    entities = [CredentialEntity::class],
    version = 1,
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
            ).build()
        }
    }
}
