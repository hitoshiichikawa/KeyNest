package io.github.hitoshiichikawa.keynest.ui.list

/**
 * Issue #101 (Phase 4 of umbrella #89) — credential kind filter.
 *
 * Three values: [All] (default), [PasswordOnly], [PasskeyOnly]. v1 does
 * not expose this on the UI — the requirements §"Non-Goal" / Issue body
 * "Out of Scope: 種別フィルタは v1 では未対応" specifically reserve the
 * chip rendering for a follow-up Issue.
 *
 * The State plumbing nonetheless lives in [CredentialListViewModel]
 * (always pinned to [All] in v1) so the future "kind filter chip"
 * Issue can light it up by adding chip XML + a `setUpKindFilter()` /
 * `onKindFilterChanged(...)` pair to `CredentialListActivity`, with no
 * restructuring of the ViewModel's `combine` pipeline.
 */
enum class KindFilter {
    /** Show password rows AND PassKey rows (v1 default). */
    All,

    /** Show password rows only (drops PassKey variants). */
    PasswordOnly,

    /** Show PassKey rows only (drops password variants). */
    PasskeyOnly,
}
