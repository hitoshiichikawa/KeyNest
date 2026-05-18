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
 * - [customFields] (Issue #66 Phase 1) is a list of decrypted custom fields.
 *   Their `value` strings are plaintext — see the limitations note below.
 *
 * customFields wiping caveat:
 * - [CustomField.fieldKey] / [CustomField.value] are `String`. The JVM does
 *   not give us deterministic zero-fill on immutable Strings, so a true wipe
 *   is impossible at the language level. [close] therefore only clears the
 *   list reference (replacing the backing list with empty) so subsequent
 *   reads return nothing; the underlying String objects will be reclaimed
 *   by GC eventually. This matches the existing handling of [username] /
 *   [label] (also Strings) and is documented as an accepted limitation.
 */
class PlaintextCredential(
    val id: CredentialId,
    val packageName: String,
    val username: String,
    val label: String,
    val password: CharArray,
    customFields: List<CustomField> = emptyList(),
) : AutoCloseable {

    @Volatile
    private var closed: Boolean = false

    private var _customFields: List<CustomField> = customFields

    /**
     * Decrypted custom fields. Read-only view. After [close] this becomes an
     * empty list (best-effort cleanup — String contents cannot be wiped
     * deterministically on the JVM; see class kdoc).
     */
    val customFields: List<CustomField>
        get() = _customFields

    /** True after [close] has been invoked at least once. */
    val isClosed: Boolean get() = closed

    /** Overwrites the [password] buffer with spaces and marks this closed. */
    override fun close() {
        if (closed) return
        Arrays.fill(password, ' ')
        // Best-effort clear of the customFields reference. The Strings
        // themselves remain in the heap until GC; this is a known JVM
        // limitation for immutable String. See class kdoc.
        _customFields = emptyList()
        closed = true
    }

    /** Do not log the password buffer or custom field values. NFR 1.3 / Req 5.1. */
    override fun toString(): String =
        "PlaintextCredential(id=$id, packageName=$packageName, username=$username, label=$label, " +
            "password=<redacted ${password.size} chars>, customFields=<${_customFields.size} entries>, closed=$closed)"
}
