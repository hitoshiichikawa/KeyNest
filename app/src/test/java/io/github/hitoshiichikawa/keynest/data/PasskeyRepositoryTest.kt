package io.github.hitoshiichikawa.keynest.data

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
import java.security.KeyStore
import java.security.KeyStoreException
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [PasskeyRepositoryImpl] (Issue #99 — inlined ahead of #107).
 *
 * Verifies:
 *  - the wrapping-key alias follows `keynest_passkey_<credentialId>` (T-08 +
 *    決定 2)
 *  - [save] encrypts via the injected cipher and never persists the plaintext
 *    private key on the entity
 *  - the lookup APIs forward to the DAO untouched
 *  - [delete] returns [DeletePasskeyResult.Success] on the happy path and
 *    [DeletePasskeyResult.KeystoreCleanupFailed] when the AndroidKeyStore
 *    delete raises a `KeyStoreException`
 *
 * Pure JVM (no Robolectric): the AndroidKeyStore + AesGcmCipher are replaced
 * via the constructor's `keyProviderFactory` / `cipherFactory` /
 * `keyStoreLoader` seams.
 */
class PasskeyRepositoryTest {

    private val dao = mockk<PasskeyDao>(relaxed = true)
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
