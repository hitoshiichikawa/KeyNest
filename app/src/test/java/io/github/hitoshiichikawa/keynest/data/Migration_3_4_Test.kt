package io.github.hitoshiichikawa.keynest.data

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.migration.Migration_3_4
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies the [Migration_3_4] migration. Issue #67 Phase 2
 * (requirements §5 Req 2 / design.md §9.1).
 *
 * Same isolation pattern as
 * [io.github.hitoshiichikawa.keynest.data.Migration_2_3_Test] — drive
 * the migration against a SupportSQLiteDatabase pre-populated with the
 * v3 schema directly, skipping Room's full open-and-validate pipeline.
 * The v3 fixture mirrors `app/schemas/.../3.json` byte-for-byte so the
 * test reflects what an upgrade from a 3.json-shaped DB will actually
 * see in production.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class Migration_3_4_Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "migration_3_4_test_${System.nanoTime()}.db"
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
    fun migrate_createsDetectedFieldsTable_andPreservesCredentials() {
        // Arrange: v3 schema with one credentials row already present.
        val db = openV3Database()
        insertV3CredentialRow(
            db,
            packageName = "com.example.target",
            username = "alice",
            label = "Example",
            createdAt = 100L,
            updatedAt = 200L,
        )

        // Act
        Migration_3_4.migrate(db)

        // Assert 1: detected_fields table now exists and is empty.
        val countCursor = db.query("SELECT COUNT(*) FROM detected_fields")
        countCursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(0)).isEqualTo(0L)
        }

        // Assert 2: detected_fields has the expected columns.
        val colCursor = db.query("PRAGMA table_info(detected_fields)")
        val columnNames = mutableListOf<String>()
        val notNullFlags = mutableMapOf<String, Int>()
        val pkOrder = mutableMapOf<String, Int>()
        colCursor.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow("name"))
                columnNames += name
                notNullFlags[name] = it.getInt(it.getColumnIndexOrThrow("notnull"))
                pkOrder[name] = it.getInt(it.getColumnIndexOrThrow("pk"))
            }
        }
        assertThat(columnNames).containsExactly(
            "package_name",
            "field_key",
            "source",
            "last_detected_at",
        )
        // All four columns are NOT NULL.
        assertThat(notNullFlags.values).containsExactly(1, 1, 1, 1)
        // Composite primary key in declared order: package_name (1),
        // field_key (2), source (3). last_detected_at is not part of
        // the PK (0).
        assertThat(pkOrder["package_name"]).isEqualTo(1)
        assertThat(pkOrder["field_key"]).isEqualTo(2)
        assertThat(pkOrder["source"]).isEqualTo(3)
        assertThat(pkOrder["last_detected_at"]).isEqualTo(0)

        // Assert 3: the (package_name, last_detected_at) index exists.
        val indexCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='detected_fields'",
        )
        val indexNames = mutableListOf<String>()
        indexCursor.use {
            while (it.moveToNext()) {
                indexNames += it.getString(0)
            }
        }
        assertThat(indexNames)
            .contains("index_detected_fields_package_name_last_detected_at")

        // Assert 4: credentials row is preserved verbatim.
        val credCursor = db.query(
            "SELECT id, package_name, username, label, created_at, updated_at, " +
                "last_used_at, custom_fields_ciphertext, custom_fields_iv FROM credentials",
        )
        credCursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(it.getColumnIndexOrThrow("package_name")))
                .isEqualTo("com.example.target")
            assertThat(it.getString(it.getColumnIndexOrThrow("username"))).isEqualTo("alice")
            assertThat(it.getString(it.getColumnIndexOrThrow("label"))).isEqualTo("Example")
            assertThat(it.getLong(it.getColumnIndexOrThrow("created_at"))).isEqualTo(100L)
            assertThat(it.getLong(it.getColumnIndexOrThrow("updated_at"))).isEqualTo(200L)
            assertThat(it.moveToNext()).isFalse()
        }

        // Assert 5: credentials column shape is unchanged (no new
        // columns / no dropped columns).
        val credColCursor = db.query("PRAGMA table_info(credentials)")
        val credColumnNames = mutableListOf<String>()
        credColCursor.use {
            while (it.moveToNext()) {
                credColumnNames += it.getString(it.getColumnIndexOrThrow("name"))
            }
        }
        assertThat(credColumnNames).containsExactly(
            "id",
            "package_name",
            "username",
            "label",
            "password_ciphertext",
            "password_iv",
            "signature_sha256",
            "signature_captured_at",
            "created_at",
            "updated_at",
            "last_used_at",
            "custom_fields_ciphertext",
            "custom_fields_iv",
        )
    }

    @Test
    fun migrate_allowsInsert_afterMigration() {
        // Sanity: post-migration, we can insert and read a sample
        // detected_fields row through plain SQL. (DAO-level coverage
        // lives in DetectedFieldDaoTest.)
        val db = openV3Database()
        Migration_3_4.migrate(db)

        val values = ContentValues().apply {
            put("package_name", "com.example.target")
            put("field_key", "loginEmail")
            put("source", "resourceId")
            put("last_detected_at", 12345L)
        }
        val rowId = db.insert(
            "detected_fields",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            values,
        )
        assertThat(rowId).isGreaterThan(0L)

        val cursor = db.query(
            "SELECT package_name, field_key, source, last_detected_at " +
                "FROM detected_fields WHERE package_name = 'com.example.target'",
        )
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example.target")
            assertThat(it.getString(1)).isEqualTo("loginEmail")
            assertThat(it.getString(2)).isEqualTo("resourceId")
            assertThat(it.getLong(3)).isEqualTo(12345L)
        }
    }

    @Test
    fun migrate_isIdempotent_onSecondCall() {
        // Defensive: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT
        // EXISTS keep migrate(db) safe to re-run (matters for the
        // Robolectric in-memory test path and for any test that
        // double-applies the migration during retries).
        val db = openV3Database()
        Migration_3_4.migrate(db)
        Migration_3_4.migrate(db)

        val countCursor = db.query("SELECT COUNT(*) FROM detected_fields")
        countCursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(0)).isEqualTo(0L)
        }
    }

    @Test
    fun migrate_compositePrimaryKey_rejectsDuplicateInserts() {
        // PRIMARY KEY (package_name, field_key, source) → a second
        // CONFLICT_ABORT insert with the same triple must fail.
        // (Production code uses INSERT OR REPLACE so this never
        // surfaces in practice, but the constraint must exist.)
        val db = openV3Database()
        Migration_3_4.migrate(db)
        val values = ContentValues().apply {
            put("package_name", "com.example.target")
            put("field_key", "loginEmail")
            put("source", "resourceId")
            put("last_detected_at", 1L)
        }
        val firstId = db.insert(
            "detected_fields",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            values,
        )
        assertThat(firstId).isGreaterThan(0L)

        val secondId = db.insert(
            "detected_fields",
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
            values,
        )
        // CONFLICT_IGNORE returns -1 when the row was suppressed; this
        // proves the PK constraint fired.
        assertThat(secondId).isEqualTo(-1L)
    }

    // ---- v3 fixture helpers ---------------------------------------------

    private fun openV3Database(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val configured = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(V3_CREATE_CREDENTIALS_SQL)
                        db.execSQL(V3_CREATE_CREDENTIALS_INDEX_SQL)
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

    private fun insertV3CredentialRow(
        db: SupportSQLiteDatabase,
        packageName: String,
        username: String,
        label: String,
        createdAt: Long,
        updatedAt: Long,
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
            putNull("last_used_at")
            put("custom_fields_ciphertext", ByteArray(0))
            put("custom_fields_iv", ByteArray(0))
        }
        db.insert("credentials", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private companion object {
        // Mirrors `app/schemas/.../3.json` (Phase 1 final v3 schema).
        private const val V3_CREATE_CREDENTIALS_SQL = """
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
                `last_used_at` INTEGER,
                `custom_fields_ciphertext` BLOB NOT NULL,
                `custom_fields_iv` BLOB NOT NULL
            )
        """
        private const val V3_CREATE_CREDENTIALS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_credentials_package_name` " +
                "ON `credentials` (`package_name`)"
    }
}
