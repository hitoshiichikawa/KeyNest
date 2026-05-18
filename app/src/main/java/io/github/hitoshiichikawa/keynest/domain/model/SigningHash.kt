package io.github.hitoshiichikawa.keynest.domain.model

import java.security.MessageDigest

/**
 * 32-byte SHA-256 digest of a package's signing certificate(s).
 *
 * Requirements: 2.1, 2.3, 4.1, 4.2
 *
 * Equality is performed via [MessageDigest.isEqual] which is implemented in
 * constant time, so signature comparison cannot be exploited for timing
 * attacks (even though the hash values themselves are not secret, treating
 * them as opaque tokens makes the broader signature-mismatch flow side-channel
 * free).
 */
class SigningHash(bytes: ByteArray) {

    init {
        require(bytes.size == DIGEST_BYTES) {
            "SigningHash must be exactly $DIGEST_BYTES bytes, got ${bytes.size}"
        }
    }

    private val bytes: ByteArray = bytes.copyOf()

    /** Defensive copy — callers cannot mutate the internal byte array. */
    val value: ByteArray
        get() = bytes.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SigningHash) return false
        return MessageDigest.isEqual(bytes, other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    /** Intentionally hides the raw value to keep logs safe. NFR 5.1. */
    override fun toString(): String = "SigningHash(<${bytes.size}B>)"

    companion object {
        const val DIGEST_BYTES = 32

        /** Convenience: hash an arbitrary byte array with SHA-256. */
        fun ofSha256(input: ByteArray): SigningHash {
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            return SigningHash(digest)
        }
    }
}
