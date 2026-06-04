package io.github.hitoshiichikawa.keynest.util

import android.content.res.Configuration
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk >= 35 (Android 15) では window が既定で edge-to-edge となり、
 * system bar / display cutout が content の上に重なる。重なりを避けるため、
 * Activity の root view に system bar + display cutout の inset を padding
 * として加算する。XML 側で定義済みの padding は保持され、その上に inset 分を
 * 重畳する。targetSdk < 35 で動く OS / device では inset が 0 として通知される
 * ため、本処理は no-op になる（後方互換）。
 */
fun View.applySystemBarsPadding() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        v.setPadding(
            initialLeft + bars.left,
            initialTop + bars.top,
            initialRight + bars.right,
            initialBottom + bars.bottom,
        )
        WindowInsetsCompat.CONSUMED
    }
}

/**
 * Activity を edge-to-edge レイアウトに切り替え、現在の uiMode (light/dark) に応じて
 * システムバーアイコンの明暗を設定する一括 helper。
 *
 * 呼び出しタイミング: `super.onCreate(...)` の直後、`setContentView()` の前で 1 回だけ呼ぶ。
 * 内部で [ComponentActivity.enableEdgeToEdge] を呼び出して window を edge-to-edge にした上で、
 * [WindowCompat.getInsetsController] 経由でアイコンの appearance を制御する。
 *
 * - light モード時: status bar / navigation bar とも **暗いアイコン**（appearance light = true）
 * - dark モード時: status bar / navigation bar とも **明るいアイコン**（appearance light = false）
 *
 * `isAppearanceLightNavigationBars` は API 27 (O_MR1) 未満では internally no-op となる
 * （androidx.core 実装契約）。クラッシュ・例外は発生しない（Req 5.3 silent degrade）。
 *
 * **注意**: 透過 Activity (`Theme.KeyNest.Translucent` を親に持つ Activity、すなわち
 * AutofillUnlockActivity / PasskeyAuthActivity / PasskeyCreateActivity) では呼ばないこと。
 * caller のシステムバー styling を上書きしてしまい、透過 launch UX が崩れるリスクがあるため。
 */
fun ComponentActivity.enableEdgeToEdgeWithKnDefaults() {
    enableEdgeToEdge()
    val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = !isNightMode
    controller.isAppearanceLightNavigationBars = !isNightMode
}
