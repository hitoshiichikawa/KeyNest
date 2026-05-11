package com.example.keynest.security

/**
 * Holder for an AES-GCM ciphertext + the IV that produced it.
 *
 * Persisted as two separate Room columns (`password_ciphertext` and
 * `password_iv`) - see [com.example.keynest.data.entity.CredentialEntity].
 *
 * Requirements: 1.2, NFR 1.1
 */
data class EncryptedBlob(
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBlob) return false
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()

    /** Hide both byte arrays from accidental logging. NFR 1.3. */
    override fun toString(): String = "EncryptedBlob(iv=<${iv.size}B>, ciphertext=<${ciphertext.size}B>)"
}
