package inc.goodanswers.keynest.util

import inc.goodanswers.keynest.domain.model.SigningHash
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display-only formatters for the Edit screen's "Advanced details" section.
 *
 * Requirements: 2.2 (local timezone timestamp), 3.1 (64-char lowercase hex),
 * 3.5 (monospace presentation contract -- the formatter promises a
 * stable-width string that the layout will style with a monospace face),
 * 4.1 (signature capture timestamp local timezone).
 *
 * Pure functions only -- no Android dependencies -- so they are testable
 * under plain JUnit and trivially reusable from instrumentation tests too.
 *
 * Logging policy: this object MUST NOT log the full 64-char SHA-256 hex.
 * Callers that need to surface a hex preview for diagnostics must go
 * through [SafeLogger.previewHex] (NFR 1.1 / 1.2).
 */
object AdvancedDetailsFormatter {

    /** SHA-256 digest is exactly 32 bytes => 64 lowercase hex characters. */
    const val SHA256_HEX_LENGTH: Int = 64

    /**
     * Year-Month-Day Hour:Minute. Seconds are intentionally omitted -- the
     * requirement (2.2) only asks for year / month / day / hour / minute
     * legibility, and a shorter timestamp keeps the row width manageable on
     * small phone screens.
     */
    const val TIMESTAMP_PATTERN: String = "yyyy-MM-dd HH:mm"

    /**
     * Formats an epoch-millis timestamp in the supplied [zone] using
     * [TIMESTAMP_PATTERN].
     *
     * @param epochMillis milliseconds since epoch (UTC).
     * @param zone target timezone -- defaults to the device's system
     *   timezone (Req 2.2 / 4.1: "device local timezone"). The parameter
     *   exists so unit tests can pin a deterministic zone without messing
     *   with the JVM's global TimeZone state.
     */
    fun formatTimestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN, Locale.ROOT)
        return Instant.ofEpochMilli(epochMillis).atZone(zone).format(formatter)
    }

    /**
     * Encodes [hash] to its canonical lowercase 64-char hex representation
     * (Req 3.1). Returns null when [hash] is null so the caller can render
     * a "not captured" placeholder (Req 3.4).
     *
     * The output is guaranteed to be exactly [SHA256_HEX_LENGTH] characters
     * because [SigningHash.value] is guaranteed to be 32 bytes by the value
     * object's invariant.
     */
    fun formatSha256Hex(hash: SigningHash?): String? {
        if (hash == null) return null
        val hex = HexEncoding.encode(hash.value)
        check(hex.length == SHA256_HEX_LENGTH) {
            // Defensive: SigningHash already enforces 32 bytes, but if a
            // future change weakens the invariant we want to fail loudly
            // rather than silently produce a truncated hex string.
            "expected $SHA256_HEX_LENGTH hex chars, got ${hex.length}"
        }
        return hex
    }
}
