package io.github.hitoshiichikawa.keynest.domain.model

/**
 * Domain request DTO carrying the plaintext PassKey material from the
 * registration ceremony (Issue #99 / parent #89) into the repository
 * boundary, where the private key is AES-GCM encrypted before INSERT.
 *
 * Inlined into this PR because the umbrella `PasskeyRepository` Issue
 * (#107) has not merged yet — the alias / encryption contract of #99
 * cannot be exercised end-to-end without these types. The shape mirrors
 * what #107 design.md §6.x specifies so that adopting #107 later
 * collapses to a no-op rename.
 *
 * **Mutability contract**: [userHandle] and [privateKey] are mutable
 * `ByteArray`s. The registration ceremony (`PasskeyCreateActivity`)
 * MUST zero-fill [privateKey] via `fill(0)` immediately after the
 * `PasskeyRepository.save(...)` call returns (NFR 1.3 / req 1.7).
 *
 * Logging contract (NFR 1.4): [toString] never emits raw [userHandle]
 * or [privateKey] bytes — only size markers — so logcat cannot leak
 * key material if a caller carelessly logs the request.
 */
data class SavePasskeyRequest(
    val credentialId: String,
    val rpId: String,
    val rpDisplayName: String?,
    val userHandle: ByteArray,
    val userName: String?,
    val userDisplayName: String?,
    val isDiscoverable: Boolean,
    /** PKCS#8-encoded EC P-256 private key, plaintext. Repository encrypts via AES-GCM and clears no buffer; caller wipes. */
    val privateKey: ByteArray,
    val signCount: Long,
    val displayName: String?,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavePasskeyRequest) return false
        return credentialId == other.credentialId &&
            rpId == other.rpId &&
            rpDisplayName == other.rpDisplayName &&
            userHandle.contentEquals(other.userHandle) &&
            userName == other.userName &&
            userDisplayName == other.userDisplayName &&
            isDiscoverable == other.isDiscoverable &&
            privateKey.contentEquals(other.privateKey) &&
            signCount == other.signCount &&
            displayName == other.displayName &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + (rpDisplayName?.hashCode() ?: 0)
        result = 31 * result + userHandle.contentHashCode()
        result = 31 * result + (userName?.hashCode() ?: 0)
        result = 31 * result + (userDisplayName?.hashCode() ?: 0)
        result = 31 * result + isDiscoverable.hashCode()
        result = 31 * result + privateKey.contentHashCode()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }

    /**
     * Redacted toString: size markers only for [userHandle] / [privateKey] so
     * the request can be logged at debug level without leaking key material
     * (NFR 1.4).
     */
    override fun toString(): String =
        "SavePasskeyRequest(credentialId=$credentialId, rpId=$rpId, " +
            "rpDisplayName=$rpDisplayName, " +
            "userHandle=ByteArray(size=${userHandle.size}), " +
            "userName=$userName, userDisplayName=$userDisplayName, " +
            "isDiscoverable=$isDiscoverable, " +
            "privateKey=ByteArray(size=${privateKey.size}), " +
            "signCount=$signCount, displayName=$displayName, " +
            "createdAt=$createdAt)"
}
