package io.github.hitoshiichikawa.keynest.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import io.github.hitoshiichikawa.keynest.BuildConfig

/**
 * Logging wrapper that mechanically prevents sensitive data from leaking to
 * logcat / crash reports.
 *
 * Requirements:
 * - NFR 1.3 - password plaintext must never appear in logs, exception
 *   messages, analytics or crash reports.
 * - NFR 5.1 - diagnostic logs must not include password / decrypted
 *   credential / full signature hex.
 *
 * Strategy:
 * - All "sensitive" payloads (password CharArray, decrypted byte array,
 *   full hex of signature hash) MUST be wrapped in [Redacted] before being
 *   passed to any of these log methods. Anything else implies a programmer
 *   error.
 * - Logged Throwable messages are scrubbed: only the exception class name is
 *   forwarded.
 * - DEBUG level is gated on [BuildConfig.DEBUG] (Issue #137): release builds
 *   have `isMinifyEnabled = false` so R8 strips nothing — the gate is the
 *   runtime mechanism that actually keeps debug-only detail (package names
 *   etc.) out of release logcat. Production code must therefore route ALL
 *   logging through this object instead of `android.util.Log`
 *   (enforced by RawLogImportAuditTest).
 */
object SafeLogger {

    private const val DEFAULT_TAG = "KeyNest"

    /**
     * Runtime gate for [debug]. Defaults to [BuildConfig.DEBUG] so release
     * builds drop DEBUG lines entirely. `@VisibleForTesting` so unit tests
     * can simulate the release behaviour (and must restore the value in
     * their teardown).
     */
    @VisibleForTesting
    internal var debugLogsEnabled: Boolean = BuildConfig.DEBUG

    @JvmStatic
    fun debug(tag: String = DEFAULT_TAG, message: String) {
        if (debugLogsEnabled) {
            Log.d(tag, message)
        }
    }

    @JvmStatic
    fun info(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
    }

    @JvmStatic
    fun warn(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, "$message (cause=${throwable.javaClass.simpleName})")
        }
    }

    /**
     * Logs at ERROR. Critically, the [throwable] message is NEVER forwarded -
     * only the class name is. A caller that needs to see the stack trace
     * during local debugging can swap this to [Log.e] manually; release
     * builds must rely on the class name.
     */
    @JvmStatic
    fun error(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, "$message (cause=${throwable.javaClass.simpleName})")
        }
    }

    /**
     * Wraps a value that, if present, would be sensitive. Calling toString()
     * on this type always returns a constant redaction marker so that string
     * interpolation in log calls is safe.
     *
     * Example:
     *   SafeLogger.info(tag = TAG, message = "saved credential pkg=${pkg} value=${Redacted(password)}")
     */
    class Redacted(@Suppress("UNUSED_PARAMETER") value: Any?) {
        override fun toString(): String = REDACTED_PLACEHOLDER
    }

    /** Returns a hex preview that drops everything except the first 8 chars. */
    fun previewHex(hex: String): String {
        if (hex.length <= HEX_PREVIEW_PREFIX_LEN) return REDACTED_PLACEHOLDER
        return "${hex.substring(0, HEX_PREVIEW_PREFIX_LEN)}..."
    }

    const val REDACTED_PLACEHOLDER = "<redacted>"
    const val HEX_PREVIEW_PREFIX_LEN = 8
}
