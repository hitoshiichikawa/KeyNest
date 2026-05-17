package inc.goodanswers.keynest.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Behaviour of the [Credential] aggregate and [EncryptedCredentialRecord]
 * boundary type.
 *
 * Backs Req 1.1, 2.1, 2.2 invariants and the NFR 1.3 redaction surface.
 */
class CredentialTest {

    @Test
    fun construction_acceptsHashWithTimestamp() {
        val cred = Credential(
            id = CredentialId(1L),
            packageName = "com.example",
            username = "alice",
            label = "Example",
            signatureSha256 = SigningHash.ofSha256("c".toByteArray()),
            signatureCapturedAt = 1234L,
            createdAt = 1L,
            updatedAt = 2L,
        )
        assertThat(cred.signatureSha256).isNotNull()
        assertThat(cred.signatureCapturedAt).isEqualTo(1234L)
    }

    @Test
    fun construction_acceptsNullHashAndNullTimestamp() {
        val cred = Credential(
            id = CredentialId(1L),
            packageName = "com.example",
            username = "alice",
            label = "Example",
            signatureSha256 = null,
            signatureCapturedAt = null,
            createdAt = 1L,
            updatedAt = 2L,
        )
        assertThat(cred.signatureSha256).isNull()
        assertThat(cred.signatureCapturedAt).isNull()
    }

    @Test(expected = IllegalArgumentException::class)
    fun construction_rejects_hashWithoutTimestamp() {
        Credential(
            id = CredentialId(1L),
            packageName = "com.example",
            username = "alice",
            label = "Example",
            signatureSha256 = SigningHash.ofSha256("c".toByteArray()),
            signatureCapturedAt = null,
            createdAt = 1L,
            updatedAt = 2L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun construction_rejects_timestampWithoutHash() {
        Credential(
            id = CredentialId(1L),
            packageName = "com.example",
            username = "alice",
            label = "Example",
            signatureSha256 = null,
            signatureCapturedAt = 1234L,
            createdAt = 1L,
            updatedAt = 2L,
        )
    }

    @Test
    fun encryptedRecord_toString_redactsCiphertext() {
        val rec = EncryptedCredentialRecord(
            id = CredentialId(1L),
            packageName = "com.example",
            username = "alice",
            label = "Example",
            passwordCiphertext = "SECRET_BYTES".toByteArray(),
            passwordIv = ByteArray(12) { 1 },
            signatureSha256 = null,
            signatureCapturedAt = null,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val rendered = rec.toString()
        assertThat(rendered).doesNotContain("SECRET_BYTES")
        // size summary survives
        assertThat(rendered).contains("ciphertext=<12B>")
        assertThat(rendered).contains("iv=<12B>")
    }
}
