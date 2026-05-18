package io.github.hitoshiichikawa.keynest.domain.model

/**
 * Failure surface for the "duplicate credential" flow. Issue #9 Req 5.3,
 * 5.4, NFR 1.3.
 *
 * Lives in the domain layer so both the repository and the use case can
 * agree on the error vocabulary without depending on UI strings.
 *
 * Important: failure objects must NOT carry any plaintext / decrypted
 * material (NFR 1.3). [Storage.reason] is the failing exception's class
 * name only.
 */
sealed class DuplicateFailure(message: String) : Exception(message) {
    /** No row matches the source credential id. */
    object NotFound : DuplicateFailure("source credential not found")

    /** Underlying SQLite or Room I/O failure. */
    data class Storage(val reason: String) : DuplicateFailure("storage error: $reason")
}
