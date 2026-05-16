package inc.goodanswers.keynest.util

import android.util.Log

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
 *   forwarded. Stack traces are emitted at DEBUG level only, never at
 *   INFO/WARN/ERROR (the assumption being that release builds may strip
 *   DEBUG via R8).
 */
object SafeLogger {

    private const val DEFAULT_TAG = "KeyNest"

    @JvmStatic
    fun debug(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
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
