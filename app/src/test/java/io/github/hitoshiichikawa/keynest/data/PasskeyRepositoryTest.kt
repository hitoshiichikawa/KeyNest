package io.github.hitoshiichikawa.keynest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.dao.PasskeyDao
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.data.repository.PasskeyRepositoryImpl
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.Passkey
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
 * Unit tests for [PasskeyRepositoryImpl] aligned with the shared #91
 * design.md §6.1 contract (Issue #107 Reviewer round=2 reshape).
 *
 * Robolectric is required so the `signWithIncrement` / round-trip tests
 * can spin up an in-memory `KeyNestDatabase` — `withTransaction { ... }`
 * needs a real `RoomDatabase`. The legacy mock-based tests continue to
 * pass because the `database` constructor arg accepts a relaxed mock
 * when transaction wiring is not exercised.
 *
 * Verifies:
 *  - the wrapping-key alias follows `passkey_<credentialId>` (#91 決定 3 /
 *    design §6.2 / §7.1)
 *  - [PasskeyRepositoryImpl.save] encrypts via the injected cipher and
 *    never persists the plaintext private key on the entity
 *  - the lookup APIs project to [Passkey] domain aggregates (secret
 *    material — ciphertext / IV / alias — is dropped at the boundary,
 *    NFR 2.2)
 *  - [PasskeyRepositoryImpl.listAllByRpId] forwards to the DAO and
 *    projects to [Passkey]
 *  - [PasskeyRepositoryImpl.incrementSignCount] is exposed directly per
 *    design §6.1 and forwards to the DAO
 *  - [PasskeyRepositoryImpl.loadPrivateKey] returns `null` when the row
 *    is absent (no exception)
 *  - [PasskeyRepositoryImpl.delete] returns [DeletePasskeyResult.Success]
 *    on the happy path and [DeletePasskeyResult.KeystoreCleanupFailed]
 *    when the AndroidKeyStore delete raises a `KeyStoreException`
 *  - Issue #100: [PasskeyRepositoryImpl.listDiscoverableByRpId] projects
 *    to [Passkey]; [PasskeyRepositoryImpl.loadPrivateKey] decrypts via
 *    the injected cipher; [PasskeyRepositoryImpl.signWithIncrement] runs
 *    the signer inside a Room transaction and rolls back when the signer
 *    throws.
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

        assertThat(entitySlot.captured.keyAlias).isEqualTo("passkey_ABC")
        assertThat(capturedAliases).containsExactly("passkey_ABC")
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
    fun findByCredentialId_returns_domain_passkey() = runTest {
        val entity = sampleEntity(credentialId = "lookup")
        coEvery { dao.findByCredentialId("lookup") } returns entity

        val result = repo.findByCredentialId("lookup")

        // Result is a domain Passkey aggregate (NFR 2.2): metadata only,
        // no ciphertext / IV / alias surfacing across the boundary.
        assertThat(result).isNotNull()
        assertThat(result!!.credentialId).isEqualTo("lookup")
        assertThat(result.rpId).isEqualTo(entity.rpId)
        assertThat(result.userHandle).isEqualTo(entity.userHandle)
        assertThat(result.signCount).isEqualTo(entity.signCount)
    }

    @Test
    fun findByRpIdAndUserHandle_returns_domain_passkey() = runTest {
        val entity = sampleEntity(credentialId = "byRp")
        val handle = ByteArray(16) { 0x77 }
        coEvery { dao.findByRpIdAndUserHandle("example.com", handle) } returns entity

        val result = repo.findByRpIdAndUserHandle("example.com", handle)

        assertThat(result).isNotNull()
        assertThat(result!!.credentialId).isEqualTo("byRp")
        assertThat(result.rpId).isEqualTo("example.com")
        assertThat(result.userHandle).isEqualTo(handle)
    }

    @Test
    fun delete_returns_Success_onCleanSuccess() = runTest {
        coEvery { dao.delete("toDelete") } returns Unit
        every { keyStore.containsAlias("passkey_toDelete") } returns true
        every { keyStore.deleteEntry("passkey_toDelete") } returns Unit

        val result = repo.delete("toDelete")

        assertThat(result).isEqualTo(DeletePasskeyResult.Success)
        coVerify(exactly = 1) { dao.delete("toDelete") }
    }

    @Test
    fun delete_returns_Success_whenAliasAbsent() = runTest {
        coEvery { dao.delete("toDelete") } returns Unit
        every { keyStore.containsAlias("passkey_toDelete") } returns false

        val result = repo.delete("toDelete")

        assertThat(result).isEqualTo(DeletePasskeyResult.Success)
    }

    @Test
    fun delete_returns_KeystoreCleanupFailed_onKeyStoreException() = runTest {
        coEvery { dao.delete("toDelete") } returns Unit
        every { keyStore.containsAlias("passkey_toDelete") } returns true
        val boom = KeyStoreException("simulated cleanup failure")
        every { keyStore.deleteEntry("passkey_toDelete") } throws boom

        val result = repo.delete("toDelete")

        assertThat(result).isInstanceOf(DeletePasskeyResult.KeystoreCleanupFailed::class.java)
        assertThat((result as DeletePasskeyResult.KeystoreCleanupFailed).cause).isSameInstanceAs(boom)
    }

    // ---- Issue #100 additions (delegation / decrypt) -------------------

    @Test
    fun listDiscoverableByRpId_delegatesToDao_andProjectsToDomain() = runTest {
        val rows = listOf(sampleEntity("a"), sampleEntity("b"))
        coEvery { dao.listDiscoverableByRpId("example.com") } returns rows

        val result = repo.listDiscoverableByRpId("example.com")

        assertThat(result).hasSize(2)
        assertThat(result.map { it.credentialId }).containsExactly("a", "b").inOrder()
        // Confirm domain projection: ensure the result type is Passkey
        // (not PasskeyEntity) so secret material does not leak across
        // the boundary (NFR 2.2).
        result.forEach { passkey: Passkey ->
            assertThat(passkey.rpId).isEqualTo("example.com")
        }
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
        assertThat(plaintext).isNotNull()
        val recovered = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(plaintext))
        assertThat(recovered.algorithm).isEqualTo("EC")
        assertThat(capturedAliases).contains("passkey_ec-key")
    }

    @Test
    fun loadPrivateKey_returnsNull_whenEntityMissing() = runTest {
        coEvery { dao.findByCredentialId("missing") } returns null

        val result = repo.loadPrivateKey("missing")

        // design §6.1: nullable signature is the contract. The repository
        // must hand back null rather than throw IllegalStateException
        // when the underlying row does not exist (#107 Reviewer round=2
        // Finding 1 alignment).
        assertThat(result).isNull()
    }

    // ---- design §6.1 — direct expose of incrementSignCount -------------

    @Test
    fun incrementSignCount_delegatesToDao_withSuppliedTimestamp() = runTest {
        repo.incrementSignCount("c1", timestamp = 1_700_000_000_000L)

        coVerify(exactly = 1) { dao.incrementSignCount("c1", 1_700_000_000_000L) }
    }

    // ---- Issue #101 additions (listAll delegate) ----------------------

    @Test
    fun listAll_delegatesToDao() = runTest {
        val rows = listOf(sampleEntity("a"), sampleEntity("b"))
        every { dao.listAll() } returns flowOf(rows)

        val emitted = repo.listAll().first()

        assertThat(emitted).containsExactly(*rows.toTypedArray()).inOrder()
        verify(exactly = 1) { dao.listAll() }
    }

    @Test
    fun listAll_emitsEmptyList_whenDaoEmits_empty() = runTest {
        // R4.11 (b): empty table surfaces as an empty list (not null, not
        // exception). The UI relies on this to compute EmptyKind.Initial.
        every { dao.listAll() } returns flowOf(emptyList())

        val emitted = repo.listAll().first()

        assertThat(emitted).isEmpty()
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
    fun listAllByRpId_includesDiscoverableAndNonDiscoverable_andProjectsToDomain() = runTest {
        // design §6.1 / Req 3.5: listAllByRpId returns BOTH discoverable
        // and non-discoverable rows so the management UI can render the
        // full per-RP set. Discoverable filtering belongs to
        // listDiscoverableByRpId.
        roomDao.insert(sampleEntity("a", rpId = "example.com").copy(isDiscoverable = true))
        roomDao.insert(
            sampleEntity("b", rpId = "example.com").copy(
                isDiscoverable = false,
                userHandle = ByteArray(16) { 0x11 },
            ),
        )
        // Different RP — must NOT appear in example.com results.
        roomDao.insert(sampleEntity("c", rpId = "other.example"))

        val result = roomRepo.listAllByRpId("example.com")

        assertThat(result.map { it.credentialId }).containsExactly("a", "b")
        // Confirms the projection to domain Passkey (NFR 2.2): the
        // returned objects are Passkey instances, not PasskeyEntity,
        // so accessing ciphertext / IV is impossible by typing.
        result.forEach { passkey: Passkey ->
            assertThat(passkey.rpId).isEqualTo("example.com")
        }
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

    // ---- Issue #107 T-10 spec gap closes -------------------------------
    //
    // The cases below complete tasks.md §T-10 (design.md §9.4.2) by covering
    // the four scenarios the legacy #99 / #100 test fixture did not assert
    // on directly:
    //
    //   - save_thenLoadPrivateKey_roundTripsPlaintext
    //     (#9.4.2 case 1)
    //   - save_multipleCredentialIds_usesDistinctAliases
    //     (#9.4.2 case 7)
    //   - findByCredentialId_returnsNull_whenAbsent (#9.4.2 case 8)
    //   - loadPrivateKey_throwsAEADBadTagException_whenCiphertextTampered
    //     (#9.4.2 case 6 — req 5.10 / NFR 2.5: silent fail禁止)
    //   - save_thenDelete_removesRowFromDao_andTouchesKeystore
    //     (#9.4.2 case 4 — pre-existing tests verified the result type only)

    @Test
    fun save_thenLoadPrivateKey_roundTripsPlaintext() = runTest {
        // Spec §T-10 #9.4.2 case 1: full encrypt → decrypt loop preserves
        // the plaintext private key. We drive both ends with a real
        // in-memory DAO so the entity ciphertext/IV round-trip is end to end;
        // the cipher itself is replaced with an identity stand-in (cipherFactory
        // returns a cipher that echoes the plaintext) so the test does NOT
        // depend on AndroidKeyStore.
        val plaintext = ByteArray(64) { (it * 17).toByte() }
        val identityCipher = object : AesGcmCipher(roomProvider) {
            override fun encrypt(plaintext: ByteArray): EncryptedBlob =
                EncryptedBlob(iv = ByteArray(12) { 0x42 }, ciphertext = plaintext.copyOf())
            override fun decrypt(blob: EncryptedBlob): ByteArray = blob.ciphertext.copyOf()
        }
        val rtRepo = PasskeyRepositoryImpl(
            dao = roomDao,
            database = roomDb,
            keyProviderFactory = { roomProvider },
            cipherFactory = { identityCipher },
            keyStoreLoader = { roomKeyStore },
            nowMillisProvider = { roomNow },
        )

        rtRepo.save(sampleSaveRequest(credentialId = "rt-1", privateKey = plaintext))
        val decrypted = rtRepo.loadPrivateKey("rt-1")

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun save_multipleCredentialIds_usesDistinctAliases() = runTest {
        // Spec §T-10 #9.4.2 case 7: every save must request a fresh
        // KeystoreKeyProvider keyed by its own alias. We collect the
        // alias strings via a real factory wrapper and assert two
        // independent saves produce two distinct entries.
        val aliasLog = mutableListOf<String>()
        val multiRepo = PasskeyRepositoryImpl(
            dao = roomDao,
            database = roomDb,
            keyProviderFactory = { alias ->
                aliasLog += alias
                roomProvider
            },
            cipherFactory = { roomCipher },
            keyStoreLoader = { roomKeyStore },
            nowMillisProvider = { roomNow },
        )
        every { roomCipher.encrypt(any()) } returns
            EncryptedBlob(iv = ByteArray(12) { 0x00 }, ciphertext = ByteArray(48) { 0x00 })

        multiRepo.save(sampleSaveRequest(credentialId = "id-A"))
        multiRepo.save(
            // Different userHandle so the (rpId, userHandle) UNIQUE index
            // does not fire on the second insert.
            sampleSaveRequest(credentialId = "id-B").copy(
                userHandle = ByteArray(16) { 0x11 },
            ),
        )

        assertThat(aliasLog).containsExactly(
            "passkey_id-A",
            "passkey_id-B",
        ).inOrder()
        // And the persisted rows carry the matching aliases:
        assertThat(roomDao.findByCredentialId("id-A")?.keyAlias)
            .isEqualTo("passkey_id-A")
        assertThat(roomDao.findByCredentialId("id-B")?.keyAlias)
            .isEqualTo("passkey_id-B")
    }

    @Test
    fun findByCredentialId_returnsNull_whenAbsent() = runTest {
        // Spec §T-10 #9.4.2 case 8: lookup miss must surface as null, not
        // an exception. The mock-based fixture already verifies the
        // happy path; this gap covers the negative branch through the real
        // DAO so the contract holds end to end.
        val result = roomRepo.findByCredentialId("does-not-exist")

        assertThat(result).isNull()
    }

    @Test
    fun loadPrivateKey_propagatesAeadBadTag_whenCiphertextTampered() = runTest {
        // Spec §T-10 #9.4.2 case 6 / req 5.10: the cipher's auth-tag
        // verification failure MUST propagate to the caller (silent fail
        // returning null is forbidden — design §6.4 / NFR 2.5).
        //
        // We do not exercise the real AEADBadTagException here because the
        // identity-cipher used elsewhere in this file is intentionally not
        // tied to AndroidKeyStore; instead we mock the cipher.decrypt() call
        // to throw the exact JCE exception the production stack would emit
        // when a tampered ciphertext fails GCM auth.
        roomDao.insert(sampleEntity("tampered"))
        val bad = javax.crypto.AEADBadTagException("tag mismatch")
        every { roomCipher.decrypt(any()) } throws bad

        val ex = runCatching { roomRepo.loadPrivateKey("tampered") }.exceptionOrNull()

        assertThat(ex).isInstanceOf(javax.crypto.AEADBadTagException::class.java)
        assertThat(ex).isSameInstanceAs(bad)
    }

    @Test
    fun delete_removesRow_fromUnderlyingDao_andTouchesKeystoreAlias() = runTest {
        // Spec §T-10 #9.4.2 case 4: pre-existing mock-based tests only
        // verified the DeletePasskeyResult; this gap proves the row really
        // disappears from the DAO afterwards and that the keystore alias
        // delete is attempted with the passkey_<id> alias.
        val deletedAliases = mutableListOf<String>()
        val mockKs = mockk<KeyStore>()
        every { mockKs.containsAlias(any()) } returns true
        every { mockKs.deleteEntry(any()) } answers {
            deletedAliases += firstArg<String>()
            Unit
        }
        val delRepo = PasskeyRepositoryImpl(
            dao = roomDao,
            database = roomDb,
            keyProviderFactory = { roomProvider },
            cipherFactory = { roomCipher },
            keyStoreLoader = { mockKs },
            nowMillisProvider = { roomNow },
        )
        roomDao.insert(sampleEntity("to-drop"))
        assertThat(roomDao.findByCredentialId("to-drop")).isNotNull()

        val result = delRepo.delete("to-drop")

        assertThat(result).isEqualTo(DeletePasskeyResult.Success)
        assertThat(roomDao.findByCredentialId("to-drop")).isNull()
        assertThat(deletedAliases).containsExactly("passkey_to-drop")
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

    private fun sampleEntity(
        credentialId: String,
        rpId: String = "example.com",
    ) = PasskeyEntity(
        credentialId = credentialId,
        rpId = rpId,
        rpDisplayName = "Example",
        userHandle = ByteArray(16) { 0x77 },
        userName = "alice@example.com",
        userDisplayName = "Alice",
        isDiscoverable = true,
        encryptedPrivateKey = ByteArray(48) { 0x66 },
        privateKeyIv = ByteArray(12) { 0x55 },
        keyAlias = "passkey_$credentialId",
        signCount = 0L,
        displayName = null,
        createdAt = 1_700_000_000_000L,
        lastUsedAt = null,
    )
}
