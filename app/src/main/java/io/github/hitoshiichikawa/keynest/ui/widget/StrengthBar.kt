package io.github.hitoshiichikawa.keynest.ui.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import io.github.hitoshiichikawa.keynest.R

/**
 * Three-segment password-strength indicator (Issue #29 Req 6.1–6.5).
 *
 * Visual contract (mapping.md §6 / design/spec.md §8):
 *  - 3 segments of width 14dp, height 4dp, gap 2dp (consumes
 *    `@dimen/kn_strength_seg_w` / `_h` / `_gap`).
 *  - Strong  → 3 segments filled with `@color/kn_success`.
 *  - Medium  → first 2 segments `@color/kn_warning`, last segment
 *              `@color/kn_border_strong` (track color).
 *  - Weak    → first 1 segment `@color/kn_danger`, remaining segments
 *              `@color/kn_border_strong`.
 *  - `null`  → the whole view is set to `View.GONE` so it occupies no
 *              vertical space (Req 6.5: if domain has no strength field,
 *              the bar must be hidden completely).
 *
 * The widget is a thin [LinearLayout] subclass that owns three child
 * [View] segments and tints them via [GradientDrawable] per-segment.
 * This keeps it cheap to instantiate in RecyclerView rows and avoids
 * any custom drawing pipeline.
 *
 * The view does not implement [setStrength] from XML attributes; the
 * row adapter sets it programmatically. The XML form simply renders an
 * empty (GONE) view by default. This matches the Issue #29 decision
 * (Open Questions / 確認事項 #2) to hide the bar when the credential
 * has no strength value, since the domain model does not yet carry one.
 */
class StrengthBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Discrete strength level. `null` means "unknown / hide". */
    enum class Strength { Weak, Medium, Strong }

    private val segmentViews: List<View>

    init {
        orientation = HORIZONTAL
        // Hidden by default; visibility flips back to VISIBLE the moment a
        // non-null strength is bound. Req 6.5: domain model has no
        // strength field yet, so the default for every row is GONE.
        visibility = GONE

        val gapPx: Int = resources.getDimensionPixelSize(R.dimen.kn_strength_seg_gap)
        val segWidthPx: Int = resources.getDimensionPixelSize(R.dimen.kn_strength_seg_w)
        val segHeightPx: Int = resources.getDimensionPixelSize(R.dimen.kn_strength_seg_h)
        val cornerPx: Float = segHeightPx / 2f

        segmentViews = List(SEGMENT_COUNT) { index ->
            View(context).apply {
                val lp = LayoutParams(segWidthPx, segHeightPx)
                if (index > 0) {
                    lp.marginStart = gapPx
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerPx
                    setColor(trackColor())
                }
            }.also(::addView)
        }
    }

    /**
     * Sets the strength level. Passing `null` hides the entire view
     * (Req 6.5).
     *
     * @param strength one of [Strength.Weak] / [Strength.Medium] /
     *                 [Strength.Strong], or `null` to hide.
     */
    fun setStrength(strength: Strength?) {
        if (strength == null) {
            visibility = GONE
            return
        }
        visibility = VISIBLE

        val filledCount: Int = when (strength) {
            Strength.Weak -> 1
            Strength.Medium -> 2
            Strength.Strong -> 3
        }
        val fillColor: Int = when (strength) {
            Strength.Weak -> ContextCompat.getColor(context, R.color.kn_danger)
            Strength.Medium -> ContextCompat.getColor(context, R.color.kn_warning)
            Strength.Strong -> ContextCompat.getColor(context, R.color.kn_success)
        }
        val track: Int = trackColor()

        segmentViews.forEachIndexed { index, segment ->
            val target: Int = if (index < filledCount) fillColor else track
            val bg: GradientDrawable = segment.background as GradientDrawable
            bg.setColor(target)
        }
    }

    /**
     * Returns the resolved color of segment index [position] (0-based).
     *
     * Exposed for unit tests so we can assert that filled segments use
     * the status color and the trailing segments fall back to the track
     * color, without rendering pixels.
     */
    internal fun segmentColorAt(position: Int): Int {
        val drawable: GradientDrawable = segmentViews[position].background as GradientDrawable
        // GradientDrawable.color is a ColorStateList in API 24+; defaultColor
        // is the value previously set via setColor(int).
        return drawable.color?.defaultColor ?: 0
    }

    private fun trackColor(): Int = ContextCompat.getColor(context, R.color.kn_border_strong)

    companion object {
        const val SEGMENT_COUNT: Int = 3
    }
}
