package io.github.hitoshiichikawa.keynest.domain.model

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
 * encrypted blob via [io.github.hitoshiichikawa.keynest.security.EncryptedBlob]
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
    /**
     * Wall-clock time (epoch millis) of the most recent autofill consumption
     * of this credential. Null means the credential has never been used via
     * autofill -- such rows are excluded from the "recently used" carousel
     * (Issue #9 Req 3.3 / 3.4).
     */
    val lastUsedAt: Long? = null,
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
 * [io.github.hitoshiichikawa.keynest.domain.usecase.UnlockVaultUseCase] via the cipher.
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
    /** See [Credential.lastUsedAt]. Null = never used. */
    val lastUsedAt: Long? = null,
    /**
     * AES-GCM ciphertext of the JSON-encoded `List<CustomField>` for this
     * credential. Issue #66 Phase 1. May be a zero-length array for rows
     * that were inserted via Migration_2_3 and have not been re-saved yet
     * (design.md §4.1 / §6.1). The codec interprets empty BLOB == empty
     * customFields list.
     */
    val customFieldsCiphertext: ByteArray = ByteArray(0),
    /**
     * 12-byte AES-GCM IV that produced [customFieldsCiphertext]. Empty when
     * [customFieldsCiphertext] is empty (post-migration default).
     */
    val customFieldsIv: ByteArray = ByteArray(0),
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
            updatedAt == other.updatedAt &&
            lastUsedAt == other.lastUsedAt &&
            customFieldsCiphertext.contentEquals(other.customFieldsCiphertext) &&
            customFieldsIv.contentEquals(other.customFieldsIv)
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
        result = 31 * result + (lastUsedAt?.hashCode() ?: 0)
        result = 31 * result + customFieldsCiphertext.contentHashCode()
        result = 31 * result + customFieldsIv.contentHashCode()
        return result
    }

    /** Hide encrypted bytes from accidental logging. NFR 1.3. */
    override fun toString(): String =
        "EncryptedCredentialRecord(id=$id, packageName=$packageName, username=$username, label=$label, " +
            "ciphertext=<${passwordCiphertext.size}B>, iv=<${passwordIv.size}B>, " +
            "signatureSha256=$signatureSha256, signatureCapturedAt=$signatureCapturedAt, " +
            "createdAt=$createdAt, updatedAt=$updatedAt, lastUsedAt=$lastUsedAt, " +
            "customFieldsCiphertext=<${customFieldsCiphertext.size}B>, " +
            "customFieldsIv=<${customFieldsIv.size}B>)"
}
