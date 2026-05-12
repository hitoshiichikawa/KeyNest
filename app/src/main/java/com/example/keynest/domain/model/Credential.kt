package com.example.keynest.domain.model

/**
 * Strongly typed credential identifier.
 *
 * Wrapping the underlying Long avoids accidentally swapping IDs from
 * different aggregates and keeps repository / use-case signatures explicit.
 */
@JvmInline
value class CredentialId(val value: Long)

/**
 * Domain representation of a saved credential.
 *
 * Notably absent: the plaintext password. The domain layer only sees the
 * encrypted blob via [com.example.keynest.security.EncryptedBlob]
 * (carried on [EncryptedCredentialRecord]) or, after explicit unlock, via
 * the short-lived [PlaintextCredential]. Decrypted bytes never live on
 * this aggregate. Requirements: 1.2, 5.5, NFR 1.4.
 */
data class Credential(
    val id: CredentialId,
    val packageName: String,
    val username: String,
    val label: String,
    val signatureSha256: SigningHash?,
    val signatureCapturedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        // Invariant: if there's no hash, there is no capture timestamp either.
        require((signatureSha256 == null) == (signatureCapturedAt == null)) {
            "signatureSha256 and signatureCapturedAt must both be null or both be non-null"
        }
    }
}

/**
 * Credential plus its encrypted password blob. Used at the repository
 * boundary (data <-> domain). The plaintext is reconstructed by
 * [com.example.keynest.domain.usecase.UnlockVaultUseCase] via the cipher.
 */
data class EncryptedCredentialRecord(
    val id: CredentialId,
    val packageName: String,
    val username: String,
    val label: String,
    val passwordCiphertext: ByteArray,
    val passwordIv: ByteArray,
    val signatureSha256: SigningHash?,
    val signatureCapturedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedCredentialRecord) return false
        return id == other.id &&
            packageName == other.packageName &&
            username == other.username &&
            label == other.label &&
            passwordCiphertext.contentEquals(other.passwordCiphertext) &&
            passwordIv.contentEquals(other.passwordIv) &&
            signatureSha256 == other.signatureSha256 &&
            signatureCapturedAt == other.signatureCapturedAt &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + passwordCiphertext.contentHashCode()
        result = 31 * result + passwordIv.contentHashCode()
        result = 31 * result + (signatureSha256?.hashCode() ?: 0)
        result = 31 * result + (signatureCapturedAt?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

    /** Hide encrypted bytes from accidental logging. NFR 1.3. */
    override fun toString(): String =
        "EncryptedCredentialRecord(id=$id, packageName=$packageName, username=$username, label=$label, " +
            "ciphertext=<${passwordCiphertext.size}B>, iv=<${passwordIv.size}B>, " +
            "signatureSha256=$signatureSha256, signatureCapturedAt=$signatureCapturedAt, " +
            "createdAt=$createdAt, updatedAt=$updatedAt)"
}
