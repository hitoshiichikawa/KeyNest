package io.github.hitoshiichikawa.keynest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hitoshiichikawa.keynest.data.repository.CredentialRepositoryImpl
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder
import io.github.hitoshiichikawa.keynest.domain.model.DuplicateFailure
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Round-trip tests for [CredentialRepositoryImpl]. Backs Req 1.1, 1.4, 1.5,
 * 2.1, 2.2, 2.3.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CredentialRepositoryImplTest {

    private lateinit var db: KeyNestDatabase
    private lateinit var repo: CredentialRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeyNestDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = CredentialRepositoryImpl(db.credentialDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun save_thenFindById_roundTripsRecord() = runTest {
        val toSave = sample(packageName = "com.example.target", username = "alice")

        val id = repo.save(toSave)
        val found = repo.findById(id)

        assertThat(id.value).isGreaterThan(0L)
        assertThat(found).isNotNull()
        assertThat(found!!.packageName).isEqualTo("com.example.target")
        assertThat(found.username).isEqualTo("alice")
        assertThat(found.passwordCiphertext).isEqualTo(toSave.passwordCiphertext)
        assertThat(found.passwordIv).isEqualTo(toSave.passwordIv)
        assertThat(found.signatureSha256).isEqualTo(toSave.signatureSha256)
    }

    @Test
    fun findByPackage_returnsAllMatchingRecords() = runTest {
        // Req 1.4
        repo.save(sample(packageName = "com.example.target", username = "alice"))
        repo.save(sample(packageName = "com.example.target", username = "bob"))
        repo.save(sample(packageName = "com.example.other", username = "carol"))

        val list = repo.findByPackage("com.example.target")
        assertThat(list).hasSize(2)
        assertThat(list.map { it.username }).containsExactly("alice", "bob")
    }

    @Test
    fun update_persistsModifiedFields_andRefreshedSignature() = runTest {
        // Req 1.5, 2.3
        val original = sample(packageName = "com.example.target", username = "alice")
        val id = repo.save(original)
        val refreshedSig = SigningHash(ByteArray(32) { 0x55.toByte() })

        val mutated = original.copy(
            id = id,
            username = "alice-new",
            signatureSha256 = refreshedSig,
            signatureCapturedAt = 9999L,
            updatedAt = 9999L,
        )
        repo.update(mutated)

        val found = repo.findById(id)!!
        assertThat(found.username).isEqualTo("alice-new")
        assertThat(found.signatureSha256).isEqualTo(refreshedSig)
        assertThat(found.signatureCapturedAt).isEqualTo(9999L)
    }

    @Test
    fun delete_removesRecord() = runTest {
        // Req 1.5
        val id = repo.save(sample(packageName = "com.example.target", username = "alice"))
        repo.delete(id)
        assertThat(repo.findById(id)).isNull()
    }

    @Test
    fun delete_isNoOp_forUnknownId() = runTest {
        // Should not throw on missing id
        repo.delete(CredentialId(99999L))
        assertThat(true).isTrue()
    }

    @Test
    fun findByPackage_returnsEmpty_whenNoMatch() = runTest {
        repo.save(sample(packageName = "com.example.target", username = "alice"))
        assertThat(repo.findByPackage("com.example.unknown")).isEmpty()
    }

    @Test
    fun nullableSignature_persistsAsNull() = runTest {
        // Req 2.2
        val toSave = sample(packageName = "com.example.target", username = "alice").copy(
            signatureSha256 = null,
            signatureCapturedAt = null,
        )
        val id = repo.save(toSave)
        val found = repo.findById(id)!!
        assertThat(found.signatureSha256).isNull()
        assertThat(found.signatureCapturedAt).isNull()
    }

    @Test
    fun observeAll_excludesEncryptedBytes_byTypeContract() = runTest {
        // The domain Credential type does not carry passwordCiphertext / iv,
        // so any caller of observeAll cannot accidentally leak them. This is
        // a structural check enforced by the type system; we still smoke test
        // that the flow yields the expected count.
        repo.save(sample(packageName = "com.example.a", username = "u1"))
        repo.save(sample(packageName = "com.example.b", username = "u2"))

        val emitted = repo.observeAll().first()

        assertThat(emitted).hasSize(2)
        assertThat(emitted.map { it.username }).containsExactly("u1", "u2")
    }

    // ---- Issue #9 ------------------------------------------------------

    @Test
    fun observeBySort_labelAsc_sortsCaseInsensitive() = runTest {
        // Issue #9 Req 4.1(b).
        repo.save(sample("com.example.a", "u1", label = "banana"))
        repo.save(sample("com.example.b", "u2", label = "Apple"))

        val emitted = repo.observeBySort(CredentialSortOrder.LabelAsc).first()

        assertThat(emitted.map { it.label }).containsExactly("Apple", "banana").inOrder()
    }

    @Test
    fun observeBySort_packageAsc_sortsCaseInsensitive() = runTest {
        // Issue #9 Req 4.1(c).
        repo.save(sample("com.example.Banana", "u1"))
        repo.save(sample("com.example.apple", "u2"))

        val emitted = repo.observeBySort(CredentialSortOrder.PackageAsc).first()

        assertThat(emitted.map { it.packageName })
            .containsExactly("com.example.apple", "com.example.Banana")
            .inOrder()
    }

    @Test
    fun observeRecentlyUsed_returnsTopNByLastUsedAt() = runTest {
        // Issue #9 Req 3.1, 3.3, 3.4.
        val id1 = repo.save(sample("com.example.a", "u1"))
        val id2 = repo.save(sample("com.example.b", "u2"))
        repo.save(sample("com.example.c", "u3")) // never used

        repo.markUsed(id1, 100L)
        repo.markUsed(id2, 200L)

        val emitted = repo.observeRecentlyUsed(limit = 5).first()

        assertThat(emitted.map { it.username }).containsExactly("u2", "u1").inOrder()
    }

    @Test
    fun markUsed_setsLastUsedAt_onTargetRow() = runTest {
        // Issue #9 Req 3.2.
        val id = repo.save(sample("com.example.target", "alice"))
        repo.markUsed(id, timestamp = 5555L)

        assertThat(repo.findById(id)!!.lastUsedAt).isEqualTo(5555L)
    }

    @Test
    fun markUsed_isSilent_forMissingId() = runTest {
        // Race between unlock-flow markUsed and a delete: must not throw.
        repo.markUsed(CredentialId(9999L), timestamp = 1L)
        assertThat(true).isTrue()
    }

    @Test
    fun duplicate_copiesAllFields_andResetsTimestamps() = runTest {
        // Issue #9 Req 5.3, 5.4, NFR 1.4.
        val sourceId = repo.save(
            sample("com.example.target", "alice").copy(createdAt = 100L, updatedAt = 200L),
        )
        repo.markUsed(sourceId, 300L)
        val source = repo.findById(sourceId)!!

        val result = repo.duplicate(sourceId, timestamp = 7777L)

        assertThat(result.isSuccess).isTrue()
        val newId = result.getOrThrow()
        assertThat(newId.value).isNotEqualTo(sourceId.value)
        val copy = repo.findById(newId)!!
        // Inherited fields
        assertThat(copy.packageName).isEqualTo(source.packageName)
        assertThat(copy.username).isEqualTo(source.username)
        assertThat(copy.label).isEqualTo(source.label)
        assertThat(copy.passwordCiphertext).isEqualTo(source.passwordCiphertext)
        assertThat(copy.passwordIv).isEqualTo(source.passwordIv)
        assertThat(copy.signatureSha256).isEqualTo(source.signatureSha256)
        assertThat(copy.signatureCapturedAt).isEqualTo(source.signatureCapturedAt)
        // Reset fields
        assertThat(copy.createdAt).isEqualTo(7777L)
        assertThat(copy.updatedAt).isEqualTo(7777L)
        assertThat(copy.lastUsedAt).isNull()
    }

    @Test
    fun duplicate_doesNotMutateSource() = runTest {
        // Issue #9 Req 5.4.
        val sourceId = repo.save(
            sample("com.example.target", "alice").copy(createdAt = 100L, updatedAt = 200L),
        )
        val before = repo.findById(sourceId)!!

        repo.duplicate(sourceId, timestamp = 7777L)

        val after = repo.findById(sourceId)!!
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun duplicate_returnsNotFound_forUnknownSourceId() = runTest {
        // Issue #9 Req 5.3 failure path: race against delete.
        val result = repo.duplicate(CredentialId(9999L), timestamp = 1L)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(DuplicateFailure.NotFound::class.java)
    }

    private fun sample(
        packageName: String,
        username: String,
        label: String = "Label-$username",
    ) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = packageName,
        username = username,
        label = label,
        passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
        passwordIv = ByteArray(12) { 0x10.toByte() },
        signatureSha256 = SigningHash(ByteArray(32) { 0x20.toByte() }),
        signatureCapturedAt = 1000L,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
