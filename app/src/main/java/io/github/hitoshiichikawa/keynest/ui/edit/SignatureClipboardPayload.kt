package io.github.hitoshiichikawa.keynest.ui.edit

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle

/**
 * Builds the `ClipData` that the Edit screen places on the system
 * clipboard when the user copies a credential's signing-certificate
 * SHA-256 hash.
 *
 * Requirements: 3.3 (full 64-char lowercase hex written to clipboard).
 *
 * This lives apart from [io.github.hitoshiichikawa.keynest.util.AdvancedDetailsFormatter]
 * so the formatter stays free of Android framework dependencies (it is
 * exercised by plain JUnit). The clipboard helper, by contrast, must hand
 * back a `ClipData` instance and therefore links against android.content.
 *
 * The function is intentionally side-effect free: it does not touch
 * `ClipboardManager`. The Activity that drives clipboard writes wraps it
 * in `clipboard.setPrimaryClip(...)`. Keeping the data construction pure
 * makes it unit-testable under Robolectric without touching the system
 * service.
 *
 * Issue #137: パスワードマネージャ発のクリップボード書き込みとして
 * [ClipDescription.EXTRA_IS_SENSITIVE] を常に付与する（API 33+ では
 * クリップボードのプレビュー UI 抑制・履歴保護が効く。下位 API は
 * extras キーを無視するだけなので version 分岐は不要）。
 */
internal object SignatureClipboardPayload {

    /**
     * `ClipDescription.EXTRA_IS_SENSITIVE` は API 33 で定数追加されたが、
     * 値は文字列リテラルとしてコンパイル時に inline されるため minSdk 26
     * でもそのまま参照できる（実行時に新 API を呼ばない）。
     */
    private const val EXTRA_IS_SENSITIVE_KEY = ClipDescription.EXTRA_IS_SENSITIVE

    /**
     * @param label human-visible label shown by clipboard manager UIs.
     *   Must be a resolved (i.e. already localised) String, not a resource
     *   id, so callers retain full control over translation.
     * @param hex full 64-char lowercase SHA-256 hex (the entire string
     *   becomes the clipboard text -- no truncation).
     */
    fun build(label: String, hex: String): ClipData {
        return ClipData.newPlainText(label, hex).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE_KEY, true)
            }
        }
    }
}
