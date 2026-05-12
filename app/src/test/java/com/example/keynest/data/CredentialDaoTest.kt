package com.example.keynest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.keynest.data.dao.CredentialDao
import com.example.keynest.data.entity.CredentialEntity
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

    private fun sample(
        packageName: String,
        username: String,
        updatedAt: Long = 0L,
    ) = CredentialEntity(
        packageName = packageName,
        username = username,
        label = "Label-$username",
        passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
        passwordIv = ByteArray(12) { 0x10.toByte() },
        signatureSha256 = ByteArray(32) { 0x20.toByte() },
        signatureCapturedAt = 1000L,
        createdAt = 0L,
        updatedAt = updatedAt,
    )
}
