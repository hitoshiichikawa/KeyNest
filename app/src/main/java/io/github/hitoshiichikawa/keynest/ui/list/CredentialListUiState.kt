package io.github.hitoshiichikawa.keynest.ui.list

import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder

/**
 * Snapshot of the credential list screen's render state. Combines all
 * user-driven inputs (search query / filter chip / sort order / kind
 * filter) plus the derived main list, "recently used" carousel feed
 * and empty-state kind.
 *
 * Issue #9 Req 1.x (search), 2.x (filter), 3.x (recent carousel), 4.x
 * (sort), 6.x (no regression in existing list behaviour).
 *
 * Issue #101 (Phase 4 of umbrella #89):
 *  - [mainList] is now `List<CredentialListItem>` instead of
 *    `List<Credential>` so the same RecyclerView can render password
 *    rows and PassKey rows side by side (R1.1).
 *  - [kindFilter] is added; v1 always pins it to [KindFilter.All] (the
 *    chip is reserved for a follow-up Issue per Q-2), but the State
 *    structure carries the value so the future chip Issue does not need
 *    to restructure UiState.
 *  - [passkeyCount] surfaces a derived count (= mainList.count {
 *    Passkey }) so tests can assert PassKey presence without having to
 *    walk `mainList` by hand.
 *  - [recentList] stays `List<Credential>` — PassKey integration into
 *    the Recently-Used carousel is out of scope (Q-6).
 *
 * Invariants:
 * - [emptyKind] is non-null iff [mainList] is empty.
 *   - [EmptyKind.Initial] when the user has not narrowed the list at all
 *     (query is blank AND filter is None).
 *   - [EmptyKind.NoMatch] when at least one of query / filter is active
 *     and the intersection happens to be empty.
 * - [recentList] is independent of [query] / [filter]; it follows
 *   `last_used_at` over the entire credential set (Req 3.6).
 * - [passkeyCount] equals `mainList.count { it is CredentialListItem.Passkey }`.
 */
data class CredentialListUiState(
    val query: String,
    val filter: CredentialFilter,
    val sort: CredentialSortOrder,
    val kindFilter: KindFilter,
    val mainList: List<CredentialListItem>,
    val recentList: List<Credential>,
    val emptyKind: EmptyKind?,
    val passkeyCount: Int,
) {
    companion object {
        /**
         * The initial state surfaced before any data has been observed.
         * Matches the requirement defaults: blank query, no filter chip
         * selected, sort = UpdatedAtDesc (Req 4.2), kindFilter = All
         * (Issue #101 v1 / Q-2), both lists empty, emptyKind = Initial
         * (treats first-frame as "vault empty"), passkeyCount = 0.
         */
        val EMPTY: CredentialListUiState = CredentialListUiState(
            query = "",
            filter = CredentialFilter.None,
            sort = CredentialSortOrder.UpdatedAtDesc,
            kindFilter = KindFilter.All,
            mainList = emptyList(),
            recentList = emptyList(),
            emptyKind = EmptyKind.Initial,
            passkeyCount = 0,
        )
    }
}
