package io.github.hitoshiichikawa.keynest.ui.list

import io.github.hitoshiichikawa.keynest.domain.model.Credential

/**
 * Issue #101 (Phase 4 of umbrella #89) — sealed type describing every
 * row that can appear in the credential list RecyclerView.
 *
 * Two variants:
 *  - [Password]: existing password credential ([Credential]).
 *  - [Passkey]: PassKey row backed by [PasskeyDisplayModel].
 *
 * DiffUtil identity uses [stableId], which carries a `"pw:"` / `"pk:"`
 * variant prefix. This prevents the cross-variant collision that
 * `Credential.id.value == 1L` and `PasskeyEntity.credentialId == "1"`
 * would otherwise trigger when DiffUtil compares `a.id == b.id`
 * (Issue #101 D-8 / R1.5).
 *
 * The shared [sortKey] projection lets [CredentialListSorting] sort a
 * mixed list of both variants with a single Comparator (R1.4).
 *
 * [Comparable] is deliberately NOT implemented — sort ordering is
 * concentrated in [CredentialListSorting] so the NULL-handling rules
 * can be unit-tested without instantiating the full ViewModel.
 */
sealed interface CredentialListItem {

    /**
     * DiffUtil `areItemsTheSame` identity. The `"pw:"` / `"pk:"` prefix
     * guarantees that the same numeric string can never identify two
     * different variants as one row.
     */
    val stableId: String

    /** Read-only `(lastUsedAt, createdAt)` projection used by sorting. */
    val sortKey: SortKey

    data class Password(val credential: Credential) : CredentialListItem {
        override val stableId: String
            get() = "pw:${credential.id.value}"
        override val sortKey: SortKey
            get() = SortKey(lastUsedAt = credential.lastUsedAt, createdAt = credential.createdAt)
    }

    data class Passkey(val passkey: PasskeyDisplayModel) : CredentialListItem {
        override val stableId: String
            get() = "pk:${passkey.credentialId}"
        override val sortKey: SortKey
            get() = SortKey(lastUsedAt = passkey.lastUsedAt, createdAt = passkey.createdAt)
    }

    /**
     * Variant-agnostic sort projection. The Comparator in
     * [CredentialListSorting] only reads these two values, so it does
     * not care whether the underlying row is a password or a PassKey.
     */
    data class SortKey(val lastUsedAt: Long?, val createdAt: Long)
}
