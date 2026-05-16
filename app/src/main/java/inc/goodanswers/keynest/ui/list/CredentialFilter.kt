package inc.goodanswers.keynest.ui.list

/**
 * The two filter chips offered above the credential list. Issue #9 Req
 * 2.1, 2.3, 2.4, 2.5, 2.6.
 *
 * Encoded as a sealed type so we can extend safely without breaking
 * exhaustive `when` blocks elsewhere if a third filter is added later.
 * Note: requirement 2.6 mandates EXCLUSIVE selection -- both chips active
 * simultaneously is NOT a valid state, so a sealed type with three
 * variants is correct (the third being "no filter at all").
 */
sealed interface CredentialFilter {
    /** No chip selected -- show everything. Req 2.2. */
    data object None : CredentialFilter

    /** Show only credentials whose `signatureSha256` is non-null. Req 2.3. */
    data object SignatureMatched : CredentialFilter

    /** Show only credentials whose `signatureSha256` is null. Req 2.4. */
    data object SignatureMissing : CredentialFilter
}
