package io.github.hitoshiichikawa.keynest.ui.list

import io.github.hitoshiichikawa.keynest.domain.model.Credential
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder

/**
 * Snapshot of the credential list screen's render state. Combines all
 * user-driven inputs (search query / filter chip / sort order) plus the
 * derived main list, "recently used" carousel feed and empty-state kind.
 *
 * Issue #9 Req 1.x (search), 2.x (filter), 3.x (recent carousel), 4.x
 * (sort), 6.x (no regression in existing list behaviour).
 *
 * Invariants:
 * - [emptyKind] is non-null iff [mainList] is empty.
 *   - [EmptyKind.Initial] when the user has not narrowed the list at all
 *     (query is blank AND filter is None).
 *   - [EmptyKind.NoMatch] when at least one of query / filter is active
 *     and the intersection happens to be empty.
 * - [recentList] is independent of [query] / [filter]; it follows
 *   `last_used_at` over the entire credential set (Req 3.6).
 */
data class CredentialListUiState(
    val query: String,
    val filter: CredentialFilter,
    val sort: CredentialSortOrder,
    val mainList: List<Credential>,
    val recentList: List<Credential>,
    val emptyKind: EmptyKind?,
) {
    companion object {
        /**
         * The initial state surfaced before any data has been observed.
         * Matches the requirement defaults: blank query, no filter chip
         * selected, sort = UpdatedAtDesc (Req 4.2), both lists empty,
         * emptyKind = Initial (treats first-frame as "vault empty").
         */
        val EMPTY: CredentialListUiState = CredentialListUiState(
            query = "",
            filter = CredentialFilter.None,
            sort = CredentialSortOrder.UpdatedAtDesc,
            mainList = emptyList(),
            recentList = emptyList(),
            emptyKind = EmptyKind.Initial,
        )
    }
}
