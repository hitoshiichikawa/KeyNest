package inc.goodanswers.keynest.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * Fallback drawable that paints a single uppercase letter on the KeyNest
 * icon tile (kn_blue_500 fill, kn_r_icon_tile corners) when the real app
 * icon could not be resolved (Req 1.4).
 *
 * The letter is computed from the package name's last segment, e.g.
 * `inc.goodanswers.keynest` -> `K`, `com.android.chrome` -> `C`. When the
 * package name has no resolvable last-segment letter, `?` is used so the
 * tile is never empty (Req 1.4 broader interpretation per Architect note).
 *
 * The drawable is pure / immutable: all visual state is captured at
 * construction time and the [draw] implementation never mutates internal
 * state. The same instance can be safely shared between multiple
 * [android.widget.ImageView] instances (NFR 1.1 cache sharing).
 *
 * Token mapping:
 *   - tile color: `@color/kn_blue_500`
 *   - text color: `@color/kn_on_primary` (#FFFFFF)
 *   - corner radius: `@dimen/kn_r_icon_tile` (12dp)
 *
 * Architectural intent:
 *   - Option A (Drawable subclass) is chosen over Option B (TextView
 *     swap) because the resulting object lives in the same
 *     `LruCache<String, Drawable>` as real `AdaptiveIconDrawable` results,
 *     so the adapter side only has to call `ImageView.setImageDrawable`
 *     for either branch (Req 1.1 / 1.4 unified surface).
 *
 * Intrinsic size (Issue #46 Req 3.x):
 *   - [getIntrinsicWidth] / [getIntrinsicHeight] return [intrinsicSizePx]
 *     (a positive px integer) so that `ImageView.scaleType=fitCenter` can
 *     compute a non-empty target rect. With the previous default `-1`
 *     return, `fitCenter` collapsed the drawing area to 0×0 and the row
 *     showed only the parent's `@drawable/kn_icon_tile_bg` tile with no
 *     letter on top (Req 3.1 / 3.2).
 *   - The actual rendered size is dictated by the [ImageView]'s
 *     measured bounds (via `Drawable.setBounds`), not by [intrinsicSizePx].
 *     The intrinsic value only needs to be positive and aspect-correct so
 *     `fitCenter` produces a square bounds matching the host tile. The
 *     45% short-edge letter ratio (Issue #43 Req 3.x) is preserved
 *     because the [draw] path keeps using `bounds.width()/.height()` for
 *     glyph sizing (Req 3.3).
 *
 * Requirements: 1.4, 3.1, 3.2, 3.3, NFR 2.1
 */
internal class InitialLetterDrawable(
    private val letter: String,
    private val tileColor: Int,
    private val textColor: Int,
    private val cornerRadiusPx: Float,
    private val intrinsicSizePx: Int = DEFAULT_INTRINSIC_SIZE_PX,
) : Drawable() {

    private val tilePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tileColor
        style = Paint.Style.FILL
    }

    private val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        // Sans-serif bold matches Text.KeyNest.Body / Mono visual weight on
        // the existing kn_blue_500 tile.
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val rectF = RectF()
    private val textBounds = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        rectF.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, tilePaint)

        // Size the letter to ~45% of the tile's short edge per design.md
        // (44dp tile -> ~20sp, 36dp -> ~16sp, 32dp -> ~14sp). We rely on
        // the Drawable bounds in px because Drawable has no Resources
        // context to convert dp; the px-from-bounds approach is independent
        // of screen density and stays consistent with the tile size set in
        // each layout.
        val shortEdge = minOf(b.width(), b.height()).toFloat()
        textPaint.textSize = shortEdge * TEXT_SIZE_RATIO

        // Measure with the actual letter so an uppercase 'I' and an 'M'
        // both land vertically centered. Paint.getTextBounds populates a
        // tight rect we can use to offset from the baseline.
        textPaint.getTextBounds(letter, 0, letter.length, textBounds)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY() + textBounds.height() / 2f - textBounds.bottom
        canvas.drawText(letter, cx, cy, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        tilePaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        tilePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int {
        // tilePaint covers the whole bounds, so opacity is dictated by it.
        // (Returning OPAQUE would be incorrect because corners are
        // anti-aliased away.)
        return PixelFormat.TRANSLUCENT
    }

    /**
     * Issue #46 Req 3.1 / 3.2: return a positive px size so that
     * `ImageView` with `scaleType=fitCenter` can compute a non-empty
     * destination rect when this drawable is applied via
     * `setImageDrawable`. Returning `-1` (the [Drawable] default) caused
     * `fitCenter` to collapse the drawing area to 0×0, leaving the parent
     * tile background uncovered.
     */
    override fun getIntrinsicWidth(): Int = intrinsicSizePx

    /** See [getIntrinsicWidth]. */
    override fun getIntrinsicHeight(): Int = intrinsicSizePx

    companion object {
        /**
         * Ratio of tile short-edge taken by the letter glyph. 0.45 matches
         * the JSX `IconTile` letter size in design/screens/screens-1.jsx
         * (the letter visually occupies just under half the tile).
         */
        internal const val TEXT_SIZE_RATIO: Float = 0.45f

        /**
         * Default intrinsic size in px (Issue #46 Req 3.1). 132 px is the
         * widest tile in use (`kn_icon_tile_lg` = 44dp at xxhdpi).
         * `ImageView.scaleType=fitCenter` will scale the drawable down to
         * the actual `ImageView` bounds, so the absolute value only matters
         * insofar as it is positive (the previous Drawable default `-1`
         * collapsed the destination rect to 0×0).
         */
        internal const val DEFAULT_INTRINSIC_SIZE_PX: Int = 132

        /**
         * Pure helper: compute the single-character fallback for the given
         * package name. Returns the uppercase form of the first letter of
         * the package's last segment, or `"?"` when no usable letter exists
         * (e.g. `""`, `"."`, `"  "`).
         *
         * Pure / deterministic / Resources-free so unit tests can call this
         * directly without spinning up Robolectric.
         */
        fun computeInitial(packageName: String): String {
            val lastSegment = packageName.substringAfterLast('.').trim()
            val firstChar = lastSegment.firstOrNull()
            return if (firstChar != null && firstChar.isLetterOrDigit()) {
                firstChar.uppercaseChar().toString()
            } else {
                FALLBACK_LETTER
            }
        }

        private const val FALLBACK_LETTER: String = "?"
    }
}
