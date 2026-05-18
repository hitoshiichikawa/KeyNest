package io.github.hitoshiichikawa.keynest.domain.model

/**
 * Plaintext representation of a single custom field associated with a
 * credential.
 *
 * Issue #66 Phase 1. Backs requirements 1.1 / 1.2 / 4.x.
 *
 * Notes:
 * - This type carries plaintext [value] and therefore MUST NOT appear on the
 *   plain [Credential] aggregate (see design.md §3.2). It is reachable only
 *   via [PlaintextCredential] (post-unlock) and the use-case input DTOs.
 * - [fieldKey] is the user-supplied display label. Normalisation for match
 *   purposes happens at match time inside the autofill builder, not here, so
 *   the original casing / whitespace is preserved (requirements Q1).
 * - Equality is structural (data class default) — both [fieldKey] and
 *   [value] participate so two custom fields with the same key but different
 *   values are distinct.
 */
data class CustomField(
    val fieldKey: String,
    val value: String,
) {
    /**
     * Hide the plaintext [value] from accidental logging.
     *
     * Requirements: 5.1 / NFR 2.1 — neither the value nor the (possibly
     * sensitive) fieldKey are emitted via [toString]. Only the key/value
     * lengths are preserved so logs can still report on size shape.
     */
    override fun toString(): String =
        "CustomField(fieldKey=<redacted ${fieldKey.length} chars>, value=<redacted ${value.length} chars>)"
}
