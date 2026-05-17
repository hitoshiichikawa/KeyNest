package io.github.hitoshiichikawa.keynest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hitoshiichikawa.keynest.data.dao.CredentialDao
import io.github.hitoshiichikawa.keynest.data.entity.CredentialEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room schema + DAO behaviour. Backs Req 1.1, 1.4, 1.5, 2.1, 2.2.
 *
 * Uses Robolectric (sdk = 33) so Room can build a real in-memory SQLite DB
 * on the host JVM. AndroidKeyStore is not exercised here - this layer never
 * touches the cipher.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CredentialDaoTest {

    private lateinit var db: KeyNestDatabase
    private lateinit var dao: CredentialDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeyNestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.credentialDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_thenFindById_returnsSavedRow() = runTest {
        // Arrange
        val toInsert = sample(packageName = "com.example.target", username = "alice")

        // Act
        val id = dao.insert(toInsert)
        val found = dao.findById(id)

        // Assert
        assertThat(id).isGreaterThan(0L)
        assertThat(found).isNotNull()
        assertThat(found!!.packageName).isEqualTo("com.example.target")
        assertThat(found.username).isEqualTo("alice")
    }

    @Test
    fun insert_allowsMultipleRowsForSamePackage() = runTest {
        // Req 1.4: multiple credentials for the same package_name must be allowed.
        dao.insert(sample(packageName = "com.example.target", username = "alice"))
        dao.insert(sample(packageName = "com.example.target", username = "bob"))

        val list = dao.findByPackage("com.example.target")

        assertThat(list).hasSize(2)
        assertThat(list.map { it.username }).containsExactly("alice", "bob")
    }

    @Test
    fun findByPackage_returnsRowsOrderedByUpdatedAtDesc() = runTest {
        val older = sample(packageName = "com.example.target", username = "older", updatedAt = 1L)
        val newer = sample(packageName = "com.example.target", username = "newer", updatedAt = 5L)
        dao.insert(older)
        dao.insert(newer)

        val list = dao.findByPackage("com.example.target")

        assertThat(list[0].username).isEqualTo("newer")
        assertThat(list[1].username).isEqualTo("older")
    }

    @Test
    fun findByPackage_returnsEmpty_whenNoMatch() = runTest {
        dao.insert(sample(packageName = "com.example.target", username = "alice"))
        assertThat(dao.findByPackage("com.example.other")).isEmpty()
    }

    @Test
    fun update_changesPersistedFields() = runTest {
        val id = dao.insert(sample(packageName = "com.example.target", username = "alice"))
        val saved = dao.findById(id)!!

        val updated = saved.copy(username = "alice2", updatedAt = saved.updatedAt + 1)
        dao.update(updated)

        val refetched = dao.findById(id)!!
        assertThat(refetched.username).isEqualTo("alice2")
    }

    @Test
    fun deleteById_removesRow() = runTest {
        val id = dao.insert(sample(packageName = "com.example.target", username = "alice"))
        dao.deleteById(id)
        assertThat(dao.findById(id)).isNull()
    }

    @Test
    fun nullableSignatureHash_persistsAsNull() = runTest {
        // Req 2.2: unsignatured credentials must be storable.
        val id = dao.insert(sample(packageName = "com.example.target", username = "alice").copy(
            signatureSha256 = null,
            signatureCapturedAt = null,
        ))
        val found = dao.findById(id)!!
        assertThat(found.signatureSha256).isNull()
        assertThat(found.signatureCapturedAt).isNull()
    }

    @Test
    fun observeAll_emitsCurrentRows() = runTest {
        dao.insert(sample(packageName = "com.example.a", username = "u1"))
        dao.insert(sample(packageName = "com.example.b", username = "u2"))

        val first = dao.observeAll().first()

        assertThat(first.map { it.username }).containsExactly("u1", "u2")
    }

    // ---- Issue #9: sort variants, recently-used, updateLastUsedAt -------

    @Test
    fun observeByUpdatedAtDesc_ordersNewestFirstWithLabelTiebreaker() = runTest {
        // Req 4.1(a), 4.2: updated_at DESC, label ASC tiebreaker.
        dao.insert(sample(packageName = "com.example.a", username = "u1", label = "Banana", updatedAt = 1L))
        dao.insert(sample(packageName = "com.example.b", username = "u2", label = "Apple", updatedAt = 5L))
        dao.insert(sample(packageName = "com.example.c", username = "u3", label = "Carrot", updatedAt = 5L))

        val emitted = dao.observeByUpdatedAtDesc().first()

        // Newest first; among the two updatedAt=5L rows, label ASC selects
        // Apple before Carrot. Banana (updatedAt=1L) trails.
        assertThat(emitted.map { it.label }).containsExactly("Apple", "Carrot", "Banana").inOrder()
    }

    @Test
    fun observeByLabelAsc_isCaseInsensitive() = runTest {
        // Req 4.1(b): label ASC COLLATE NOCASE.
        dao.insert(sample(packageName = "com.example.a", username = "u1", label = "banana"))
        dao.insert(sample(packageName = "com.example.b", username = "u2", label = "Apple"))
        dao.insert(sample(packageName = "com.example.c", username = "u3", label = "carrot"))

        val emitted = dao.observeByLabelAsc().first()

        assertThat(emitted.map { it.label }).containsExactly("Apple", "banana", "carrot").inOrder()
    }

    @Test
    fun observeByPackageAsc_isCaseInsensitive() = runTest {
        // Req 4.1(c): package_name ASC COLLATE NOCASE.
        dao.insert(sample(packageName = "com.example.Banana", username = "u1"))
        dao.insert(sample(packageName = "com.example.apple", username = "u2"))
        dao.insert(sample(packageName = "com.example.Carrot", username = "u3"))

        val emitted = dao.observeByPackageAsc().first()

        assertThat(emitted.map { it.packageName })
            .containsExactly("com.example.apple", "com.example.Banana", "com.example.Carrot")
            .inOrder()
    }

    @Test
    fun observeRecentlyUsed_excludesNullLastUsedAt() = runTest {
        // Req 3.1, 3.3, 3.4: never-used rows are excluded; result honours
        // the LIMIT.
        dao.insert(sample(packageName = "com.example.a", username = "never", lastUsedAt = null))
        dao.insert(sample(packageName = "com.example.b", username = "old", lastUsedAt = 10L))
        dao.insert(sample(packageName = "com.example.c", username = "newer", lastUsedAt = 20L))
        dao.insert(sample(packageName = "com.example.d", username = "newest", lastUsedAt = 30L))

        val emitted = dao.observeRecentlyUsed(limit = 5).first()

        assertThat(emitted.map { it.username }).containsExactly("newest", "newer", "old").inOrder()
    }

    @Test
    fun observeRecentlyUsed_respectsLimit() = runTest {
        // Req 3.1: top-N by lastUsedAt DESC.
        (1..7L).forEach { ts ->
            dao.insert(sample(packageName = "com.example.$ts", username = "u$ts", lastUsedAt = ts))
        }

        val emitted = dao.observeRecentlyUsed(limit = 5).first()

        assertThat(emitted).hasSize(5)
        assertThat(emitted.first().username).isEqualTo("u7") // newest
        assertThat(emitted.last().username).isEqualTo("u3")  // 5th newest
    }

    @Test
    fun observeRecentlyUsed_returnsEmpty_whenAllRowsAreNull() = runTest {
        // Req 3.4: 0 usable rows => empty flow value => carousel hides.
        dao.insert(sample(packageName = "com.example.a", username = "u1", lastUsedAt = null))
        dao.insert(sample(packageName = "com.example.b", username = "u2", lastUsedAt = null))

        val emitted = dao.observeRecentlyUsed(limit = 5).first()
        assertThat(emitted).isEmpty()
    }

    @Test
    fun updateLastUsedAt_setsTimestamp_onTargetRowOnly() = runTest {
        // Req 3.2.
        val targetId = dao.insert(sample(packageName = "com.example.target", username = "u1", lastUsedAt = null))
        val otherId = dao.insert(sample(packageName = "com.example.other", username = "u2", lastUsedAt = null))

        dao.updateLastUsedAt(id = targetId, timestamp = 5555L)

        assertThat(dao.findById(targetId)!!.lastUsedAt).isEqualTo(5555L)
        assertThat(dao.findById(otherId)!!.lastUsedAt).isNull()
    }

    @Test
    fun updateLastUsedAt_isSilent_whenIdMissing() = runTest {
        // Req 3.2: a race where the row was deleted between the unlock and
        // the markUsed update must not throw.
        dao.updateLastUsedAt(id = 9999L, timestamp = 1234L)
        // No exception -> success.
        assertThat(true).isTrue()
    }

    // ---- Issue #10: observeCount / observeLatestUpdatedAt / deleteAll ---

    @Test
    fun observeCount_emitsZero_onEmptyTable() = runTest {
        // Issue #10 Req 4.1.
        val emitted = dao.observeCount().first()
        assertThat(emitted).isEqualTo(0)
    }

    @Test
    fun observeCount_reflectsCurrentRowCount() = runTest {
        // Issue #10 Req 4.1: COUNT(*) tracks inserts.
        dao.insert(sample(packageName = "com.example.a", username = "u1"))
        dao.insert(sample(packageName = "com.example.b", username = "u2"))
        dao.insert(sample(packageName = "com.example.c", username = "u3"))

        val emitted = dao.observeCount().first()

        assertThat(emitted).isEqualTo(3)
    }

    @Test
    fun observeLatestUpdatedAt_emitsNull_onEmptyTable() = runTest {
        // Issue #10 Req 4.3: MAX(updated_at) over no rows is NULL, which
        // the UI maps to a "未登録" placeholder.
        val emitted = dao.observeLatestUpdatedAt().first()
        assertThat(emitted).isNull()
    }

    @Test
    fun observeLatestUpdatedAt_returnsMaxAcrossRows() = runTest {
        // Issue #10 Req 4.2.
        dao.insert(sample(packageName = "com.example.a", username = "u1", updatedAt = 100L))
        dao.insert(sample(packageName = "com.example.b", username = "u2", updatedAt = 500L))
        dao.insert(sample(packageName = "com.example.c", username = "u3", updatedAt = 300L))

        val emitted = dao.observeLatestUpdatedAt().first()

        assertThat(emitted).isEqualTo(500L)
    }

    @Test
    fun deleteAll_removesEveryRow() = runTest {
        // Issue #10 Req 7.5.
        dao.insert(sample(packageName = "com.example.a", username = "u1"))
        dao.insert(sample(packageName = "com.example.b", username = "u2"))

        dao.deleteAll()

        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.observeCount().first()).isEqualTo(0)
        assertThat(dao.observeLatestUpdatedAt().first()).isNull()
    }

    @Test
    fun deleteAll_isIdempotent_onEmptyTable() = runTest {
        // Issue #10 Req 7.7: clearing twice (e.g. a retry after a partial
        // failure) must not throw.
        dao.deleteAll()
        dao.deleteAll()
        assertThat(dao.observeCount().first()).isEqualTo(0)
    }

    private fun sample(
        packageName: String,
        username: String,
        updatedAt: Long = 0L,
        label: String = "Label-$username",
        lastUsedAt: Long? = null,
    ) = CredentialEntity(
        packageName = packageName,
        username = username,
        label = label,
        passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
        passwordIv = ByteArray(12) { 0x10.toByte() },
        signatureSha256 = ByteArray(32) { 0x20.toByte() },
        signatureCapturedAt = 1000L,
        createdAt = 0L,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
    )
}
