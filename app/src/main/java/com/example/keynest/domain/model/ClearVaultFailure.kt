package com.example.keynest.domain.model

/**
 * Failure cause for [com.example.keynest.domain.usecase.ClearVaultUseCase].
 *
 * Issue #10 Req 7.7. Carries only the exception class simple name as the
 * `reason` field -- NEVER the full message / stack trace -- so that the
 * SafeLogger / Snackbar paths cannot leak credential plaintext or
 * Keystore internals (NFR 1.2, mirrors [DuplicateFailure]).
 */
sealed interface ClearVaultFailure {

    /** Underlying exception class simple name (e.g. "SQLiteException"). */
    val reason: String

    /**
     * Room `DELETE FROM credentials` failed. The Keystore alias was NOT
     * touched yet, so retrying from the Danger Zone "再認証" entry point
     * starts from a clean slate.
     */
    data class Storage(override val reason: String) : ClearVaultFailure, Throwable(reason)

    /**
     * Room DELETE succeeded but `KeystoreKeyProvider.deleteKey()` threw.
     * The DB is empty at this point, so a Danger Zone retry only needs
     * to redo the deleteKey step (the next deleteAll is a no-op).
     */
    data class KeystoreAlias(override val reason: String) : ClearVaultFailure, Throwable(reason)
}
