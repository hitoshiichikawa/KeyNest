package inc.goodanswers.keynest.ui.list

/**
 * Distinguishes the two reasons the credential list can be empty.
 * Issue #9 Req 1.4, 2.7.
 *
 * - [Initial]: the user has never created a credential yet -> show the
 *   onboarding "no credentials" message.
 * - [NoMatch]: there are credentials but none match the current query /
 *   filter -> show "no matching credentials" so the user does not think
 *   their data was lost.
 */
enum class EmptyKind {
    /** Vault is genuinely empty (Req 1.4 reuses Initial when not searching). */
    Initial,

    /**
     * Vault has rows, but the active query / filter excludes all of them.
     * Req 1.4, 2.7.
     */
    NoMatch,
}
