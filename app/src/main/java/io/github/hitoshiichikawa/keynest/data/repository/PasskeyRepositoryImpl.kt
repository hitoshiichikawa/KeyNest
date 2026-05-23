package io.github.hitoshiichikawa.keynest.data.repository

import androidx.room.withTransaction
import io.github.hitoshiichikawa.keynest.data.KeyNestDatabase
import io.github.hitoshiichikawa.keynest.data.dao.PasskeyDao
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import io.github.hitoshiichikawa.keynest.domain.model.DeletePasskeyResult
import io.github.hitoshiichikawa.keynest.domain.model.Passkey
import io.github.hitoshiichikawa.keynest.domain.model.SavePasskeyRequest
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import java.security.KeyStore
import java.security.KeyStoreException
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed implementation of [PasskeyRepository] aligned with the
 * shared design.md §6.1 contract (Issue #107 / parent #91).
 *
 * Responsibilities:
 *  - AES-GCM encrypts [SavePasskeyRequest.privateKey] before INSERT.
 *  - Owns the per-PassKey AndroidKeyStore wrapping-key alias
 *    `passkey_<credentialId>` (#91 決定 3 / design §6.2 / §7.1).
 *    The wrapping key is provisioned on first use by [KeystoreKeyProvider.getOrCreateKey]
 *    when the cipher initializes for the new alias.
 *  - Translates persistence entities to [Passkey] domain aggregates on
 *    every read so secret material ([PasskeyEntity.encryptedPrivateKey] /
 *    [PasskeyEntity.privateKeyIv] / [PasskeyEntity.keyAlias]) never
 *    escapes the data layer (NFR 2.2).
 *  - On [delete], removes the row first and then attempts to drop the
 *    AndroidKeyStore alias. A `KeyStoreException` during alias cleanup is
 *    surfaced as [DeletePasskeyResult.KeystoreCleanupFailed] rather than
 *    bubbled (design §7.4 / Req 1.6).
 *
 * **Plaintext private key lifetime**: this class does NOT wipe
 * `request.privateKey` after [save]. The wipe contract belongs to the
 * registration flow (`PasskeyCreateActivity`), which holds the ByteArray
 * and runs `fill(0)` in its `finally` block (NFR 1.3).
 *
 * Issue #100 additions (design §4.5):
 *  - [loadPrivateKey] decrypts `(privateKeyIv, encryptedPrivateKey)` back
 *    to plaintext PKCS#8. Returns `null` when the row is missing — caller
 *    must guard explicitly. The caller (`PasskeyAuthActivity`) wipes the
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

    override suspend fun findByCredentialId(credentialId: String): Passkey? =
        dao.findByCredentialId(credentialId)?.toDomain()

    override suspend fun findByRpIdAndUserHandle(
        rpId: String,
        userHandle: ByteArray,
    ): Passkey? = dao.findByRpIdAndUserHandle(rpId, userHandle)?.toDomain()

    override suspend fun listDiscoverableByRpId(rpId: String): List<Passkey> =
        dao.listDiscoverableByRpId(rpId).map { it.toDomain() }

    override suspend fun listAllByRpId(rpId: String): List<Passkey> =
        dao.listAllByRpId(rpId).map { it.toDomain() }

    override suspend fun incrementSignCount(credentialId: String, timestamp: Long) {
        dao.incrementSignCount(credentialId, timestamp)
    }

    /**
     * Issue #102: full-row update used by the PassKey detail UI to rename
     * [PasskeyEntity.displayName]. Pure delegate to [PasskeyDao.update]
     * (`@Update`) — no encryption, no Keystore alias touch.
     *
     * The caller is responsible for preserving every column other than the
     * one being changed (typically by `findByCredentialId(id).copy(displayName = trimmed)`).
     * See [PasskeyRepository.update] KDoc for the contract.
     */
    override suspend fun update(entity: PasskeyEntity) {
        dao.update(entity)
    }

    /**
     * Issue #102 §4.4 / §5.3: atomize "DB row delete → AndroidKeyStore
     * alias delete" so the DB row and the wrapping key are guaranteed to
     * be in one of two states only — **both deleted** or **both
     * untouched**. The previous implementation deleted the DB row first
     * and then attempted the alias cleanup outside any transaction, which
     * left the system in a "row gone, alias orphaned" state when the
     * KeyStore delete raised.
     *
     * Implementation:
     *  1. Open a Room transaction via [KeyNestDatabase.withTransaction].
     *  2. Inside the transaction, run [PasskeyDao.delete] then
     *     [KeyStore.deleteEntry] (guarded by [KeyStore.containsAlias] so
     *     a missing alias is a Success no-op, not a no-op-with-failure).
     *  3. When [KeyStore.deleteEntry] raises [KeyStoreException], the
     *     exception bubbles out of the `withTransaction { ... }` block,
     *     which prompts Room to roll back the DB row delete. The outer
     *     try-catch then converts the surfaced [KeyStoreException] to
     *     [DeletePasskeyResult.KeystoreCleanupFailed], keeping the
     *     historical return-shape (Requirement 6.4) intact for existing
     *     callers (e.g. the authentication ceremony's anomaly path).
     *  4. Any other exception (e.g. `SQLiteException`) is intentionally
     *     not caught here so it propagates to the caller after the
     *     transaction rolls back (design §9 risk 1 / Requirement 3.9).
     */
    override suspend fun delete(credentialId: String): DeletePasskeyResult {
        return try {
            database.withTransaction {
                dao.delete(credentialId)
                val keyStore = keyStoreLoader()
                val alias = aliasFor(credentialId)
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                }
            }
            DeletePasskeyResult.Success
        } catch (e: KeyStoreException) {
            DeletePasskeyResult.KeystoreCleanupFailed(e)
        }
    }

    // ---- Issue #100 (authentication ceremony) ---------------------------

    override suspend fun loadPrivateKey(credentialId: String): ByteArray? {
        val entity = dao.findByCredentialId(credentialId) ?: return null
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

    // ---- Issue #101 (Phase 4 of umbrella #89) -------------------------

    /**
     * Pure DAO delegate — Entity → DisplayModel mapping happens one layer
     * up in [io.github.hitoshiichikawa.keynest.domain.usecase.ListPasskeysUseCase]
     * so sensitive entity columns (`userHandle`, `encryptedPrivateKey`,
     * `privateKeyIv`, `keyAlias`, `signCount`) never reach the UI layer.
     */
    override fun listAll(): Flow<List<PasskeyEntity>> = dao.listAll()

    companion object {
        /**
         * Per-PassKey wrapping-key alias for the AndroidKeyStore (#91
         * 決定 3 / design §6.2 / §7.1). The literal `passkey_<credentialId>`
         * form is the spec contract; the legacy `keynest_passkey_*` prefix
         * used during early Issue #99 / #100 development is intentionally
         * dropped here because the feature has not yet shipped to
         * end-users (CredentialProviderService wiring is in place but no
         * release exposes PassKey registration yet). See impl-notes
         * "Reviewer round=2 引き継ぎ事項" for the explicit decision to
         * skip a data migration.
         */
        internal fun aliasFor(credentialId: String): String = "passkey_$credentialId"
    }
}

/**
 * Project the persistence-layer [PasskeyEntity] to a [Passkey] domain
 * aggregate (design §6.1). Drops `encryptedPrivateKey` / `privateKeyIv` /
 * `keyAlias` so secret material stays bounded to the data layer (NFR 2.2).
 *
 * Kept private at file scope because no caller outside this file should
 * need to perform the projection — repository methods return [Passkey]
 * directly.
 */
private fun PasskeyEntity.toDomain(): Passkey = Passkey(
    credentialId = credentialId,
    rpId = rpId,
    rpDisplayName = rpDisplayName,
    userHandle = userHandle,
    userName = userName,
    userDisplayName = userDisplayName,
    isDiscoverable = isDiscoverable,
    signCount = signCount,
    displayName = displayName,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
)
