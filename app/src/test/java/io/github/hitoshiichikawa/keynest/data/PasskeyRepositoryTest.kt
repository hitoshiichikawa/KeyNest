package io.github.hitoshiichikawa.keynest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.dao.PasskeyDao
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.data.repository.PasskeyRepositoryImpl
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [PasskeyRepositoryImpl] (Issue #99 inline + Issue #100
 * authentication ceremony additions).
 *
 * Robolectric is required so the new Issue #100 `signWithIncrement` /
 * `loadPrivateKey` tests can spin up an in-memory `KeyNestDatabase` —
 * `withTransaction { ... }` needs a real `RoomDatabase`. The legacy #99
 * mock-based tests continue to pass because the new `database` constructor
 * arg accepts a relaxed mock when transaction wiring is not exercised.
 *
 * Verifies:
 *  - the wrapping-key alias follows `keynest_passkey_<credentialId>` (T-08 +
 *    決定 2)
 *  - [PasskeyRepositoryImpl.save] encrypts via the injected cipher and never
 *    persists the plaintext private key on the entity
 *  - the lookup APIs forward to the DAO untouched
 *  - [PasskeyRepositoryImpl.delete] returns [DeletePasskeyResult.Success] on
 *    the happy path and [DeletePasskeyResult.KeystoreCleanupFailed] when the
 *    AndroidKeyStore delete raises a `KeyStoreException`
 *  - Issue #100: [PasskeyRepositoryImpl.listDiscoverableByRpId] delegates to
 *    the DAO; [PasskeyRepositoryImpl.loadPrivateKey] decrypts via the
 *    injected cipher; [PasskeyRepositoryImpl.signWithIncrement] runs the
 *    signer inside a Room transaction and rolls back when the signer throws.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PasskeyRepositoryTest {

    // ---- mock-based fixture (legacy #99 cases) -------------------------

    private val dao = mockk<PasskeyDao>(relaxed = true)
    private val database = mockk<KeyNestDatabase>(relaxed = true)
    private val provider = mockk<KeystoreKeyProvider>(relaxed = true)
    private val cipher = mockk<AesGcmCipher>()
    private val keyStore = mockk<KeyStore>()

    private val capturedAliases = mutableListOf<String>()
    private val keyProviderFactory: (String) -> KeystoreKeyProvider = { alias ->
        capturedAliases.add(alias)
        provider
    }
    private val cipherFactory: (KeystoreKeyProvider) -> AesGcmCipher = { cipher }
    private val keyStoreLoader: () -> KeyStore = { keyStore }

    private val repo = PasskeyRepositoryImpl(
        dao = dao,
        database = database,
        keyProviderFactory = keyProviderFactory,
        cipherFactory = cipherFactory,
        keyStoreLoader = keyStoreLoader,
    )

    @Test
    fun save_persistsKeyAlias_inEntity() = runTest {
        every { cipher.encrypt(any()) } returns
            EncryptedBlob(iv = ByteArray(12) { 0x55 }, ciphertext = ByteArray(48) { 0x66 })
        val entitySlot = slot<PasskeyEntity>()
        coEvery { dao.insert(capture(entitySlot)) } returns Unit

        repo.save(sampleSaveRequest(credentialId = "ABC"))

        assertThat(entitySlot.captured.keyAlias).isEqualTo("keynest_passkey_ABC")
        assertThat(capturedAliases).containsExactly("keynest_passkey_ABC")
    }

    @Test
    fun save_encryptsPrivateKey_and_stripsPlaintext_fromEntity() = runTest {
        val plaintext = ByteArray(64) { 0x11 }
        val ciphertext = ByteArray(80) { 0x22 }
        val iv = ByteArray(12) { 0x33 }
        every { cipher.encrypt(any()) } returns EncryptedBlob(iv = iv, ciphertext = ciphertext)
        val entitySlot = slot<PasskeyEntity>()
        val plainSlot = slot<ByteArray>()
        every { cipher.encrypt(capture(plainSlot)) } returns EncryptedBlob(iv = iv, ciphertext = ciphertext)
        coEvery { dao.insert(capture(entitySlot)) } returns Unit

        repo.save(sampleSaveRequest(credentialId = "XYZ", privateKey = plaintext))

        // The plaintext was passed to the cipher
        assertThat(plainSlot.captured).isEqualTo(plaintext)
        // The entity carries only the ciphertext + iv — never the plaintext bytes
        assertThat(entitySlot.captured.encryptedPrivateKey).isEqualTo(ciphertext)
        assertThat(entitySlot.captured.privateKeyIv).isEqualTo(iv)
        assertThat(entitySlot.captured.encryptedPrivateKey).isNotEqualTo(plaintext)
    }

    @Test
    fun findByCredentialId_returns_dao_value() = runTest {
        val entity = sampleEntity(credentialId = "lookup")
        coEvery { dao.findByCredentialId("lookup") } returns entity

        val result = repo.findByCredentialId("lookup")

        assertThat(result).isEqualTo(entity)
    }

    @Test
    fun findByRpIdAndUserHandle_returns_dao_value() = runTest {
        val entity = sampleEntity(credentialId = "byRp")
        val handle = ByteArray(16) { 0x77 }
        coEvery { dao.findByRpIdAndUserHandle("example.com", handle) } returns entity

        val result = repo.findByRpIdAndUserHandle("example.com", handle)

        assertThat(result).isEqualTo(entity)
    }

    @Test
    fun delete_returns_Success_onCleanSuccess() = runTest {
        coEvery { dao.delete("toDelete") } returns Unit
        every { keyStore.containsAlias("keynest_passkey_toDelete") } returns true
        every { keyStore.deleteEntry("keynest_passkey_toDelete") } returns Unit

        val result = repo.delete("toDelete")

        assertThat(result).isEqualTo(DeletePasskeyResult.Success)
        coVerify(exactly = 1) { dao.delete("toDelete") }
    }

    @Test
    fun delete_returns_Success_whenAliasAbsent() = runTest {
        coEvery { dao.delete("toDelete") } returns Unit
        every { keyStore.containsAlias("keynest_passkey_toDelete") } returns false

        val result = repo.delete("toDelete")

        assertThat(result).isEqualTo(DeletePasskeyResult.Success)
    }

    @Test
    fun delete_returns_KeystoreCleanupFailed_onKeyStoreException() = runTest {
        coEvery { dao.delete("toDelete") } returns Unit
        every { keyStore.containsAlias("keynest_passkey_toDelete") } returns true
        val boom = KeyStoreException("simulated cleanup failure")
        every { keyStore.deleteEntry("keynest_passkey_toDelete") } throws boom

        val result = repo.delete("toDelete")

        assertThat(result).isInstanceOf(DeletePasskeyResult.KeystoreCleanupFailed::class.java)
        assertThat((result as DeletePasskeyResult.KeystoreCleanupFailed).cause).isSameInstanceAs(boom)
    }

    // ---- Issue #100 additions (delegation / decrypt) -------------------

    @Test
    fun listDiscoverableByRpId_delegatesToDao() = runTest {
        val rows = listOf(sampleEntity("a"), sampleEntity("b"))
        coEvery { dao.listDiscoverableByRpId("example.com") } returns rows

        val result = repo.listDiscoverableByRpId("example.com")

        assertThat(result).isEqualTo(rows)
        coVerify(exactly = 1) { dao.listDiscoverableByRpId("example.com") }
    }

    @Test
    fun loadPrivateKey_returnsPlaintextPkcs8_thatRecoversEcPrivateKey() = runTest {
        // Generate a real P-256 keypair so we can round-trip PKCS#8 → ECPrivateKey
        // verify after the cipher decrypts. The cipher itself is mocked: it
        // returns the same PKCS#8 bytes it was asked to "decrypt".
        val gen = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        val realPkcs8 = gen.generateKeyPair().private.encoded!!
        val entity = sampleEntity("ec-key").copy(
            encryptedPrivateKey = ByteArray(48) { 0x66 },
            privateKeyIv = ByteArray(12) { 0x55 },
        )
        coEvery { dao.findByCredentialId("ec-key") } returns entity
        every {
            cipher.decrypt(
                EncryptedBlob(iv = entity.privateKeyIv, ciphertext = entity.encryptedPrivateKey),
            )
        } returns realPkcs8

        val plaintext = repo.loadPrivateKey("ec-key")

        // Round-trip: PKCS#8 plaintext must be parseable as a P-256 EC private key.
        val recovered = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(plaintext))
        assertThat(recovered.algorithm).isEqualTo("EC")
        assertThat(capturedAliases).contains("keynest_passkey_ec-key")
    }

    @Test
    fun loadPrivateKey_throwsIllegalStateException_whenEntityMissing() = runTest {
        coEvery { dao.findByCredentialId("missing") } returns null

        val ex = runCatching { repo.loadPrivateKey("missing") }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
    }

    // ---- Issue #100 signWithIncrement (Option A) -----------------------
    //
    // These tests need a real Room database so `withTransaction { ... }`
    // executes against an actual transaction context. The mock-based fixture
    // above cannot drive `withTransaction` because the extension function
    // dispatches through the database's internal coroutine context.

    private lateinit var roomDb: KeyNestDatabase
    private lateinit var roomDao: PasskeyDao
    private lateinit var roomRepo: PasskeyRepositoryImpl
    private val roomCipher = mockk<AesGcmCipher>(relaxed = true)
    private val roomProvider = mockk<KeystoreKeyProvider>(relaxed = true)
    private val roomKeyStore = mockk<KeyStore>(relaxed = true)
    private var roomNow: Long = 1_700_000_000_000L

    @Before
    fun setUpRoom() {
        roomDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KeyNestDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        roomDao = roomDb.passkeyDao()
        roomRepo = PasskeyRepositoryImpl(
            dao = roomDao,
            database = roomDb,
            keyProviderFactory = { roomProvider },
            cipherFactory = { roomCipher },
            keyStoreLoader = { roomKeyStore },
            nowMillisProvider = { roomNow },
        )
    }

    @After
    fun tearDownRoom() {
        if (::roomDb.isInitialized) roomDb.close()
    }

    @Test
    fun signWithIncrement_invokesSigner_andCommitsOnSuccess() = runTest {
        roomDao.insert(sampleEntity("c1").copy(signCount = 0L))

        val result = roomRepo.signWithIncrement("c1") { newSignCount ->
            "ok=$newSignCount"
        }

        assertThat(result).isEqualTo("ok=1")
        assertThat(roomDao.findByCredentialId("c1")?.signCount).isEqualTo(1L)
    }

    @Test
    fun signWithIncrement_signerReceivesNewSignCount() = runTest {
        roomDao.insert(sampleEntity("c1").copy(signCount = 41L))

        var receivedSignCount: Long = -1L
        roomRepo.signWithIncrement("c1") { newSignCount ->
            receivedSignCount = newSignCount
        }

        assertThat(receivedSignCount).isEqualTo(42L)
        assertThat(roomDao.findByCredentialId("c1")?.signCount).isEqualTo(42L)
    }

    @Test
    fun signWithIncrement_calledTwice_incrementsSignCountByTwo() = runTest {
        roomDao.insert(sampleEntity("c1").copy(signCount = 0L))

        roomRepo.signWithIncrement("c1") { /* commit */ }
        roomRepo.signWithIncrement("c1") { /* commit */ }

        assertThat(roomDao.findByCredentialId("c1")?.signCount).isEqualTo(2L)
    }

    @Test
    fun signWithIncrement_rollsBackSignCount_whenSignerThrows() = runTest {
        roomDao.insert(sampleEntity("c1").copy(signCount = 5L))

        val ex = runCatching {
            roomRepo.signWithIncrement<Unit>("c1") { throw RuntimeException("signer failed") }
        }.exceptionOrNull()

        // Room's withTransaction unwraps the original via the coroutine
        // boundary so identity is not preserved; class + message is the
        // contractually-stable check.
        assertThat(ex).isInstanceOf(RuntimeException::class.java)
        assertThat(ex?.message).isEqualTo("signer failed")
        // signCount must be rolled back to the pre-increment value.
        assertThat(roomDao.findByCredentialId("c1")?.signCount).isEqualTo(5L)
    }

    @Test
    fun signWithIncrement_rollsBackSignCount_whenSignerCoroutineCancelled() = runTest {
        roomDao.insert(sampleEntity("c1").copy(signCount = 9L))

        val ex = runCatching {
            roomRepo.signWithIncrement<Unit>("c1") {
                throw CancellationException("Activity destroyed mid-flight")
            }
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(CancellationException::class.java)
        // Activity onDestroy / process kill must not leave signCount bumped.
        assertThat(roomDao.findByCredentialId("c1")?.signCount).isEqualTo(9L)
    }

    @Test
    fun signWithIncrement_throwsIllegalStateException_whenEntityMissing() = runTest {
        // No row inserted for "missing".
        val ex = runCatching {
            roomRepo.signWithIncrement<Unit>("missing") { /* never called */ }
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
    }

    // ---- helpers --------------------------------------------------------

    private fun sampleSaveRequest(
        credentialId: String,
        privateKey: ByteArray = ByteArray(64) { 0x11 },
    ) = SavePasskeyRequest(
        credentialId = credentialId,
        rpId = "example.com",
        rpDisplayName = "Example",
        userHandle = ByteArray(16) { 0x77 },
        userName = "alice@example.com",
        userDisplayName = "Alice",
        isDiscoverable = true,
        privateKey = privateKey,
        signCount = 0L,
        displayName = null,
        createdAt = 1_700_000_000_000L,
    )

    private fun sampleEntity(credentialId: String) = PasskeyEntity(
        credentialId = credentialId,
        rpId = "example.com",
        rpDisplayName = "Example",
        userHandle = ByteArray(16) { 0x77 },
        userName = "alice@example.com",
        userDisplayName = "Alice",
        isDiscoverable = true,
        encryptedPrivateKey = ByteArray(48) { 0x66 },
        privateKeyIv = ByteArray(12) { 0x55 },
        keyAlias = "keynest_passkey_$credentialId",
        signCount = 0L,
        displayName = null,
        createdAt = 1_700_000_000_000L,
        lastUsedAt = null,
    )
}
