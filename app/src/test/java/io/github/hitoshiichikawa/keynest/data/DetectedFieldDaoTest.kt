package io.github.hitoshiichikawa.keynest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.dao.DetectedFieldDao
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldEntity
import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room DAO behaviour for [DetectedFieldDao]. Issue #67 Phase 2
 * (design.md §9.2 / requirements Req 5.2).
 *
 * Uses Robolectric (sdk = 33) so Room can build a real in-memory SQLite
 * DB on the host JVM. Same idiom as the existing
 * [io.github.hitoshiichikawa.keynest.data.CredentialDaoTest].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DetectedFieldDaoTest {

    private lateinit var db: KeyNestDatabase
    private lateinit var dao: DetectedFieldDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeyNestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.detectedFieldDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertWithLruCap_insertsNewRow() = runTest {
        // Sanity: the simple INSERT path writes the row and the
        // observe query surfaces it.
        dao.upsertWithLruCap(entity("pkg.a", "loginEmail", DetectedFieldSource.ResourceId, 100L))

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 10).first()

        assertThat(emitted).hasSize(1)
        assertThat(emitted.single().fieldKey).isEqualTo("loginEmail")
        assertThat(emitted.single().lastDetectedAt).isEqualTo(100L)
    }

    @Test
    fun upsertWithLruCap_updatesExistingRow_byCompositeKey() = runTest {
        // Same (packageName, fieldKey, source) → REPLACE keeps row
        // count at 1 and updates lastDetectedAt to the newer value.
        dao.upsertWithLruCap(entity("pkg.a", "loginEmail", DetectedFieldSource.ResourceId, 100L))
        dao.upsertWithLruCap(entity("pkg.a", "loginEmail", DetectedFieldSource.ResourceId, 200L))

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 10).first()

        assertThat(emitted).hasSize(1)
        assertThat(emitted.single().lastDetectedAt).isEqualTo(200L)
    }

    @Test
    fun upsertWithLruCap_distinctSourcesForSameKey_keepBothRows() = runTest {
        // The composite PK includes `source`, so the same fieldKey
        // observed through autofillHints AND through resourceId
        // surfaces as two rows (the repository / ViewModel layer
        // dedupes by normalised form for the UI).
        dao.upsertWithLruCap(entity("pkg.a", "username", DetectedFieldSource.AutofillHints, 100L))
        dao.upsertWithLruCap(entity("pkg.a", "username", DetectedFieldSource.ResourceId, 200L))

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 10).first()

        assertThat(emitted).hasSize(2)
        assertThat(emitted.map { it.source })
            .containsExactly("resourceId", "autofillHints")
            .inOrder()
    }

    @Test
    fun observeRecentByPackage_ordersByLastDetectedAtDesc() = runTest {
        dao.upsertWithLruCap(entity("pkg.a", "early", DetectedFieldSource.Hint, 100L))
        dao.upsertWithLruCap(entity("pkg.a", "middle", DetectedFieldSource.Hint, 200L))
        dao.upsertWithLruCap(entity("pkg.a", "latest", DetectedFieldSource.Hint, 300L))

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 10).first()

        assertThat(emitted.map { it.fieldKey })
            .containsExactly("latest", "middle", "early")
            .inOrder()
    }

    @Test
    fun observeRecentByPackage_respectsLimit() = runTest {
        for (i in 1..7) {
            dao.upsertWithLruCap(
                entity("pkg.a", "key$i", DetectedFieldSource.Hint, i.toLong() * 10),
            )
        }

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 3).first()

        assertThat(emitted).hasSize(3)
        // The three newest rows by lastDetectedAt.
        assertThat(emitted.map { it.fieldKey }).containsExactly("key7", "key6", "key5").inOrder()
    }

    @Test
    fun observeRecentByPackage_excludesOtherPackages() = runTest {
        dao.upsertWithLruCap(entity("pkg.a", "alpha", DetectedFieldSource.Hint, 100L))
        dao.upsertWithLruCap(entity("pkg.b", "beta", DetectedFieldSource.Hint, 200L))

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 10).first()

        assertThat(emitted.map { it.fieldKey }).containsExactly("alpha")
    }

    @Test
    fun upsertWithLruCap_keepsCapacity_andDropsOldest() = runTest {
        // Drive cap=3 to exercise the LRU prune. Insert 4 rows with
        // strictly increasing timestamps; the oldest must be dropped.
        dao.upsertWithLruCap(entity("pkg.a", "k1", DetectedFieldSource.Hint, 10L), capacity = 3)
        dao.upsertWithLruCap(entity("pkg.a", "k2", DetectedFieldSource.Hint, 20L), capacity = 3)
        dao.upsertWithLruCap(entity("pkg.a", "k3", DetectedFieldSource.Hint, 30L), capacity = 3)
        dao.upsertWithLruCap(entity("pkg.a", "k4", DetectedFieldSource.Hint, 40L), capacity = 3)

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 10).first()

        assertThat(emitted).hasSize(3)
        assertThat(emitted.map { it.fieldKey }).containsExactly("k4", "k3", "k2").inOrder()
        // k1 (lastDetectedAt = 10) was the eviction.
        assertThat(emitted.map { it.fieldKey }).doesNotContain("k1")
    }

    @Test
    fun upsertWithLruCap_doesNotAffectOtherPackages() = runTest {
        // Drive cap=2 for pkg.a, but pkg.b's rows must remain intact.
        for (i in 1..4) {
            dao.upsertWithLruCap(
                entity("pkg.a", "a$i", DetectedFieldSource.Hint, i.toLong() * 10),
                capacity = 2,
            )
        }
        dao.upsertWithLruCap(entity("pkg.b", "b1", DetectedFieldSource.Hint, 100L), capacity = 2)
        dao.upsertWithLruCap(entity("pkg.b", "b2", DetectedFieldSource.Hint, 200L), capacity = 2)

        assertThat(dao.observeRecentByPackage("pkg.a", limit = 10).first()).hasSize(2)
        // pkg.b has only 2 rows so the cap is irrelevant; both survive.
        assertThat(dao.observeRecentByPackage("pkg.b", limit = 10).first().map { it.fieldKey })
            .containsExactly("b2", "b1").inOrder()
    }

    @Test
    fun upsertWithLruCap_replaceOnDuplicate_doesNotShrinkBelowCap() = runTest {
        // Replacing an existing PK keeps row count constant; the LRU
        // prune step must not over-delete when count == capacity.
        dao.upsertWithLruCap(entity("pkg.a", "k1", DetectedFieldSource.Hint, 10L), capacity = 2)
        dao.upsertWithLruCap(entity("pkg.a", "k2", DetectedFieldSource.Hint, 20L), capacity = 2)
        // Replace k1 with a newer timestamp.
        dao.upsertWithLruCap(entity("pkg.a", "k1", DetectedFieldSource.Hint, 30L), capacity = 2)

        val emitted = dao.observeRecentByPackage("pkg.a", limit = 10).first()

        assertThat(emitted).hasSize(2)
        assertThat(emitted.map { it.fieldKey }).containsExactly("k1", "k2").inOrder()
    }

    @Test
    fun deleteByPackage_removesOnlyMatching() = runTest {
        dao.upsertWithLruCap(entity("pkg.a", "alpha", DetectedFieldSource.Hint, 100L))
        dao.upsertWithLruCap(entity("pkg.b", "beta", DetectedFieldSource.Hint, 200L))

        dao.deleteByPackage("pkg.a")

        assertThat(dao.observeRecentByPackage("pkg.a", limit = 10).first()).isEmpty()
        assertThat(dao.observeRecentByPackage("pkg.b", limit = 10).first().map { it.fieldKey })
            .containsExactly("beta")
    }

    @Test
    fun deleteAll_emptiesTable() = runTest {
        dao.upsertWithLruCap(entity("pkg.a", "alpha", DetectedFieldSource.Hint, 100L))
        dao.upsertWithLruCap(entity("pkg.b", "beta", DetectedFieldSource.Hint, 200L))

        dao.deleteAll()

        assertThat(dao.observeRecentByPackage("pkg.a", limit = 10).first()).isEmpty()
        assertThat(dao.observeRecentByPackage("pkg.b", limit = 10).first()).isEmpty()
    }

    @Test
    fun deleteAll_idempotentOnEmptyTable() = runTest {
        // Vault clear retry path: a second deleteAll() on an empty
        // table must not throw.
        dao.deleteAll()
        dao.deleteAll()
        assertThat(dao.observeRecentByPackage("pkg.a", limit = 10).first()).isEmpty()
    }

    @Test
    fun countByPackage_returnsZero_whenNoRows() = runTest {
        assertThat(dao.countByPackage("pkg.a")).isEqualTo(0)
    }

    @Test
    fun countByPackage_reflectsRowCount() = runTest {
        dao.upsertWithLruCap(entity("pkg.a", "k1", DetectedFieldSource.Hint, 10L))
        dao.upsertWithLruCap(entity("pkg.a", "k2", DetectedFieldSource.Hint, 20L))
        assertThat(dao.countByPackage("pkg.a")).isEqualTo(2)
        assertThat(dao.countByPackage("pkg.b")).isEqualTo(0)
    }

    @Test
    fun defaultCapacityIs50() = runTest {
        // Pin the requirements §4 Q3 constant. Insert 51 distinct
        // rows; only the newest 50 must remain.
        for (i in 1..51) {
            dao.upsertWithLruCap(
                entity("pkg.a", "k$i", DetectedFieldSource.Hint, i.toLong()),
            )
        }
        val emitted = dao.observeRecentByPackage("pkg.a", limit = 100).first()
        assertThat(emitted).hasSize(DetectedFieldDao.LRU_CAPACITY)
        assertThat(emitted.map { it.fieldKey }).doesNotContain("k1") // oldest evicted
        assertThat(emitted.map { it.fieldKey }).contains("k51") // newest present
    }

    // ---- helpers --------------------------------------------------------

    private fun entity(
        pkg: String,
        key: String,
        source: DetectedFieldSource,
        ts: Long,
    ) = DetectedFieldEntity(
        packageName = pkg,
        fieldKey = key,
        source = source.storageKey,
        lastDetectedAt = ts,
    )
}
