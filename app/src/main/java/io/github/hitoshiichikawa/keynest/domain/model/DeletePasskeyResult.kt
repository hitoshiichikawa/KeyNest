package io.github.hitoshiichikawa.keynest.domain.model

/**
 * Outcome of [io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository.delete].
 *
 * Surfaced as a sealed type rather than a plain `Boolean` so the caller can
 * distinguish between a clean delete and a "DB row removed but the
 * AndroidKeyStore wrapping key alias could not be cleared" situation.
 * The latter leaves an orphaned alias that is best-effort recovered later;
 * the row removal itself succeeded (see #99 design §5.3.2).
 *
 * Inlined into this PR ahead of the upstream #107 merge — same shape so
 * adopting #107 later collapses to a no-op rename.
 */
sealed class DeletePasskeyResult {
    /** Row + Keystore alias removed (or alias already absent). */
    object Success : DeletePasskeyResult()

    /**
     * Row removed but the Keystore alias delete raised
     * `java.security.KeyStoreException`. Caller may proceed (e.g. continue
     * to a new INSERT for the overwrite case) and surface the orphaned
     * alias separately (§5.3.2).
     */
    data class KeystoreCleanupFailed(val cause: Throwable) : DeletePasskeyResult()
}
