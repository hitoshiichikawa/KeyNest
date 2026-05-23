package io.github.hitoshiichikawa.keynest.domain.model

/**
 * Domain aggregate that represents a stored PassKey as observed from
 * outside the persistence boundary (Issue #107 / parent #91 /
 * design §3.1 / §6.1).
 *
 * **Why a separate domain type?**
 * - The persistence-layer [io.github.hitoshiichikawa.keynest.data.entity.PasskeyEntity]
 *   carries the AES-GCM ciphertext, the GCM IV, and the AndroidKeyStore
 *   wrapping-key alias. These three fields are **persistence internals**
 *   and MUST NOT leak across the repository boundary into UI / service
 *   callers (NFR 2.2 — secret material stays at the data layer).
 * - Higher layers (`KeyNestCredentialProviderService`, `GetEntryBuilder`,
 *   `PasskeyAuthActivity`, future management UI) need only the metadata
 *   carried below: credentialId, RP info, user info, discoverability flag,
 *   signCount, displayName and the createdAt / lastUsedAt timestamps.
 *
 * **Plaintext access**: To obtain the decrypted PKCS#8 private key bytes,
 * callers go through [io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository.loadPrivateKey];
 * the bytes are never carried on a `Passkey` instance.
 *
 * **Logging contract (NFR 2.2)**: [toString] redacts [userHandle] to a
 * size marker so accidental `Log.d("...", passkey)` cannot leak the user
 * identifier bytes (which W3C WebAuthn §5.4.3 explicitly forbids exposing).
 *
 * `data class` is intentionally NOT used here because [userHandle] is a
 * `ByteArray`; the generated `equals` / `hashCode` would compare by
 * reference rather than content (same gotcha that motivates the override
 * in [SavePasskeyRequest] / [PasskeyEntity]).
 */
class Passkey(
    val credentialId: String,
    val rpId: String,
    val rpDisplayName: String?,
    val userHandle: ByteArray,
    val userName: String?,
    val userDisplayName: String?,
    val isDiscoverable: Boolean,
    val signCount: Long,
    val displayName: String?,
    val createdAt: Long,
    val lastUsedAt: Long?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Passkey) return false
        return credentialId == other.credentialId &&
            rpId == other.rpId &&
            rpDisplayName == other.rpDisplayName &&
            userHandle.contentEquals(other.userHandle) &&
            userName == other.userName &&
            userDisplayName == other.userDisplayName &&
            isDiscoverable == other.isDiscoverable &&
            signCount == other.signCount &&
            displayName == other.displayName &&
            createdAt == other.createdAt &&
            lastUsedAt == other.lastUsedAt
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + (rpDisplayName?.hashCode() ?: 0)
        result = 31 * result + userHandle.contentHashCode()
        result = 31 * result + (userName?.hashCode() ?: 0)
        result = 31 * result + (userDisplayName?.hashCode() ?: 0)
        result = 31 * result + isDiscoverable.hashCode()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastUsedAt?.hashCode() ?: 0)
        return result
    }

    /**
     * Redacted toString: size marker for [userHandle] only (NFR 2.2).
     */
    override fun toString(): String =
        "Passkey(credentialId=$credentialId, rpId=$rpId, " +
            "rpDisplayName=$rpDisplayName, " +
            "userHandle=ByteArray(size=${userHandle.size}), " +
            "userName=$userName, userDisplayName=$userDisplayName, " +
            "isDiscoverable=$isDiscoverable, signCount=$signCount, " +
            "displayName=$displayName, createdAt=$createdAt, " +
            "lastUsedAt=$lastUsedAt)"
}
