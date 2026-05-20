package io.github.hitoshiichikawa.keynest.autofill.icon

import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [AutofillIconRasterizer]. Each test injects a mocked
 * [PackageManager] via the secondary constructor (the production code
 * paths still rely on the single-arg constructor that pulls the real
 * `PackageManager` from `Context`).
 *
 * The contract under test:
 *
 * - happy path: a `Drawable` from `getApplicationIcon` is rasterised
 *   onto a square `Bitmap` of the expected dimension.
 * - `NameNotFoundException` / `RuntimeException` / blank package /
 *   `null` package all fall back to the popup composite bitmap (popup
 *   path) or `Icon.createWithResource(ic_key_24)` (inline path) —
 *   crucially **never null**.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AutofillIconRasterizerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- loadCallerIconBitmap ----------------------------------------------

    @Test
    fun loadCallerIconBitmap_happyPath_returnsSquareBitmapAtRequestedSize() {
        // Use ColorDrawable rather than mockk so the framework's
        // `Drawable.draw` / `setBounds` paths execute against a real
        // drawable (we are exercising Canvas plumbing, not just the
        // PackageManager call site).
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("com.example.target") } returns ColorDrawable(0xFFFF0000.toInt())
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadCallerIconBitmap("com.example.target", sizePx = 96)

        assertThat(bitmap.width).isEqualTo(96)
        assertThat(bitmap.height).isEqualTo(96)
        verify(exactly = 1) { pm.getApplicationIcon("com.example.target") }
    }

    @Test
    fun loadCallerIconBitmap_nameNotFoundException_returnsFallbackBitmap() {
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("com.example.target") } throws PackageManager.NameNotFoundException()
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadCallerIconBitmap("com.example.target", sizePx = 96)

        // The fallback bitmap is always non-null and square at the
        // requested dimension. We deliberately do not introspect pixel
        // values — the visual contract (blue tile + key icon) is
        // covered by the layout token tests + visual review.
        assertThat(bitmap).isNotNull()
        assertThat(bitmap.width).isEqualTo(96)
        assertThat(bitmap.height).isEqualTo(96)
    }

    @Test
    fun loadCallerIconBitmap_runtimeException_returnsFallbackBitmap() {
        val pm = mockk<PackageManager>()
        // SecurityException is the realistic case: some OEM PackageManager
        // shims throw it for cross-user package queries.
        every { pm.getApplicationIcon("com.example.target") } throws SecurityException("denied")
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadCallerIconBitmap("com.example.target", sizePx = 96)

        assertThat(bitmap).isNotNull()
        assertThat(bitmap.width).isEqualTo(96)
    }

    @Test
    fun loadCallerIconBitmap_nullPackage_returnsFallbackBitmapWithoutTouchingPackageManager() {
        val pm = mockk<PackageManager>()
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadCallerIconBitmap(callerPackage = null, sizePx = 64)

        assertThat(bitmap).isNotNull()
        assertThat(bitmap.width).isEqualTo(64)
        verify(exactly = 0) { pm.getApplicationIcon(any<String>()) }
    }

    @Test
    fun loadCallerIconBitmap_blankPackage_returnsFallbackBitmapWithoutTouchingPackageManager() {
        val pm = mockk<PackageManager>()
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadCallerIconBitmap(callerPackage = "   ", sizePx = 64)

        assertThat(bitmap).isNotNull()
        verify(exactly = 0) { pm.getApplicationIcon(any<String>()) }
    }

    @Test
    fun loadCallerIconBitmap_sizeIsClampedToMaxSizePx() {
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("com.example.target") } returns ColorDrawable(0xFF0000FF.toInt())
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadCallerIconBitmap("com.example.target", sizePx = 4096)

        assertThat(bitmap.width).isAtMost(AutofillIconRasterizer.MAX_SIZE_PX)
        assertThat(bitmap.height).isAtMost(AutofillIconRasterizer.MAX_SIZE_PX)
    }

    // --- loadFallbackBitmap ------------------------------------------------

    @Test
    fun loadFallbackBitmap_returnsNonNullSquareBitmap() {
        val pm = mockk<PackageManager>(relaxed = true)
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadFallbackBitmap(sizePx = 96)

        assertThat(bitmap).isNotNull()
        assertThat(bitmap.width).isEqualTo(96)
        assertThat(bitmap.height).isEqualTo(96)
        verify(exactly = 0) { pm.getApplicationIcon(any<String>()) }
    }

    // --- loadCallerIconForInline ------------------------------------------

    @Test
    fun loadCallerIconForInline_happyPath_returnsBitmapIcon() {
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("com.example.target") } returns ColorDrawable(0xFF00FF00.toInt())
        val rasterizer = AutofillIconRasterizer(context, pm)

        val icon = rasterizer.loadCallerIconForInline("com.example.target", sizePx = 64)

        // `Icon.getType()` is API 23+. TYPE_BITMAP / TYPE_ADAPTIVE_BITMAP are
        // both acceptable on the happy path; we assert it is NOT a resource
        // icon (which would mean the fallback path was wrongly taken).
        assertThat(icon).isNotNull()
        assertThat(icon.type).isAnyOf(
            android.graphics.drawable.Icon.TYPE_BITMAP,
            android.graphics.drawable.Icon.TYPE_ADAPTIVE_BITMAP,
        )
    }

    @Test
    fun loadCallerIconForInline_nameNotFoundException_returnsResourceFallbackIcon() {
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("com.example.target") } throws PackageManager.NameNotFoundException()
        val rasterizer = AutofillIconRasterizer(context, pm)

        val icon = rasterizer.loadCallerIconForInline("com.example.target")

        assertThat(icon.type).isEqualTo(android.graphics.drawable.Icon.TYPE_RESOURCE)
        assertThat(icon.resId).isEqualTo(io.github.hitoshiichikawa.keynest.R.drawable.ic_key_24)
    }

    @Test
    fun loadCallerIconForInline_runtimeException_returnsResourceFallbackIcon() {
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("com.example.target") } throws SecurityException("denied")
        val rasterizer = AutofillIconRasterizer(context, pm)

        val icon = rasterizer.loadCallerIconForInline("com.example.target")

        assertThat(icon.type).isEqualTo(android.graphics.drawable.Icon.TYPE_RESOURCE)
        assertThat(icon.resId).isEqualTo(io.github.hitoshiichikawa.keynest.R.drawable.ic_key_24)
    }

    @Test
    fun loadCallerIconForInline_nullPackage_returnsResourceFallbackIcon() {
        val pm = mockk<PackageManager>()
        val rasterizer = AutofillIconRasterizer(context, pm)

        val icon = rasterizer.loadCallerIconForInline(callerPackage = null)

        assertThat(icon.type).isEqualTo(android.graphics.drawable.Icon.TYPE_RESOURCE)
        assertThat(icon.resId).isEqualTo(io.github.hitoshiichikawa.keynest.R.drawable.ic_key_24)
        verify(exactly = 0) { pm.getApplicationIcon(any<String>()) }
    }

    @Test
    fun loadCallerIconForInline_blankPackage_returnsResourceFallbackIcon() {
        val pm = mockk<PackageManager>()
        val rasterizer = AutofillIconRasterizer(context, pm)

        val icon = rasterizer.loadCallerIconForInline(callerPackage = "   ")

        assertThat(icon.type).isEqualTo(android.graphics.drawable.Icon.TYPE_RESOURCE)
        assertThat(icon.resId).isEqualTo(io.github.hitoshiichikawa.keynest.R.drawable.ic_key_24)
        verify(exactly = 0) { pm.getApplicationIcon(any<String>()) }
    }

    // --- Drawable variants -------------------------------------------------

    @Test
    fun loadCallerIconBitmap_drawableWithoutIntrinsicSize_rendersFullTile() {
        // Some legacy drawables report intrinsicWidth/Height <= 0; we
        // must not crash and must still produce a bitmap of the
        // requested size (design.md §5 fallback branch).
        val zeroSized: Drawable = object : Drawable() {
            override fun draw(canvas: android.graphics.Canvas) { /* no-op */ }
            override fun setAlpha(alpha: Int) { /* no-op */ }
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { /* no-op */ }
            @Deprecated("Required override")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            override fun getIntrinsicWidth(): Int = -1
            override fun getIntrinsicHeight(): Int = 0
        }
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("com.example.target") } returns zeroSized
        val rasterizer = AutofillIconRasterizer(context, pm)

        val bitmap = rasterizer.loadCallerIconBitmap("com.example.target", sizePx = 72)

        assertThat(bitmap.width).isEqualTo(72)
        assertThat(bitmap.height).isEqualTo(72)
    }
}
