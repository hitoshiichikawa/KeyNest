package io.github.hitoshiichikawa.keynest.data

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.migration.Migration_4_5
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies the [Migration_4_5] migration. Issue #91 (parent #89), Issue #107
 * T-08 (tasks.md §T-08 / design.md §9.2).
 *
 * Pattern matches the established
 * [io.github.hitoshiichikawa.keynest.data.Migration_3_4_Test] approach: run
 * the migration against a SupportSQLiteDatabase pre-populated with the v4
 * schema byte-for-byte (mirrors `app/schemas/.../4.json`) and validate the
 * resulting structure via PRAGMA queries. Room's MigrationTestHelper is
 * deliberately NOT used — the rest of the migration suite avoids it so the
 * tests stay self-contained and do not depend on KSP-generated JSON during
 * the test run.
 *
 * Covers:
 *  - Req 2.1 / 5.1: the `passkeys` table is created with all 14 columns and
 *    the correct NOT NULL / DEFAULT metadata
 *  - Req 2.2 / 5.3: `index_passkeys_rpId` (non-unique) and
 *    `index_passkeys_rpId_userHandle` (UNIQUE) exist
 *  - Req 2.3 / 5.2: existing `credentials` / `detected_fields` rows survive
 *    the migration verbatim (no `ALTER TABLE` on legacy tables)
 *  - Idempotency: a second `migrate(db)` call succeeds because of
 *    `IF NOT EXISTS` clauses (matches Migration_3_4_Test pattern)
 *  - UNIQUE constraint enforcement: a duplicate `(rpId, userHandle)` insert
 *    is rejected by CONFLICT_IGNORE (returns -1)
 *  - Post-migration writes succeed: a row that satisfies NOT NULL / DEFAULT
 *    metadata inserts cleanly
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class Migration_4_5_Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "migration_4_5_test_${System.nanoTime()}.db"
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
    fun migrate_createsPasskeysTable_andPreservesCredentialsAndDetectedFields() {
        // Arrange: v4 fixture with one row in each existing table.
        val db = openV4Database()
        insertV4CredentialRow(
            db,
            packageName = "com.example.target",
            username = "alice",
            label = "Example",
            createdAt = 100L,
            updatedAt = 200L,
        )
        insertV4DetectedFieldRow(
            db,
            packageName = "com.example.target",
            fieldKey = "loginEmail",
            source = "resourceId",
            lastDetectedAt = 12345L,
        )

        // Act
        Migration_4_5.migrate(db)

        // Assert 1: passkeys table exists with the exact 14 columns in
        // declared order (matches PasskeyEntity + 5.json fields[]).
        val colCursor = db.query("PRAGMA table_info(passkeys)")
        val columnNames = mutableListOf<String>()
        val notNullFlags = mutableMapOf<String, Int>()
        val defaultValues = mutableMapOf<String, String?>()
        val pkOrder = mutableMapOf<String, Int>()
        colCursor.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow("name"))
                columnNames += name
                notNullFlags[name] = it.getInt(it.getColumnIndexOrThrow("notnull"))
                val dfltIdx = it.getColumnIndexOrThrow("dflt_value")
                defaultValues[name] = if (it.isNull(dfltIdx)) null else it.getString(dfltIdx)
                pkOrder[name] = it.getInt(it.getColumnIndexOrThrow("pk"))
            }
        }
        assertThat(columnNames).containsExactly(
            "credentialId",
            "rpId",
            "rpDisplayName",
            "userHandle",
            "userName",
            "userDisplayName",
            "isDiscoverable",
            "encryptedPrivateKey",
            "privateKeyIv",
            "keyAlias",
            "signCount",
            "displayName",
            "createdAt",
            "lastUsedAt",
        ).inOrder()

        // NOT NULL flags: nullable columns are rpDisplayName / userName /
        // userDisplayName / displayName / lastUsedAt. Everything else NOT NULL.
        assertThat(notNullFlags["credentialId"]).isEqualTo(1)
        assertThat(notNullFlags["rpId"]).isEqualTo(1)
        assertThat(notNullFlags["rpDisplayName"]).isEqualTo(0)
        assertThat(notNullFlags["userHandle"]).isEqualTo(1)
        assertThat(notNullFlags["userName"]).isEqualTo(0)
        assertThat(notNullFlags["userDisplayName"]).isEqualTo(0)
        assertThat(notNullFlags["isDiscoverable"]).isEqualTo(1)
        assertThat(notNullFlags["encryptedPrivateKey"]).isEqualTo(1)
        assertThat(notNullFlags["privateKeyIv"]).isEqualTo(1)
        assertThat(notNullFlags["keyAlias"]).isEqualTo(1)
        assertThat(notNullFlags["signCount"]).isEqualTo(1)
        assertThat(notNullFlags["displayName"]).isEqualTo(0)
        assertThat(notNullFlags["createdAt"]).isEqualTo(1)
        assertThat(notNullFlags["lastUsedAt"]).isEqualTo(0)

        // DEFAULT values per design §3.1.2 / §3.1.3: isDiscoverable defaults
        // to 1, signCount defaults to 0. All other columns have no DEFAULT.
        assertThat(defaultValues["isDiscoverable"]).isEqualTo("1")
        assertThat(defaultValues["signCount"]).isEqualTo("0")
        assertThat(defaultValues["credentialId"]).isNull()
        assertThat(defaultValues["createdAt"]).isNull()

        // credentialId is the (single-column) PRIMARY KEY.
        assertThat(pkOrder["credentialId"]).isEqualTo(1)
        // No other column participates in the PK.
        assertThat(pkOrder.filterValues { it > 0 }).hasSize(1)

        // Assert 2: the legacy credentials row survived the migration verbatim.
        val credCursor = db.query(
            "SELECT package_name, username, label, created_at, updated_at FROM credentials",
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

        // Assert 3: the legacy detected_fields row also survived.
        val dfCursor = db.query(
            "SELECT package_name, field_key, source, last_detected_at FROM detected_fields",
        )
        dfCursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example.target")
            assertThat(it.getString(1)).isEqualTo("loginEmail")
            assertThat(it.getString(2)).isEqualTo("resourceId")
            assertThat(it.getLong(3)).isEqualTo(12345L)
            assertThat(it.moveToNext()).isFalse()
        }
    }

    @Test
    fun migrate_createsIndexes() {
        // Defensive: design §3.2 mandates two indices on `passkeys`. Confirm
        // both exist with the correct unique flag and column composition.
        val db = openV4Database()
        Migration_4_5.migrate(db)

        // sqlite_master lists every index attached to the table.
        val indexNamesCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='passkeys' " +
                "AND name NOT LIKE 'sqlite_autoindex_%'",
        )
        val indexNames = mutableListOf<String>()
        indexNamesCursor.use {
            while (it.moveToNext()) indexNames += it.getString(0)
        }
        assertThat(indexNames).containsExactly(
            "index_passkeys_rpId",
            "index_passkeys_rpId_userHandle",
        )

        // PRAGMA index_list returns one row per index, with a `unique` 0/1
        // column we can assert on.
        val indexListCursor = db.query("PRAGMA index_list('passkeys')")
        val uniqueByName = mutableMapOf<String, Int>()
        indexListCursor.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow("name"))
                if (name in indexNames) {
                    uniqueByName[name] = it.getInt(it.getColumnIndexOrThrow("unique"))
                }
            }
        }
        assertThat(uniqueByName["index_passkeys_rpId"]).isEqualTo(0)
        assertThat(uniqueByName["index_passkeys_rpId_userHandle"]).isEqualTo(1)

        // PRAGMA index_info reveals the column order inside the composite
        // unique index — must be (rpId, userHandle) in that order.
        val indexInfoCursor = db.query("PRAGMA index_info('index_passkeys_rpId_userHandle')")
        val compositeColumns = mutableListOf<String>()
        indexInfoCursor.use {
            while (it.moveToNext()) {
                compositeColumns += it.getString(it.getColumnIndexOrThrow("name"))
            }
        }
        assertThat(compositeColumns).containsExactly("rpId", "userHandle").inOrder()
    }

    @Test
    fun migrate_isIdempotent_onSecondCall() {
        // CREATE TABLE / CREATE INDEX both use `IF NOT EXISTS` so a second
        // migrate(db) must be a silent no-op (matters for any test that
        // re-runs the migration and for defense against accidental double-apply).
        val db = openV4Database()
        Migration_4_5.migrate(db)
        Migration_4_5.migrate(db)

        // Post-second-migration the table is still empty and queryable.
        val cursor = db.query("SELECT COUNT(*) FROM passkeys")
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(0)).isEqualTo(0L)
        }
    }

    @Test
    fun migrate_uniqueConstraint_rejectsDuplicateRpIdAndUserHandle() {
        // Req 5.3 補強 — confirm the UNIQUE INDEX actually fires when a second
        // row with the same (rpId, userHandle) tuple is inserted.
        val db = openV4Database()
        Migration_4_5.migrate(db)

        val userHandle = ByteArray(16) { 0x33 }
        val firstId = db.insert(
            "passkeys",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            passkeyContentValues(
                credentialId = "cred-1",
                rpId = "example.com",
                userHandle = userHandle,
            ),
        )
        assertThat(firstId).isGreaterThan(0L)

        // Same (rpId, userHandle) different credentialId → CONFLICT_IGNORE
        // suppresses the row (returns -1), proving the UNIQUE constraint.
        val secondId = db.insert(
            "passkeys",
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
            passkeyContentValues(
                credentialId = "cred-2",
                rpId = "example.com",
                userHandle = userHandle,
            ),
        )
        assertThat(secondId).isEqualTo(-1L)
    }

    @Test
    fun migrate_allowsInsert_afterMigration() {
        // Sanity: post-migration a fully populated row that respects every
        // NOT NULL / DEFAULT can be inserted and read back.
        val db = openV4Database()
        Migration_4_5.migrate(db)

        val rowId = db.insert(
            "passkeys",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            passkeyContentValues(
                credentialId = "cred-1",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x44 },
            ),
        )
        assertThat(rowId).isGreaterThan(0L)

        val cursor = db.query(
            "SELECT credentialId, rpId, isDiscoverable, signCount FROM passkeys " +
                "WHERE credentialId = 'cred-1'",
        )
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("cred-1")
            assertThat(it.getString(1)).isEqualTo("example.com")
            // We did not set these — DEFAULT 1 / 0 should apply via PRAGMA.
            // Note: ContentValues path bypasses defaults only when columns
            // are omitted — we omit them in passkeyContentValues to exercise
            // the DEFAULT clause.
            assertThat(it.getInt(2)).isEqualTo(1)
            assertThat(it.getLong(3)).isEqualTo(0L)
        }
    }

    // ---- v4 fixture helpers ---------------------------------------------

    private fun openV4Database(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val configured = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(V4_CREATE_CREDENTIALS_SQL)
                        db.execSQL(V4_CREATE_CREDENTIALS_INDEX_SQL)
                        db.execSQL(V4_CREATE_DETECTED_FIELDS_SQL)
                        db.execSQL(V4_CREATE_DETECTED_FIELDS_INDEX_SQL)
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

    private fun insertV4CredentialRow(
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

    private fun insertV4DetectedFieldRow(
        db: SupportSQLiteDatabase,
        packageName: String,
        fieldKey: String,
        source: String,
        lastDetectedAt: Long,
    ) {
        val values = ContentValues().apply {
            put("package_name", packageName)
            put("field_key", fieldKey)
            put("source", source)
            put("last_detected_at", lastDetectedAt)
        }
        db.insert("detected_fields", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
    }

    /**
     * Builds a `passkeys` row that respects every NOT NULL constraint but
     * deliberately omits `isDiscoverable` / `signCount` so the SQLite DEFAULT
     * clauses (1 / 0) are exercised. Used by both the post-migration insert
     * sanity test and the UNIQUE constraint duplicate test.
     */
    private fun passkeyContentValues(
        credentialId: String,
        rpId: String,
        userHandle: ByteArray,
    ): ContentValues = ContentValues().apply {
        put("credentialId", credentialId)
        put("rpId", rpId)
        putNull("rpDisplayName")
        put("userHandle", userHandle)
        putNull("userName")
        putNull("userDisplayName")
        // isDiscoverable / signCount intentionally omitted to exercise DEFAULT
        put("encryptedPrivateKey", ByteArray(48) { 0x66 })
        put("privateKeyIv", ByteArray(12) { 0x55 })
        put("keyAlias", "keynest_passkey_$credentialId")
        putNull("displayName")
        put("createdAt", 1_700_000_000_000L)
        putNull("lastUsedAt")
    }

    private companion object {
        // Mirrors `app/schemas/.../4.json` (Issue #67 Phase 2 final v4 schema).
        private const val V4_CREATE_CREDENTIALS_SQL = """
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
        private const val V4_CREATE_CREDENTIALS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_credentials_package_name` " +
                "ON `credentials` (`package_name`)"

        private const val V4_CREATE_DETECTED_FIELDS_SQL = """
            CREATE TABLE IF NOT EXISTS `detected_fields` (
                `package_name` TEXT NOT NULL,
                `field_key` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `last_detected_at` INTEGER NOT NULL,
                PRIMARY KEY(`package_name`, `field_key`, `source`)
            )
        """
        private const val V4_CREATE_DETECTED_FIELDS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS " +
                "`index_detected_fields_package_name_last_detected_at` " +
                "ON `detected_fields` (`package_name` ASC, `last_detected_at` DESC)"
    }
}
