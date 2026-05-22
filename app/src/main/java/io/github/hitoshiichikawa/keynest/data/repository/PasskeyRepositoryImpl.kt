package io.github.hitoshiichikawa.keynest.data.repository

import androidx.room.withTransaction
import io.github.hitoshiichikawa.keynest.data.KeyNestDatabase
import io.github.hitoshiichikawa.keynest.data.dao.PasskeyDao
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import java.security.KeyStore
import java.security.KeyStoreException

/**
 * Room-backed implementation of [PasskeyRepository] (Issue #99 — inlined
 * ahead of the umbrella `PasskeyRepository` Issue #107 merge so the
 * registration ceremony has a real persistence boundary to call into).
 *
 * Responsibilities:
 *  - AES-GCM encrypts [SavePasskeyRequest.privateKey] before INSERT.
 *  - Owns the per-PassKey AndroidKeyStore wrapping-key alias
 *    `keynest_passkey_<credentialId>` (req 決定 2 / #99 design §6.1).
 *    The wrapping key is provisioned on first use by [KeystoreKeyProvider.getOrCreateKey]
 *    when the cipher initializes for the new alias.
 *  - On [delete], removes the row first and then attempts to drop the
 *    AndroidKeyStore alias. A `KeyStoreException` during alias cleanup is
 *    surfaced as [DeletePasskeyResult.KeystoreCleanupFailed] rather than
 *    bubbled, so the caller can keep going (§5.3.2).
 *
 * **Plaintext private key lifetime**: this class does NOT wipe
 * `request.privateKey` after [save]. The wipe contract belongs to the
 * registration flow (`PasskeyCreateActivity`), which holds the ByteArray
 * and runs `fill(0)` in its `finally` block (NFR 1.3).
 *
 * Issue #100 additions (design §4.5 / §6):
 *  - [loadPrivateKey] decrypts `(privateKeyIv, encryptedPrivateKey)` back
 *    to plaintext PKCS#8. The caller (`PasskeyAuthActivity`) wipes the
 *    returned array in its `finally` block (NFR 1.1).
 *  - [signWithIncrement] wraps `dao.incrementSignCount` + `signer` in a
 *    single Room `withTransaction { ... }` so the signCount bump rolls
 *    back automatically when the signer throws (Option A / 決定 3 /
 *    design §6.1 案 C).
 *
 * Test seams: `cipherFactory` / `keyProviderFactory` / `keyStoreLoader`
 * are injectable so unit tests can substitute fake AES-GCM / Keystore
 * implementations without an Android runtime. `nowMillisProvider` lets
 * tests pin the `lastUsedAt` timestamp written by `incrementSignCount`.
 */
class PasskeyRepositoryImpl(
    private val dao: PasskeyDao,
    private val database: KeyNestDatabase,
    private val keyProviderFactory: (String) -> KeystoreKeyProvider = { alias -> KeystoreKeyProvider(keyAlias = alias) },
    private val cipherFactory: (KeystoreKeyProvider) -> AesGcmCipher = { provider -> AesGcmCipher(provider) },
    private val keyStoreLoader: () -> KeyStore = {
        KeyStore.getInstance(KeystoreKeyProvider.ANDROID_KEYSTORE).apply { load(null) }
    },
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) : PasskeyRepository {

    override suspend fun save(request: SavePasskeyRequest) {
        val alias = aliasFor(request.credentialId)
        val cipher = cipherFactory(keyProviderFactory(alias))
        val blob = cipher.encrypt(request.privateKey)
        val entity = PasskeyEntity(
            credentialId = request.credentialId,
            rpId = request.rpId,
            rpDisplayName = request.rpDisplayName,
            userHandle = request.userHandle,
            userName = request.userName,
            userDisplayName = request.userDisplayName,
            isDiscoverable = request.isDiscoverable,
            encryptedPrivateKey = blob.ciphertext,
            privateKeyIv = blob.iv,
            keyAlias = alias,
            signCount = request.signCount,
            displayName = request.displayName,
            createdAt = request.createdAt,
            lastUsedAt = null,
        )
        dao.insert(entity)
    }

    override suspend fun findByCredentialId(credentialId: String): PasskeyEntity? =
        dao.findByCredentialId(credentialId)

    override suspend fun findByRpIdAndUserHandle(
        rpId: String,
        userHandle: ByteArray,
    ): PasskeyEntity? = dao.findByRpIdAndUserHandle(rpId, userHandle)

    override suspend fun delete(credentialId: String): DeletePasskeyResult {
        dao.delete(credentialId)
        return try {
            val keyStore = keyStoreLoader()
            val alias = aliasFor(credentialId)
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
            DeletePasskeyResult.Success
        } catch (e: KeyStoreException) {
            DeletePasskeyResult.KeystoreCleanupFailed(e)
        }
    }

    // ---- Issue #100 (authentication ceremony) ---------------------------

    override suspend fun listDiscoverableByRpId(rpId: String): List<PasskeyEntity> =
        dao.listDiscoverableByRpId(rpId)

    override suspend fun loadPrivateKey(credentialId: String): ByteArray {
        val entity = dao.findByCredentialId(credentialId)
            ?: error("PasskeyEntity not found for credentialId=$credentialId")
        val alias = aliasFor(credentialId)
        val cipher = cipherFactory(keyProviderFactory(alias))
        val blob = EncryptedBlob(iv = entity.privateKeyIv, ciphertext = entity.encryptedPrivateKey)
        return cipher.decrypt(blob)
    }

    override suspend fun <T> signWithIncrement(
        credentialId: String,
        signer: suspend (newSignCount: Long) -> T,
    ): T = database.withTransaction {
        dao.incrementSignCount(credentialId, nowMillisProvider())
        val newSignCount = dao.findByCredentialId(credentialId)?.signCount
            ?: error("PasskeyEntity disappeared mid-transaction: credentialId=$credentialId")
        signer(newSignCount)
    }

    companion object {
        /**
         * Per-PassKey wrapping-key alias for the AndroidKeyStore (#99 決定 2 /
         * design §6.1). Issue #99 settles the spelling at
         * `keynest_passkey_<credentialId>` from the first row written, so no
         * data migration is required (the umbrella #107 — which would have
         * used `passkey_<credentialId>` — has not merged yet).
         */
        internal fun aliasFor(credentialId: String): String = "keynest_passkey_$credentialId"
    }
}
