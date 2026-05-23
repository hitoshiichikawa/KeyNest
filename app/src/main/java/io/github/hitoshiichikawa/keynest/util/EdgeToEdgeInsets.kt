package io.github.hitoshiichikawa.keynest.util

import android.view.View
import androidx.core.view.ViewCompat
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
