package io.github.hitoshiichikawa.keynest.util

import android.view.Window
import android.view.WindowManager

/**
 * Issue #135: 機密情報を表示する Activity のウィンドウに
 * [WindowManager.LayoutParams.FLAG_SECURE] を設定する共通ヘルパー。
 *
 * FLAG_SECURE はスクリーンショット / 画面録画 (MediaProjection) /
 * recents サムネイル / 非セキュアディスプレイへのミラーリングから
 * ウィンドウ内容を除外する（キャプチャ結果は黒塗りになる）。
 *
 * 設定は必ず本ヘルパー経由で行うこと。個別 Activity での
 * `window.setFlags(...)` 直書きは禁止 — 適用漏れ / 適用先一覧を
 * `FlagSecureSourceAuditTest` が機械的に検査できる状態を保つため。
 *
 * 呼び出し位置は `onCreate` の `super.onCreate(...)` 直後を推奨
 * （`setContentView` より前に設定すれば初回フレームから有効）。
 */
object SecureWindow {

    /** [window] にキャプチャ抑止フラグ FLAG_SECURE を立てる。冪等。 */
    fun apply(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}
