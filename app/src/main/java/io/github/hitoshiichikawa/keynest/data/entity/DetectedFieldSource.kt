package io.github.hitoshiichikawa.keynest.data.entity

/**
 * Enum representation of the four supported `source` values of
 * [DetectedFieldEntity]. Issue #67 Phase 2 (requirements §4 Q1).
 *
 * The on-disk column type is `TEXT` (see [DetectedFieldEntity.source]) so
 * the value persisted is [storageKey] rather than the Kotlin enum name.
 * Decoupling the wire format from the enum identifier protects us from
 * accidental DB-corrupting renames in future refactors.
 *
 * Values:
 *   - [AutofillHints]: per-element of `ViewNode.autofillHints` (developer-
 *     declared, most authoritative).
 *   - [Hint]: `ViewNode.hint` (the "hint" label rendered to the user inside
 *     the empty input).
 *   - [ResourceId]: `ViewNode.idEntry` (developer-defined resource id).
 *   - [ContentDescription]: `ViewNode.contentDescription` (a11y label).
 *
 * Note: `ViewNode.text` is intentionally NOT a source value. Storing the
 * user-typed content would risk leaking plaintext passwords / PII into the
 * detected_fields table (requirements §4 Q1 / NFR 1).
 */
enum class DetectedFieldSource(val storageKey: String) {
    AutofillHints("autofillHints"),
    Hint("hint"),
    ResourceId("resourceId"),
    ContentDescription("contentDescription");

    companion object {
        /**
         * Decode [key] (as persisted in the DB) into a [DetectedFieldSource],
         * or return null if [key] is not one of the four supported values.
         *
         * Returning null (rather than throwing) lets the repository layer
         * skip forward-compatibility unknown rows without surfacing an
         * error to UI callers — Phase 3 may introduce additional sources
         * via a Migration_4_5, in which case Phase 2 binaries will simply
         * not display the unknown rows.
         */
        fun fromStorageKey(key: String): DetectedFieldSource? =
            values().firstOrNull { it.storageKey == key }
    }
}
