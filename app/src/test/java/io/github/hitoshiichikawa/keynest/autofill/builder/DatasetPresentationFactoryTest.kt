package io.github.hitoshiichikawa.keynest.autofill.builder

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.util.Size
import android.widget.inline.InlinePresentationSpec
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.autofill.icon.AutofillIconRasterizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behavioural tests for [DatasetPresentationFactory] focusing on the
 * caller-icon wiring introduced by Issue #80.
 *
 * The factory is exercised through a mocked [AutofillIconRasterizer] so
 * the test does not depend on Robolectric's `PackageManager` state. The
 * actual rasterisation contract (drawable → bitmap, fallback bitmap
 * composition, `Icon` type selection) is covered exhaustively by
 * `AutofillIconRasterizerTest`.
 *
 * What we verify here:
 *
 * 1. Popup happy path — `build(...)` with a non-null caller package
 *    invokes the rasterizer and the returned `RemoteViews` is non-null.
 * 2. Popup null-caller path — `build(...)` with `callerPackage = null`
 *    does **not** touch the rasterizer (existing visual is preserved).
 * 3. Inline happy path — `buildInline(...)` calls
 *    `loadCallerIconForInline` with the caller package and the size
 *    derived from `spec.maxSize`.
 * 4. Inline failure path — when the rasterizer returns the resource
 *    `Icon`, the inline presentation is still built without exception.
 * 5. Inline size clamping — `spec.maxSize` smaller than
 *    `defaultSizePx` overrides the default.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DatasetPresentationFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Popup (RemoteViews) -----------------------------------------------

    @Test
    fun build_withCallerPackage_invokesRasterizerAndProducesRemoteViews() {
        val rasterizer = mockk<AutofillIconRasterizer>()
        every { rasterizer.defaultSizePx } returns 96
        val fakeBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        every { rasterizer.loadCallerIconBitmap("com.example.target", any()) } returns fakeBitmap
        val factory = DatasetPresentationFactory(context, rasterizer)

        val views = factory.build(
            label = "Label",
            subtitle = "alice",
            callerPackage = "com.example.target",
        )

        assertThat(views).isNotNull()
        verify(exactly = 1) { rasterizer.loadCallerIconBitmap("com.example.target", any()) }
    }

    @Test
    fun build_withNullCallerPackage_skipsRasterizerForPopup() {
        val rasterizer = mockk<AutofillIconRasterizer>(relaxed = true)
        every { rasterizer.defaultSizePx } returns 96
        val factory = DatasetPresentationFactory(context, rasterizer)

        val views = factory.build(label = "Label", subtitle = "alice", callerPackage = null)

        assertThat(views).isNotNull()
        verify(exactly = 0) { rasterizer.loadCallerIconBitmap(any(), any()) }
    }

    @Test
    fun build_withBlankCallerPackage_skipsRasterizerForPopup() {
        val rasterizer = mockk<AutofillIconRasterizer>(relaxed = true)
        every { rasterizer.defaultSizePx } returns 96
        val factory = DatasetPresentationFactory(context, rasterizer)

        val views = factory.build(label = "Label", subtitle = "alice", callerPackage = "   ")

        assertThat(views).isNotNull()
        verify(exactly = 0) { rasterizer.loadCallerIconBitmap(any(), any()) }
    }

    // --- Inline (Slice) ----------------------------------------------------

    @Test
    fun buildInline_callsSetStartIconWithBitmapIconOnHappyPath() {
        val rasterizer = mockk<AutofillIconRasterizer>()
        every { rasterizer.defaultSizePx } returns 96
        val bitmapIcon = Icon.createWithBitmap(
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
        )
        val sizeSlot = slot<Int>()
        every {
            rasterizer.loadCallerIconForInline(eq("com.example.target"), capture(sizeSlot))
        } returns bitmapIcon
        val factory = DatasetPresentationFactory(context, rasterizer)

        val inline = factory.buildInline(
            label = "Label",
            subtitle = "alice",
            spec = makeSpec(120, 120),
            callerPackage = "com.example.target",
        )

        // Requirement §3.1: setStartIcon must be wired — the cleanest way
        // to assert that without inflating the Slice is to verify the
        // rasterizer was consulted (the factory has no other path to an
        // Icon).
        assertThat(inline).isNotNull()
        verify(exactly = 1) {
            rasterizer.loadCallerIconForInline("com.example.target", any())
        }
        // §3.4: size cap = min(defaultSizePx, min(maxSize.w, maxSize.h)).
        // defaultSizePx = 96 (mocked), maxSize = 120 → expect 96.
        assertThat(sizeSlot.captured).isEqualTo(96)
    }

    @Test
    fun buildInline_withNullCallerPackage_stillCallsRasterizerForFallbackIcon() {
        // Requirement §3.1 + §3.3: even when the caller package is null
        // we MUST call setStartIcon (with the resource fallback icon).
        // That is implemented by passing `null` straight to the
        // rasterizer, which returns `Icon.createWithResource(...)` —
        // the factory does not branch on `callerPackage` for inline.
        val rasterizer = mockk<AutofillIconRasterizer>()
        every { rasterizer.defaultSizePx } returns 96
        val resourceIcon = Icon.createWithResource(
            context,
            io.github.hitoshiichikawa.keynest.R.drawable.ic_key_24,
        )
        every { rasterizer.loadCallerIconForInline(null, any()) } returns resourceIcon
        val factory = DatasetPresentationFactory(context, rasterizer)

        val inline = factory.buildInline(
            label = "Label",
            subtitle = "alice",
            spec = makeSpec(120, 120),
            callerPackage = null,
        )

        assertThat(inline).isNotNull()
        verify(exactly = 1) { rasterizer.loadCallerIconForInline(null, any()) }
    }

    @Test
    fun buildInline_clampsBitmapSizeToSpecMaxSize() {
        val rasterizer = mockk<AutofillIconRasterizer>()
        every { rasterizer.defaultSizePx } returns 192
        val bitmapIcon = Icon.createWithBitmap(
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
        )
        val sizeSlot = slot<Int>()
        every {
            rasterizer.loadCallerIconForInline(any(), capture(sizeSlot))
        } returns bitmapIcon
        val factory = DatasetPresentationFactory(context, rasterizer)

        factory.buildInline(
            label = "L",
            subtitle = "s",
            spec = makeSpec(width = 48, height = 60),
            callerPackage = "com.example.target",
        )

        // min(defaultSizePx=192, min(48, 60)=48) = 48.
        assertThat(sizeSlot.captured).isEqualTo(48)
    }

    @Test
    fun buildInline_treatsZeroMaxSizeAsUnconstrained() {
        // `InlinePresentationSpec.maxSize` is documented as "Size(0, 0)"
        // when the IME does not constrain the chip. The factory must
        // fall back to `defaultSizePx` in that case.
        val rasterizer = mockk<AutofillIconRasterizer>()
        every { rasterizer.defaultSizePx } returns 96
        val bitmapIcon = Icon.createWithBitmap(
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
        )
        val sizeSlot = slot<Int>()
        every {
            rasterizer.loadCallerIconForInline(any(), capture(sizeSlot))
        } returns bitmapIcon
        val factory = DatasetPresentationFactory(context, rasterizer)

        factory.buildInline(
            label = "L",
            subtitle = "s",
            spec = makeSpec(width = 0, height = 0),
            callerPackage = "com.example.target",
        )

        assertThat(sizeSlot.captured).isEqualTo(96)
    }

    @Test
    fun buildInline_returnsNullWhenSpecIsNull() {
        val rasterizer = mockk<AutofillIconRasterizer>(relaxed = true)
        val factory = DatasetPresentationFactory(context, rasterizer)

        val inline = factory.buildInline(
            label = "L",
            subtitle = "s",
            spec = null,
            callerPackage = "com.example.target",
        )

        assertThat(inline).isNull()
        // Spec-less path should not touch the rasterizer either.
        verify(exactly = 0) { rasterizer.loadCallerIconForInline(any(), any()) }
    }

    private fun makeSpec(width: Int, height: Int): InlinePresentationSpec {
        // The constructor was added in API 30 (R) and Robolectric runs
        // under SDK 33 per the @Config above, so this path is safe.
        return InlinePresentationSpec.Builder(
            Size(width, height),
            Size(width, height),
        ).build()
    }
}
