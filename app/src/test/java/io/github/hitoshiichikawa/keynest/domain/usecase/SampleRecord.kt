package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash

/**
 * Shared test fixture: builds a minimally-populated
 * [EncryptedCredentialRecord] suitable for round-tripping through
 * [FakeCredentialRepository]. Used by tests that don't care about
 * the field values, only that "a credential exists".
 */
internal fun sampleRecord(
    label: String,
    updatedAt: Long = 0L,
): EncryptedCredentialRecord = EncryptedCredentialRecord(
    id = CredentialId(0L),
    packageName = "com.example.$label",
    username = "user-$label",
    label = label,
    passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
    passwordIv = ByteArray(12) { 0x10.toByte() },
    signatureSha256 = SigningHash(ByteArray(32) { 0x20.toByte() }),
    signatureCapturedAt = 1_000L,
    createdAt = updatedAt,
    updatedAt = updatedAt,
)
