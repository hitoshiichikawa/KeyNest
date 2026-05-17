package io.github.hitoshiichikawa.keynest.domain.model

import java.util.Arrays

/**
 * Short-lived holder for a decrypted credential. The [password] CharArray is
 * zero-filled by [close], so callers MUST use this type in a `use { }` block
 * (or call [close] in a try/finally).
 *
 * Requirements: 5.5, NFR 1.4
 *
 * Design notes:
 * - The CharArray is the canonical representation so we never have to round
 *   trip through a `String` (which lives in a separate, GC-controlled pool
 *   that we cannot deterministically erase).
 * - [packageName], [username] and [label] are not sensitive on their own;
 *   they are kept as plain String to keep the Autofill flow simple.
 */
class PlaintextCredential(
    val id: CredentialId,
    val packageName: String,
    val username: String,
    val label: String,
    val password: CharArray,
) : AutoCloseable {

    @Volatile
    private var closed: Boolean = false

    /** True after [close] has been invoked at least once. */
    val isClosed: Boolean get() = closed

    /** Overwrites the [password] buffer with spaces and marks this closed. */
    override fun close() {
        if (closed) return
        Arrays.fill(password, ' ')
        closed = true
    }

    /** Do not log the password buffer. NFR 1.3. */
    override fun toString(): String =
        "PlaintextCredential(id=$id, packageName=$packageName, username=$username, label=$label, " +
            "password=<redacted ${password.size} chars>, closed=$closed)"
}
