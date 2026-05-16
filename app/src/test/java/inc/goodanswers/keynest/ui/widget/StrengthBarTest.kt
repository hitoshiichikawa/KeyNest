package inc.goodanswers.keynest.ui.widget

import android.view.View
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import inc.goodanswers.keynest.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of [StrengthBar]. Backs Issue #29 Req 6.1 / 6.2 / 6.3 / 6.4 /
 * 6.5 (three-segment password-strength bar visualisation).
 *
 * Verifies:
 *  - segment count and dp dimensions resolved from kn_strength_seg_* tokens
 *    (Req 6.1)
 *  - per-strength filled-segment count and fill color (Req 6.2 / 6.3 /
 *    6.4)
 *  - the unfilled segments fall back to `@color/kn_border_strong` (track)
 *  - `setStrength(null)` hides the entire view (Req 6.5 — no placeholder
 *    color rendered when domain has no strength)
 *  - default visibility is GONE before any strength is bound (Req 6.5
 *    boundary: rows that never call setStrength must not show the bar)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class StrengthBarTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val successColor get() = ContextCompat.getColor(context, R.color.kn_success)
    private val warningColor get() = ContextCompat.getColor(context, R.color.kn_warning)
    private val dangerColor get() = ContextCompat.getColor(context, R.color.kn_danger)
    private val trackColor get() = ContextCompat.getColor(context, R.color.kn_border_strong)

    // ---- Req 6.1: segment count + dp dimensions -----------------------------

    @Test
    fun strengthBar_alwaysHasThreeSegments() {
        // Arrange / Act
        val bar = StrengthBar(context)

        // Assert
        assertThat(bar.childCount).isEqualTo(StrengthBar.SEGMENT_COUNT)
        assertThat(StrengthBar.SEGMENT_COUNT).isEqualTo(3)
    }

    @Test
    fun strengthBar_eachSegmentMatchesKnStrengthDimens() {
        // Req 6.1: segment width / height / gap come from the design tokens
        // imported in Phase 1. Asserting on the raw pixel values catches
        // accidental hard-coded dp drift.
        val expectedWidthPx = context.resources.getDimensionPixelSize(R.dimen.kn_strength_seg_w)
        val expectedHeightPx = context.resources.getDimensionPixelSize(R.dimen.kn_strength_seg_h)
        val expectedGapPx = context.resources.getDimensionPixelSize(R.dimen.kn_strength_seg_gap)

        val bar = StrengthBar(context)

        repeat(StrengthBar.SEGMENT_COUNT) { index ->
            val child = bar.getChildAt(index)
            val lp = child.layoutParams as android.widget.LinearLayout.LayoutParams
            assertThat(lp.width).isEqualTo(expectedWidthPx)
            assertThat(lp.height).isEqualTo(expectedHeightPx)
            // Only segments 1+ carry a leading gap (segment 0 has no
            // preceding segment).
            val expectedMargin = if (index == 0) 0 else expectedGapPx
            assertThat(lp.marginStart).isEqualTo(expectedMargin)
        }
    }

    // ---- Default visibility (Req 6.5 boundary) ------------------------------

    @Test
    fun strengthBar_isHiddenByDefault_beforeAnyStrengthIsBound() {
        val bar = StrengthBar(context)

        assertThat(bar.visibility).isEqualTo(View.GONE)
    }

    // ---- Req 6.2: strong → all three filled with success --------------------

    @Test
    fun setStrengthStrong_fillsAllSegmentsWithSuccess() {
        val bar = StrengthBar(context)

        bar.setStrength(StrengthBar.Strength.Strong)

        assertThat(bar.visibility).isEqualTo(View.VISIBLE)
        assertThat(bar.segmentColorAt(0)).isEqualTo(successColor)
        assertThat(bar.segmentColorAt(1)).isEqualTo(successColor)
        assertThat(bar.segmentColorAt(2)).isEqualTo(successColor)
    }

    // ---- Req 6.3: medium → 2 warning + 1 track ------------------------------

    @Test
    fun setStrengthMedium_fillsTwoWithWarningAndTrailingWithTrack() {
        val bar = StrengthBar(context)

        bar.setStrength(StrengthBar.Strength.Medium)

        assertThat(bar.visibility).isEqualTo(View.VISIBLE)
        assertThat(bar.segmentColorAt(0)).isEqualTo(warningColor)
        assertThat(bar.segmentColorAt(1)).isEqualTo(warningColor)
        assertThat(bar.segmentColorAt(2)).isEqualTo(trackColor)
    }

    // ---- Req 6.4: weak → 1 danger + 2 track ---------------------------------

    @Test
    fun setStrengthWeak_fillsOneWithDangerAndTrailingPairWithTrack() {
        val bar = StrengthBar(context)

        bar.setStrength(StrengthBar.Strength.Weak)

        assertThat(bar.visibility).isEqualTo(View.VISIBLE)
        assertThat(bar.segmentColorAt(0)).isEqualTo(dangerColor)
        assertThat(bar.segmentColorAt(1)).isEqualTo(trackColor)
        assertThat(bar.segmentColorAt(2)).isEqualTo(trackColor)
    }

    // ---- Req 6.5: null → hide ------------------------------------------------

    @Test
    fun setStrengthNull_hidesTheBar() {
        // Arrange: first set a real strength so we know the bar can be VISIBLE.
        val bar = StrengthBar(context)
        bar.setStrength(StrengthBar.Strength.Strong)
        assertThat(bar.visibility).isEqualTo(View.VISIBLE)

        // Act
        bar.setStrength(null)

        // Assert: Req 6.5 — when the domain cannot supply a strength we must
        // not render the bar at all (no placeholder color).
        assertThat(bar.visibility).isEqualTo(View.GONE)
    }

    // ---- Transition: re-binding overrides previous fill ---------------------

    @Test
    fun setStrengthStrong_thenWeak_overridesPreviouslyFilledSegments() {
        // Defensive: RecyclerView rebinds the same ViewHolder against
        // different items. The bar must reflect only the latest call,
        // not an aggregate of previous strengths.
        val bar = StrengthBar(context)

        bar.setStrength(StrengthBar.Strength.Strong)
        bar.setStrength(StrengthBar.Strength.Weak)

        assertThat(bar.segmentColorAt(0)).isEqualTo(dangerColor)
        assertThat(bar.segmentColorAt(1)).isEqualTo(trackColor)
        assertThat(bar.segmentColorAt(2)).isEqualTo(trackColor)
    }
}
