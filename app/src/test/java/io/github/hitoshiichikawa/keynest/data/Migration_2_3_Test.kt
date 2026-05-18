package io.github.hitoshiichikawa.keynest.data

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.migration.Migration_2_3
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies the [Migration_2_3] migration. Issue #66 Phase 1 requirements
 * 2.1, 2.2 and 6.1.
 *
 * Same isolation pattern as Migration_1_2_Test — the migration is driven
 * directly against a SupportSQLiteDatabase pre-populated with the v2
 * schema (no `custom_fields_*` columns). The schema JSON files generated
 * by KSP are not required and Room's identity-hash validation is bypassed
 * to keep the test self-contained.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class Migration_2_3_Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "migration_2_3_test_${System.nanoTime()}.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        val file: File = context.getDatabasePath(dbName)
        if (file.exists()) file.delete()
        listOf("-journal", "-shm", "-wal").forEach { ext ->
            val side = File(file.absolutePath + ext)
            if (side.exists()) side.delete()
        }
    }

    @Test
    fun migrate_preservesExistingRow_andLeavesCustomFieldsBlobEmpty() {
        // Arrange: v2 schema + one sample row.
        val db = openV2Database()
        insertV2Row(
            db,
            packageName = "com.example.target",
            username = "alice",
            label = "Example",
            createdAt = 100L,
            updatedAt = 200L,
            lastUsedAt = 300L,
        )

        // Act
        Migration_2_3.migrate(db)

        // Assert: existing columns preserved, new BLOB columns are length 0.
        val cursor = db.query(
            "SELECT id, package_name, username, label, created_at, updated_at, " +
                "last_used_at, custom_fields_ciphertext, custom_fields_iv FROM credentials",
        )
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(it.getColumnIndexOrThrow("package_name")))
                .isEqualTo("com.example.target")
            assertThat(it.getString(it.getColumnIndexOrThrow("username"))).isEqualTo("alice")
            assertThat(it.getString(it.getColumnIndexOrThrow("label"))).isEqualTo("Example")
            assertThat(it.getLong(it.getColumnIndexOrThrow("created_at"))).isEqualTo(100L)
            assertThat(it.getLong(it.getColumnIndexOrThrow("updated_at"))).isEqualTo(200L)
            assertThat(it.getLong(it.getColumnIndexOrThrow("last_used_at"))).isEqualTo(300L)
            val cfCipher = it.getBlob(it.getColumnIndexOrThrow("custom_fields_ciphertext"))
            val cfIv = it.getBlob(it.getColumnIndexOrThrow("custom_fields_iv"))
            assertThat(cfCipher).hasLength(0)
            assertThat(cfIv).hasLength(0)
            assertThat(it.moveToNext()).isFalse()
        }
    }

    @Test
    fun migrate_addsColumns_andAllowsSubsequentWrites() {
        // Arrange: empty v2 database.
        val db = openV2Database()

        // Act
        Migration_2_3.migrate(db)

        // Assert: we can insert a row that writes the new columns.
        val cfCiphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val cfIv = ByteArray(12) { 0x20.toByte() }
        val values = ContentValues().apply {
            put("package_name", "com.example.other")
            put("username", "bob")
            put("label", "Other")
            put("password_ciphertext", byteArrayOf(1, 2, 3))
            put("password_iv", ByteArray(12) { 0x10.toByte() })
            putNull("signature_sha256")
            putNull("signature_captured_at")
            put("created_at", 0L)
            put("updated_at", 0L)
            putNull("last_used_at")
            put("custom_fields_ciphertext", cfCiphertext)
            put("custom_fields_iv", cfIv)
        }
        val rowId = db.insert(
            "credentials",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            values,
        )
        assertThat(rowId).isGreaterThan(0L)

        val cursor = db.query(
            "SELECT custom_fields_ciphertext, custom_fields_iv FROM credentials WHERE id = $rowId",
        )
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getBlob(0)).isEqualTo(cfCiphertext)
            assertThat(it.getBlob(1)).isEqualTo(cfIv)
        }
    }

    @Test
    fun migrate_isIdempotentOnEmptyDatabase() {
        // Arrange: empty v2 database.
        val db = openV2Database()

        // Act
        Migration_2_3.migrate(db)

        // Assert: COUNT(*) = 0 and both new columns are queryable.
        val countCursor = db.query("SELECT COUNT(*) FROM credentials")
        countCursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(0)).isEqualTo(0L)
        }
        val colCursor = db.query("SELECT custom_fields_ciphertext, custom_fields_iv FROM credentials LIMIT 1")
        colCursor.use {
            assertThat(it.columnNames.toList())
                .containsAtLeast("custom_fields_ciphertext", "custom_fields_iv")
        }
    }

    // ---- v2 fixture helpers ---------------------------------------------

    private fun openV2Database(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val configured = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(V2_CREATE_TABLE_SQL)
                        db.execSQL(V2_CREATE_INDEX_SQL)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper = configured
        return configured.writableDatabase
    }

    private fun insertV2Row(
        db: SupportSQLiteDatabase,
        packageName: String,
        username: String,
        label: String,
        createdAt: Long,
        updatedAt: Long,
        lastUsedAt: Long?,
    ) {
        val values = ContentValues().apply {
            put("package_name", packageName)
            put("username", username)
            put("label", label)
            put("password_ciphertext", byteArrayOf(1, 2, 3))
            put("password_iv", ByteArray(12) { 0x10.toByte() })
            putNull("signature_sha256")
            putNull("signature_captured_at")
            put("created_at", createdAt)
            put("updated_at", updatedAt)
            if (lastUsedAt != null) put("last_used_at", lastUsedAt) else putNull("last_used_at")
        }
        db.insert("credentials", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private companion object {
        // Faithful reproduction of the v2 schema that Room generates for
        // CredentialEntity (includes `last_used_at INTEGER` from
        // Migration_1_2).
        private const val V2_CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS `credentials` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `package_name` TEXT NOT NULL,
                `username` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `password_ciphertext` BLOB NOT NULL,
                `password_iv` BLOB NOT NULL,
                `signature_sha256` BLOB,
                `signature_captured_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `last_used_at` INTEGER
            )
        """
        private const val V2_CREATE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_credentials_package_name` ON `credentials` (`package_name`)"
    }
}
