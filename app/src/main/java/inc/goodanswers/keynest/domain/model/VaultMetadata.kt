package inc.goodanswers.keynest.domain.model

/**
 * Aggregated metadata about the credential vault.
 *
 * Issue #10 Requirements: 4.1, 4.2, 4.3, 4.5, 4.6.
 *
 * Carries only aggregate values -- never individual credential fields --
 * because the Settings screen MUST NOT expose label / username /
 * packageName / password / signature plaintext (NFR 1.2). The DAO query
 * that produces this value is intentionally `COUNT(*)` / `MAX(updated_at)`
 * (column-level) so no row contents are ever materialised.
 *
 * Invariant: when [count] == 0 the [latestUpdatedAt] is always null (the
 * DAO returns `null` for `MAX(updated_at)` on an empty table). The UI
 * uses this to switch to a "未登録" / "—" placeholder (Req 4.3).
 */
data class VaultMetadata(
    /** Total number of saved credentials. Always >= 0. */
    val count: Int,
    /**
     * The most recent `updated_at` timestamp across all credentials, or
     * null when no credentials are saved. Epoch millis (UTC); the UI
     * layer is responsible for converting to the device local timezone
     * (Req 4.2).
     */
    val latestUpdatedAt: Long?,
)
