package io.github.hitoshiichikawa.keynest.ui.list

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity
import org.junit.Test

/**
 * Unit tests for [PasskeyDisplayModel] (Issue #101 / Phase 4 of umbrella
 * #89). The single most important assertion here is that the data class
 * does NOT declare any of the five sensitive entity columns
 * ([NFR 2.1]):
 *  - userHandle (BLOB, opaque WebAuthn user identifier)
 *  - encryptedPrivateKey (AES-GCM ciphertext)
 *  - privateKeyIv (12-byte GCM IV)
 *  - keyAlias (AndroidKeyStore alias)
 *  - signCount (WebAuthn assertion counter)
 *
 * That is enforced both as a hand-rolled field check and via reflection
 * so a future code-gen / refactor cannot accidentally promote one of
 * those names back onto the projection.
 */
class PasskeyDisplayModelTest {

    @Test
    fun fromEntity_copiesAllNineNonSensitiveFields() {
        val entity = sampleEntity(
            credentialId = "cid-1",
            rpId = "example.com",
            rpDisplayName = "Example",
            userName = "alice@example.com",
            userDisplayName = "Alice",
            displayName = "My favourite passkey",
            isDiscoverable = false,
            createdAt = 1_700_000_000_000L,
            lastUsedAt = 1_700_000_100_000L,
        )

        val dm = PasskeyDisplayModel.fromEntity(entity)

        assertThat(dm.credentialId).isEqualTo("cid-1")
        assertThat(dm.rpId).isEqualTo("example.com")
        assertThat(dm.rpDisplayName).isEqualTo("Example")
        assertThat(dm.userName).isEqualTo("alice@example.com")
        assertThat(dm.userDisplayName).isEqualTo("Alice")
        assertThat(dm.displayName).isEqualTo("My favourite passkey")
        assertThat(dm.isDiscoverable).isFalse()
        assertThat(dm.createdAt).isEqualTo(1_700_000_000_000L)
        assertThat(dm.lastUsedAt).isEqualTo(1_700_000_100_000L)
    }

    @Test
    fun fromEntity_preservesIsDiscoverableTrue() {
        val dm = PasskeyDisplayModel.fromEntity(sampleEntity("c", isDiscoverable = true))
        assertThat(dm.isDiscoverable).isTrue()
    }

    @Test
    fun fromEntity_preservesNullableFields_whenEntityFieldsAreNull() {
        val entity = sampleEntity(
            credentialId = "c-null",
            rpDisplayName = null,
            userName = null,
            userDisplayName = null,
            displayName = null,
            lastUsedAt = null,
        )

        val dm = PasskeyDisplayModel.fromEntity(entity)

        assertThat(dm.rpDisplayName).isNull()
        assertThat(dm.userName).isNull()
        assertThat(dm.userDisplayName).isNull()
        assertThat(dm.displayName).isNull()
        assertThat(dm.lastUsedAt).isNull()
    }

    @Test
    fun dataClassDoesNotDeclareSensitiveEntityFields_reflection() {
        // NFR 2.1 / R4.6: the projection MUST NOT carry any of the five
        // sensitive entity columns. Reflection guards this contract
        // against accidental refactors.
        val forbidden = setOf(
            "userHandle",
            "encryptedPrivateKey",
            "privateKeyIv",
            "keyAlias",
            "signCount",
        )
        val declared = PasskeyDisplayModel::class.java.declaredFields
            .map { it.name }
            .toSet()

        val intersect = declared.intersect(forbidden)

        assertThat(intersect).isEmpty()
    }

    @Test
    fun dataClassDoesNotMentionSensitiveFields_inToString() {
        // Defensive: the data-class-generated toString must not surface
        // any of the forbidden column names so a stray logging call
        // cannot leak the sensitive metadata of a PassKey row.
        val dm = PasskeyDisplayModel.fromEntity(sampleEntity("with-everything"))

        val s = dm.toString()

        assertThat(s).doesNotContain("userHandle")
        assertThat(s).doesNotContain("encryptedPrivateKey")
        assertThat(s).doesNotContain("privateKeyIv")
        assertThat(s).doesNotContain("keyAlias")
        assertThat(s).doesNotContain("signCount")
    }

    @Test
    fun equals_isReflexive() {
        val a = PasskeyDisplayModel.fromEntity(sampleEntity("c"))
        val b = PasskeyDisplayModel.fromEntity(sampleEntity("c"))
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun equals_distinguishesDifferentCredentialIds() {
        val a = PasskeyDisplayModel.fromEntity(sampleEntity("c-1"))
        val b = PasskeyDisplayModel.fromEntity(sampleEntity("c-2"))
        assertThat(a).isNotEqualTo(b)
    }

    // ---- helpers -------------------------------------------------------

    private fun sampleEntity(
        credentialId: String,
        rpId: String = "example.com",
        rpDisplayName: String? = "Example",
        userName: String? = "alice",
        userDisplayName: String? = "Alice",
        displayName: String? = null,
        isDiscoverable: Boolean = true,
        createdAt: Long = 1_700_000_000_000L,
        lastUsedAt: Long? = null,
    ) = PasskeyEntity(
        credentialId = credentialId,
        rpId = rpId,
        rpDisplayName = rpDisplayName,
        userHandle = ByteArray(16) { 0x77 },
        userName = userName,
        userDisplayName = userDisplayName,
        isDiscoverable = isDiscoverable,
        encryptedPrivateKey = ByteArray(48) { 0x66 },
        privateKeyIv = ByteArray(12) { 0x55 },
        keyAlias = "keynest_passkey_$credentialId",
        signCount = 7L,
        displayName = displayName,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )
}
