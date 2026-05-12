package com.example.keynest.ui.edit

import android.content.ClipData

/**
 * Builds the `ClipData` that the Edit screen places on the system
 * clipboard when the user copies a credential's signing-certificate
 * SHA-256 hash.
 *
 * Requirements: 3.3 (full 64-char lowercase hex written to clipboard).
 *
 * This lives apart from [com.example.keynest.util.AdvancedDetailsFormatter]
 * so the formatter stays free of Android framework dependencies (it is
 * exercised by plain JUnit). The clipboard helper, by contrast, must hand
 * back a `ClipData` instance and therefore links against android.content.
 *
 * The function is intentionally side-effect free: it does not touch
 * `ClipboardManager`. The Activity that drives clipboard writes wraps it
 * in `clipboard.setPrimaryClip(...)`. Keeping the data construction pure
 * makes it unit-testable under Robolectric without touching the system
 * service.
 */
internal object SignatureClipboardPayload {

    /**
     * @param label human-visible label shown by clipboard manager UIs.
     *   Must be a resolved (i.e. already localised) String, not a resource
     *   id, so callers retain full control over translation.
     * @param hex full 64-char lowercase SHA-256 hex (the entire string
     *   becomes the clipboard text -- no truncation).
     */
    fun build(label: String, hex: String): ClipData {
        return ClipData.newPlainText(label, hex)
    }
}
