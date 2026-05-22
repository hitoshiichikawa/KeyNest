package io.github.hitoshiichikawa.keynest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.dao.PasskeyDao
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for the [PasskeyDao.listAll] method introduced in Issue #101
 * (Phase 4 of umbrella #89). Backs requirements R4.10:
 *
 *  - empty table emits an empty list on first subscribe
 *  - inserts cause the Flow to re-emit an updated list (Room invalidation
 *    tracker integration)
 *  - the SQL ordering matches the existing per-RP queries
 *    (lastUsedAt DESC, NULL last, then createdAt DESC tiebreaker)
 *
 * Uses Robolectric (sdk = 34) + an in-memory Room database so the test runs
 * on the JVM `:app:testDebugUnitTest` task — matching the project pattern
 * established by `PasskeyRepositoryTest` and `Migration_4_5_Test`.
 *
 * The pre-existing 8 DAO methods (`insert` / `update` / `delete` /
 * `findByCredentialId` / `findByRpIdAndUserHandle` / `listDiscoverableByRpId`
 * / `listAllByRpId` / `incrementSignCount`) are deliberately NOT re-tested
 * here — they are covered indirectly by `PasskeyRepositoryTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PasskeyDaoTest {

    private lateinit var db: KeyNestDatabase
    private lateinit var dao: PasskeyDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeyNestDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.passkeyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- R4.10 (a): empty table emits empty list ----------------------

    @Test
    fun listAll_emitsEmptyList_whenTableIsEmpty() = runTest {
        val first = dao.listAll().first()

        assertThat(first).isEmpty()
    }

    // ---- R4.10 (b) / (c): Flow re-emits after inserts ------------------

    @Test
    fun listAll_reflectsInsertedRow() = runTest {
        val entity = sampleEntity(credentialId = "cid-1", lastUsedAt = 100L, createdAt = 50L)
        dao.insert(entity)

        val list = dao.listAll().first()

        assertThat(list).containsExactly(entity)
    }

    @Test
    fun listAll_reflectsMultipleInsertedRows() = runTest {
        val a = sampleEntity(credentialId = "cid-a", lastUsedAt = 200L, createdAt = 50L)
        val b = sampleEntity(credentialId = "cid-b", lastUsedAt = 100L, createdAt = 10L)
        dao.insert(a)
        dao.insert(b)

        val list = dao.listAll().first()

        assertThat(list).hasSize(2)
        assertThat(list).containsExactly(a, b).inOrder()
    }

    // ---- R4.10 (d): ordering matches listAllByRpId semantics ----------

    @Test
    fun listAll_ordersByLastUsedDescThenCreatedDescNullsLast() = runTest {
        // Four rows covering every ordering edge case:
        //  - rowHigh:   lastUsedAt = 300, createdAt = 100  -> first (newest use)
        //  - rowMid:    lastUsedAt = 200, createdAt = 50   -> second
        //  - rowNullA:  lastUsedAt = null, createdAt = 150 -> third (null last,
        //                                                     createdAt tiebreaker)
        //  - rowNullB:  lastUsedAt = null, createdAt = 50  -> fourth
        val rowHigh = sampleEntity(credentialId = "high", lastUsedAt = 300L, createdAt = 100L)
        val rowMid = sampleEntity(credentialId = "mid", lastUsedAt = 200L, createdAt = 50L)
        val rowNullA = sampleEntity(credentialId = "nullA", lastUsedAt = null, createdAt = 150L)
        val rowNullB = sampleEntity(credentialId = "nullB", lastUsedAt = null, createdAt = 50L)
        // Insert in scrambled order so we are confident the ORDER BY is doing
        // the work (not the insertion order).
        dao.insert(rowNullB)
        dao.insert(rowHigh)
        dao.insert(rowNullA)
        dao.insert(rowMid)

        val list = dao.listAll().first()

        assertThat(list).containsExactly(rowHigh, rowMid, rowNullA, rowNullB).inOrder()
    }

    @Test
    fun listAll_breaksTies_byCreatedAtDesc_whenLastUsedAtMatches() = runTest {
        val newer = sampleEntity(credentialId = "newer", lastUsedAt = 100L, createdAt = 200L)
        val older = sampleEntity(credentialId = "older", lastUsedAt = 100L, createdAt = 100L)
        dao.insert(older)
        dao.insert(newer)

        val list = dao.listAll().first()

        // Same lastUsedAt -> newer createdAt sorts first.
        assertThat(list).containsExactly(newer, older).inOrder()
    }

    @Test
    fun listAll_returnsBothDiscoverableAndNonDiscoverableRows() = runTest {
        // D-7: the integrated credential list shows every stored PassKey
        // regardless of resident-key flag, unlike `listDiscoverableByRpId`.
        val discoverable = sampleEntity(
            credentialId = "disc",
            lastUsedAt = 200L,
            createdAt = 50L,
            isDiscoverable = true,
        )
        val nonDiscoverable = sampleEntity(
            credentialId = "non",
            lastUsedAt = 100L,
            createdAt = 50L,
            isDiscoverable = false,
        )
        dao.insert(discoverable)
        dao.insert(nonDiscoverable)

        val list = dao.listAll().first()

        assertThat(list).containsExactly(discoverable, nonDiscoverable).inOrder()
    }

    // ---- helpers -------------------------------------------------------

    /**
     * Build a sample entity. The `(rpId, userHandle)` UNIQUE index in
     * `PasskeyEntity` means each row needs a distinct userHandle when
     * sharing the default `rpId`; we derive it from `credentialId` so
     * tests can stay focused on ordering / counting rather than user
     * handle bookkeeping.
     */
    private fun sampleEntity(
        credentialId: String,
        rpId: String = "example.com",
        userName: String? = "alice@example.com",
        userDisplayName: String? = "Alice",
        isDiscoverable: Boolean = true,
        signCount: Long = 0L,
        displayName: String? = null,
        createdAt: Long = 1_700_000_000_000L,
        lastUsedAt: Long? = null,
    ): PasskeyEntity {
        val userHandle = ByteArray(16) { index ->
            (credentialId.hashCode() + index).toByte()
        }
        return PasskeyEntity(
            credentialId = credentialId,
            rpId = rpId,
            rpDisplayName = "Example",
            userHandle = userHandle,
            userName = userName,
            userDisplayName = userDisplayName,
            isDiscoverable = isDiscoverable,
            encryptedPrivateKey = ByteArray(48) { 0x66 },
            privateKeyIv = ByteArray(12) { 0x55 },
            keyAlias = "keynest_passkey_$credentialId",
            signCount = signCount,
            displayName = displayName,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
        )
    }
}
