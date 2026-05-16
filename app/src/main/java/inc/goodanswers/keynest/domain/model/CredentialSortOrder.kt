package inc.goodanswers.keynest.domain.model

/**
 * The three sort orders offered to the credential list UI. Issue #9 Req
 * 4.1, 4.2.
 *
 * Lives in the domain layer (not UI) so it can be passed through use
 * cases and the [inc.goodanswers.keynest.domain.repository.CredentialRepository]
 * without dragging Android / UI types into shared code.
 *
 * Default is [UpdatedAtDesc] (Req 4.2).
 */
enum class CredentialSortOrder {
    /** Most recently updated first; label ASC tiebreaker. Default. */
    UpdatedAtDesc,

    /** Alphabetical by label, case-insensitive. */
    LabelAsc,

    /** Alphabetical by package_name, case-insensitive. */
    PackageAsc,
}
