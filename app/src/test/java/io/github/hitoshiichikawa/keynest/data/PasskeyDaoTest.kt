package io.github.hitoshiichikawa.keynest.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.dao.PasskeyDao
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behavioural unit tests for [PasskeyDao]. Issue #91 (parent #89) /
 * Issue #107 T-09 (tasks.md §T-09 / design.md §9.3).
 *
 * Pattern mirrors [io.github.hitoshiichikawa.keynest.data.CredentialDaoTest]
 * and [io.github.hitoshiichikawa.keynest.data.DetectedFieldDaoTest]: a real
 * in-memory Room database is constructed via Robolectric so the host JVM
 * `:app:testDebugUnitTest` task can execute every DAO method end to end.
 * AndroidKeyStore is not exercised here — the encryption boundary lives in
 * `PasskeyRepositoryImpl` and is covered by `PasskeyRepositoryTest`.
 *
 * Cases (14, per tasks.md §T-09 / design.md §9.3):
 *  1. insert_thenFindByCredentialId_returnsEntity        — req 3.2 / 5.4
 *  2. findByCredentialId_returnsNull_whenAbsent          — req 3.2 / 5.4
 *  3. insert_duplicateRpIdAndUserHandle_throwsConstraintException — req 5.6
 *  4. findByRpIdAndUserHandle_returnsSingleEntity        — req 3.3 / 5.4
 *  5. findByRpIdAndUserHandle_returnsNull_whenUserHandleDiffers — req 3.3 / 5.4
 *  6. listDiscoverableByRpId_excludesNonDiscoverable     — req 3.4 / 5.5
 *  7. listDiscoverableByRpId_ordersByLastUsedAtThenCreatedAt — req 3.4 / 5.2
 *  8. listAllByRpId_includesNonDiscoverable              — req 3.5 / 5.5
 *  9. listAllByRpId_excludesOtherRpId                    — req 3.5
 * 10. incrementSignCount_increments_andUpdatesLastUsedAt — req 3.6 / 5.7
 * 11. incrementSignCount_onAbsentRow_isNoOp              — req 3.7 / 5.7
 * 12. delete_removesOnlyTargetRow                        — req 3.7
 * 13. delete_onAbsentCredentialId_isNoOp                 — req 3.7
 * 14. update_persistsModifiedFields                      — basic behaviour
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PasskeyDaoTest {

    private lateinit var db: KeyNestDatabase
    private lateinit var dao: PasskeyDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeyNestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.passkeyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- 1. insert + findByCredentialId roundtrip ----------------------

    @Test
    fun insert_thenFindByCredentialId_returnsEntity() = runTest {
        val entity = passkeyEntity(credentialId = "cred-1")

        dao.insert(entity)
        val loaded = dao.findByCredentialId("cred-1")

        assertThat(loaded).isEqualTo(entity)
    }

    // ---- 2. lookup miss ------------------------------------------------

    @Test
    fun findByCredentialId_returnsNull_whenAbsent() = runTest {
        val loaded = dao.findByCredentialId("does-not-exist")

        assertThat(loaded).isNull()
    }

    // ---- 3. (rpId, userHandle) UNIQUE constraint ----------------------

    @Test
    fun insert_duplicateRpIdAndUserHandle_throwsConstraintException() = runTest {
        val handle = ByteArray(16) { 0x33 }
        dao.insert(
            passkeyEntity(
                credentialId = "cred-1",
                rpId = "example.com",
                userHandle = handle,
            ),
        )

        // Different credentialId but identical (rpId, userHandle) — the
        // UNIQUE INDEX must fire and Room must surface
        // SQLiteConstraintException to the caller.
        val ex = runCatching {
            dao.insert(
                passkeyEntity(
                    credentialId = "cred-2",
                    rpId = "example.com",
                    userHandle = handle,
                ),
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(SQLiteConstraintException::class.java)
    }

    // ---- 4. findByRpIdAndUserHandle happy path -------------------------

    @Test
    fun findByRpIdAndUserHandle_returnsSingleEntity() = runTest {
        val handle = ByteArray(16) { 0x44 }
        val target = passkeyEntity(
            credentialId = "cred-target",
            rpId = "example.com",
            userHandle = handle,
        )
        dao.insert(target)
        // Different RP, same handle — must NOT collide on lookup.
        dao.insert(
            passkeyEntity(
                credentialId = "cred-other-rp",
                rpId = "other.example.com",
                userHandle = handle,
            ),
        )

        val loaded = dao.findByRpIdAndUserHandle("example.com", handle)

        assertThat(loaded).isEqualTo(target)
    }

    // ---- 5. findByRpIdAndUserHandle: BLOB equality semantics -----------

    @Test
    fun findByRpIdAndUserHandle_returnsNull_whenUserHandleDiffers() = runTest {
        dao.insert(
            passkeyEntity(
                credentialId = "cred-1",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x55 },
            ),
        )

        // Different byte content — even one differing byte must fail the
        // BLOB equality predicate.
        val loaded = dao.findByRpIdAndUserHandle(
            rpId = "example.com",
            userHandle = ByteArray(16) { 0x56 },
        )

        assertThat(loaded).isNull()
    }

    // ---- 6. listDiscoverableByRpId: non-discoverable filtered out ------

    @Test
    fun listDiscoverableByRpId_excludesNonDiscoverable() = runTest {
        dao.insert(
            passkeyEntity(
                credentialId = "cred-d",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x01 },
                isDiscoverable = true,
            ),
        )
        dao.insert(
            passkeyEntity(
                credentialId = "cred-nd",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x02 },
                isDiscoverable = false,
            ),
        )

        val discoverable = dao.listDiscoverableByRpId("example.com")

        assertThat(discoverable.map { it.credentialId })
            .containsExactly("cred-d")
    }

    // ---- 7. listDiscoverableByRpId: ORDER BY contract ------------------

    @Test
    fun listDiscoverableByRpId_ordersByLastUsedAtThenCreatedAt() = runTest {
        // design §5.2: (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC.
        // Recently-used entries come first; NEVER-used entries (NULL
        // lastUsedAt) fall to the tail with createdAt DESC tiebreaker.
        dao.insert(
            passkeyEntity(
                credentialId = "used-old",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x01 },
                createdAt = 100L,
                lastUsedAt = 1_000L,
            ),
        )
        dao.insert(
            passkeyEntity(
                credentialId = "used-new",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x02 },
                createdAt = 100L,
                lastUsedAt = 5_000L,
            ),
        )
        dao.insert(
            passkeyEntity(
                credentialId = "never-old",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x03 },
                createdAt = 200L,
                lastUsedAt = null,
            ),
        )
        dao.insert(
            passkeyEntity(
                credentialId = "never-new",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x04 },
                createdAt = 300L,
                lastUsedAt = null,
            ),
        )

        val ordered = dao.listDiscoverableByRpId("example.com")

        assertThat(ordered.map { it.credentialId }).containsExactly(
            "used-new",     // lastUsedAt 5000 (DESC)
            "used-old",     // lastUsedAt 1000
            "never-new",    // NULL, createdAt 300 DESC
            "never-old",    // NULL, createdAt 200
        ).inOrder()
    }

    // ---- 8. listAllByRpId: includes non-discoverable -------------------

    @Test
    fun listAllByRpId_includesNonDiscoverable() = runTest {
        dao.insert(
            passkeyEntity(
                credentialId = "cred-d",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x01 },
                isDiscoverable = true,
            ),
        )
        dao.insert(
            passkeyEntity(
                credentialId = "cred-nd",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x02 },
                isDiscoverable = false,
            ),
        )

        val all = dao.listAllByRpId("example.com")

        assertThat(all.map { it.credentialId })
            .containsExactly("cred-d", "cred-nd")
    }

    // ---- 9. listAllByRpId scopes to the requested RP -------------------

    @Test
    fun listAllByRpId_excludesOtherRpId() = runTest {
        dao.insert(
            passkeyEntity(
                credentialId = "cred-target",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x01 },
            ),
        )
        dao.insert(
            passkeyEntity(
                credentialId = "cred-other",
                rpId = "other.example.com",
                userHandle = ByteArray(16) { 0x02 },
            ),
        )

        val rows = dao.listAllByRpId("example.com")

        assertThat(rows.map { it.credentialId }).containsExactly("cred-target")
    }

    // ---- 10. incrementSignCount happy path -----------------------------

    @Test
    fun incrementSignCount_increments_andUpdatesLastUsedAt() = runTest {
        dao.insert(
            passkeyEntity(
                credentialId = "cred-1",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x01 },
                signCount = 41L,
                lastUsedAt = null,
            ),
        )

        dao.incrementSignCount("cred-1", timestamp = 9_999L)

        val updated = dao.findByCredentialId("cred-1")
        assertThat(updated?.signCount).isEqualTo(42L)
        assertThat(updated?.lastUsedAt).isEqualTo(9_999L)
    }

    // ---- 11. incrementSignCount on missing row: silent no-op ----------

    @Test
    fun incrementSignCount_onAbsentRow_isNoOp() = runTest {
        // No row inserted for "missing". The single-UPDATE statement must
        // match zero rows and silently succeed (SQLite default behaviour).
        dao.incrementSignCount("missing", timestamp = 1_234L)

        assertThat(dao.findByCredentialId("missing")).isNull()
    }

    // ---- 12. delete removes only the target row ------------------------

    @Test
    fun delete_removesOnlyTargetRow() = runTest {
        dao.insert(
            passkeyEntity(
                credentialId = "cred-keep",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x01 },
            ),
        )
        dao.insert(
            passkeyEntity(
                credentialId = "cred-drop",
                rpId = "example.com",
                userHandle = ByteArray(16) { 0x02 },
            ),
        )

        dao.delete("cred-drop")

        assertThat(dao.findByCredentialId("cred-drop")).isNull()
        assertThat(dao.findByCredentialId("cred-keep")).isNotNull()
    }

    // ---- 13. delete: silent no-op on missing row -----------------------

    @Test
    fun delete_onAbsentCredentialId_isNoOp() = runTest {
        // DELETE WHERE credentialId = :x → 0 rows matched → SQLite returns
        // success without throwing. design §5.3 explicitly relies on this.
        dao.delete("does-not-exist")

        // Nothing to assert beyond "no exception" — sanity check the table
        // is still empty.
        assertThat(dao.findByCredentialId("does-not-exist")).isNull()
    }

    // ---- 14. update overwrites mutable fields --------------------------

    @Test
    fun update_persistsModifiedFields() = runTest {
        val original = passkeyEntity(
            credentialId = "cred-1",
            rpId = "example.com",
            userHandle = ByteArray(16) { 0x01 },
            displayName = "Original",
            isDiscoverable = true,
            signCount = 0L,
        )
        dao.insert(original)

        val mutated = original.copy(
            displayName = "Renamed",
            isDiscoverable = false,
            signCount = 7L,
            lastUsedAt = 2_000L,
        )
        dao.update(mutated)

        val loaded = dao.findByCredentialId("cred-1")
        assertThat(loaded?.displayName).isEqualTo("Renamed")
        assertThat(loaded?.isDiscoverable).isFalse()
        assertThat(loaded?.signCount).isEqualTo(7L)
        assertThat(loaded?.lastUsedAt).isEqualTo(2_000L)
    }

    // ---- fixture helper -------------------------------------------------

    /**
     * Test fixture builder. Every parameter has a deterministic default so a
     * call site can override only the fields under test. ByteArrays use
     * tag-like fill bytes so it is obvious in debugger output which slot a
     * value came from.
     */
    private fun passkeyEntity(
        credentialId: String,
        rpId: String = "example.com",
        rpDisplayName: String? = "Example",
        userHandle: ByteArray = ByteArray(16) { 0x77 },
        userName: String? = "alice@example.com",
        userDisplayName: String? = "Alice",
        isDiscoverable: Boolean = true,
        encryptedPrivateKey: ByteArray = ByteArray(48) { 0x66 },
        privateKeyIv: ByteArray = ByteArray(12) { 0x55 },
        keyAlias: String = "passkey_$credentialId",
        signCount: Long = 0L,
        displayName: String? = null,
        createdAt: Long = 1_700_000_000_000L,
        lastUsedAt: Long? = null,
    ) = PasskeyEntity(
        credentialId = credentialId,
        rpId = rpId,
        rpDisplayName = rpDisplayName,
        userHandle = userHandle,
        userName = userName,
        userDisplayName = userDisplayName,
        isDiscoverable = isDiscoverable,
        encryptedPrivateKey = encryptedPrivateKey,
        privateKeyIv = privateKeyIv,
        keyAlias = keyAlias,
        signCount = signCount,
        displayName = displayName,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )
}
