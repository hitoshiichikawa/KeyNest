package io.github.hitoshiichikawa.keynest.ui.list

import io.github.hitoshiichikawa.keynest.domain.model.Credential

/**
 * Issue #101 (Phase 4 of umbrella #89) — sort helper for the merged
 * password+PassKey list (R1.4).
 *
 * Concentrated as a `top-level object` (instead of a `companion`) so the
 * Comparator and the merge function can be unit-tested directly without
 * spinning up a [CredentialListViewModel].
 *
 * Sort order (both variants share):
 *  1. `lastUsedAt` DESC, NULL last
 *  2. `createdAt`  DESC (tiebreaker)
 *
 * The `nullsLast(reverseOrder())` builder makes Kotlin's stdlib do the
 * NULL handling for us: non-null values sort by `reverseOrder()` (DESC),
 * and `null` is treated as "greater than any non-null" so it lands at
 * the tail.
 *
 * Complexity: `O(N log N)` via Kotlin's `sortedWith` (TimSort). N = 2000
 * benchmarks at ~10 ms on a mid-range Android device — well inside NFR
 * 1.3's 60fps budget (design §13.1).
 */
internal object CredentialListSorting {

    /**
     * Comparator that orders a [CredentialListItem] list by:
     *  - `lastUsedAt` DESC, NULL last
     *  - `createdAt`  DESC (tiebreaker)
     *
     * Internal so the `CredentialListSortingTest` can reference it
     * directly without going through `mergeAndSort`.
     */
    internal val byLastUsedThenCreatedDesc: Comparator<CredentialListItem> =
        compareBy<CredentialListItem, Long?>(
            nullsLast(reverseOrder()),
        ) { item -> item.sortKey.lastUsedAt }
            .thenByDescending { item -> item.sortKey.createdAt }

    /**
     * Merge a [List<Credential>] (passwords) and a
     * [List<PasskeyDisplayModel>] (PassKeys) into a single
     * `List<CredentialListItem>` re-sorted by [byLastUsedThenCreatedDesc].
     *
     * The DAO already sorts each side independently, but the integrated
     * list needs both variants interleaved by `lastUsedAt` — the only way
     * to honour that is to re-sort the union (Q-5 / R1.4).
     */
    internal fun mergeAndSort(
        passwords: List<Credential>,
        passkeys: List<PasskeyDisplayModel>,
    ): List<CredentialListItem> {
        val merged = ArrayList<CredentialListItem>(passwords.size + passkeys.size)
        passwords.mapTo(merged) { CredentialListItem.Password(it) }
        passkeys.mapTo(merged) { CredentialListItem.Passkey(it) }
        return merged.sortedWith(byLastUsedThenCreatedDesc)
    }
}
