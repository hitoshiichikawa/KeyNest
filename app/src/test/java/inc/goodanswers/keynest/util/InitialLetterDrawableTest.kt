package inc.goodanswers.keynest.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-helper behaviour of [InitialLetterDrawable.computeInitial]
 * (Issue #43 Req 1.4 supporting unit test).
 *
 * The helper is intentionally pure so it can be exercised without
 * spinning up Robolectric; it has no Android dependencies. The visual
 * draw path is covered indirectly via [IconLoaderTest] (the fallback
 * branch returns an [InitialLetterDrawable] instance).
 */
class InitialLetterDrawableTest {

    @Test
    fun computeInitial_returnsUppercaseLastSegmentFirstChar() {
        // Arrange / Act / Assert: representative package names cited in
        // requirements.md > 確認事項 (1) and design.md.
        assertThat(InitialLetterDrawable.computeInitial("inc.goodanswers.keynest")).isEqualTo("K")
        assertThat(InitialLetterDrawable.computeInitial("com.android.chrome")).isEqualTo("C")
        assertThat(InitialLetterDrawable.computeInitial("org.mozilla.firefox")).isEqualTo("F")
    }

    @Test
    fun computeInitial_singleSegmentPackage_usesItsFirstChar() {
        // Arrange / Act / Assert
        assertThat(InitialLetterDrawable.computeInitial("foo")).isEqualTo("F")
        assertThat(InitialLetterDrawable.computeInitial("a")).isEqualTo("A")
    }

    @Test
    fun computeInitial_alreadyUppercase_returnsAsIs() {
        // Arrange / Act / Assert
        assertThat(InitialLetterDrawable.computeInitial("com.Example.Keynest")).isEqualTo("K")
    }

    @Test
    fun computeInitial_unresolvableLastSegment_returnsQuestionMark() {
        // Arrange / Act / Assert: empty, dot-only, trailing-dot, whitespace.
        assertThat(InitialLetterDrawable.computeInitial("")).isEqualTo("?")
        assertThat(InitialLetterDrawable.computeInitial(".")).isEqualTo("?")
        assertThat(InitialLetterDrawable.computeInitial("com.")).isEqualTo("?")
        assertThat(InitialLetterDrawable.computeInitial("   ")).isEqualTo("?")
    }

    @Test
    fun computeInitial_digitLastSegment_returnsDigit() {
        // Arrange / Act / Assert: package names ending in a numeric segment
        // (e.g. internal QA builds) should not collapse to '?' — the digit
        // is a valid identifying glyph.
        assertThat(InitialLetterDrawable.computeInitial("com.example.7zip")).isEqualTo("7")
    }

    // --- Issue #46 Req 3.1 / 3.2: intrinsic size ----------------------------

    @Test
    fun intrinsicWidth_returnsPositivePxValuePassedAtConstruction() {
        // Arrange: the InitialLetterDrawable must not fall back to the
        // Drawable default of -1 for intrinsic dimensions, otherwise
        // ImageView.scaleType=fitCenter collapses the drawing area to 0x0
        // and the fallback letter never appears on top of the parent tile.
        val drawable = InitialLetterDrawable(
            letter = "K",
            tileColor = 0xFF0000FF.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            cornerRadiusPx = 12f,
            intrinsicSizePx = 132,
        )

        // Act
        val width = drawable.intrinsicWidth

        // Assert
        assertThat(width).isEqualTo(132)
        assertThat(width).isGreaterThan(0)
    }

    @Test
    fun intrinsicHeight_returnsPositivePxValuePassedAtConstruction() {
        // Arrange: the drawable is intentionally square so width and
        // height share the same intrinsicSizePx value (the parent tile
        // FrameLayout in all 3 layouts is square).
        val drawable = InitialLetterDrawable(
            letter = "K",
            tileColor = 0xFF0000FF.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            cornerRadiusPx = 12f,
            intrinsicSizePx = 132,
        )

        // Act
        val height = drawable.intrinsicHeight

        // Assert
        assertThat(height).isEqualTo(132)
        assertThat(height).isGreaterThan(0)
    }

    @Test
    fun intrinsicSize_isNotNegativeOne_evenForSmallTilePx() {
        // Arrange: 32dp tile ≈ 32px at 1x density; the smallest tile used
        // by the 3 affected layouts. The intrinsic value must still be a
        // positive integer (not -1, which is the Drawable default).
        val drawable = InitialLetterDrawable(
            letter = "?",
            tileColor = 0xFF0000FF.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            cornerRadiusPx = 12f,
            intrinsicSizePx = 32,
        )

        // Act / Assert
        assertThat(drawable.intrinsicWidth).isNotEqualTo(-1)
        assertThat(drawable.intrinsicHeight).isNotEqualTo(-1)
        assertThat(drawable.intrinsicWidth).isGreaterThan(0)
        assertThat(drawable.intrinsicHeight).isGreaterThan(0)
    }
}
