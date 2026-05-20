package io.github.hitoshiichikawa.keynest.autofill.icon

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import androidx.core.content.res.ResourcesCompat
import io.github.hitoshiichikawa.keynest.R

/**
 * Rasterises the caller application's icon for the Autofill dataset
 * presentations.
 *
 * Two consumers:
 *
 * - **Popup (`RemoteViews`)** — needs a fully composed [Bitmap] because
 *   `RemoteViews.setImageViewBitmap` is the only API safe to call across
 *   `Binder` (host-activity theme cannot resolve adaptive icons; the
 *   bitmap carries every visual decision baked-in).
 * - **Inline (`Slice`)** — needs an [Icon]. On the happy path we hand the
 *   IME `Icon.createWithBitmap(callerBitmap)`; on every failure we fall
 *   back to `Icon.createWithResource(R.drawable.ic_key_24)` so the IME
 *   suggestion strip can render the vector with its own theme tint.
 *
 * Popup vs inline fallback contract (design.md §6):
 *
 * - Popup fallback is the "blue tile + key icon" composite bitmap so the
 *   row visually matches the kn_blue_500 tile that the layout XML already
 *   guarantees.
 * - Inline fallback is the raw key vector via `Icon.createWithResource`;
 *   the IME chip is rendered on a translucent suggestion-strip
 *   background, so the blue tile would look out of place baked in.
 *
 * No caching is intentional — `IconLoader` (in-app, lifecycle scoped)
 * is the canonical cache; autofill runs on the system Service which has
 * no Activity lifecycle to bound a cache against, and a single
 * `FillResponse` already shares a per-response bitmap reference via the
 * builder. Requirements: 1.1 / 1.2 / 1.3 / 3.1 / 3.2 / 3.3 (design.md §2).
 */
class AutofillIconRasterizer(
    private val context: Context,
    private val packageManager: PackageManager,
) {

    /**
     * Convenience constructor that pulls the [PackageManager] from the
     * supplied [Context]. Production code uses this — tests use the
     * primary constructor to inject a mocked [PackageManager].
     */
    constructor(context: Context) : this(context, context.packageManager)

    private val resources = context.resources

    /**
     * Default bitmap dimension in pixels. 48dp scaled by the device
     * density; clamped to [MAX_SIZE_PX] so we never blow past the
     * Binder transaction budget on an unusually-high-density device.
     */
    val defaultSizePx: Int = run {
        val scaled = (DEFAULT_ICON_DP * resources.displayMetrics.density).toInt()
        scaled.coerceIn(1, MAX_SIZE_PX)
    }

    /**
     * Resolves the caller package's application icon and rasterises it
     * over the kn_icon_tile_bg blue tile so the popup dataset row keeps
     * the layout's visual contract.
     *
     * The returned bitmap is always non-null:
     *
     * - blank / null `callerPackage` → [loadFallbackBitmap]
     * - `PackageManager.NameNotFoundException` → [loadFallbackBitmap]
     * - any other [RuntimeException] (e.g. SecurityException,
     *   DeadObjectException) → [loadFallbackBitmap]
     */
    fun loadCallerIconBitmap(
        callerPackage: String?,
        sizePx: Int = defaultSizePx,
    ): Bitmap {
        val size = sanitiseSize(sizePx)
        if (callerPackage.isNullOrBlank()) {
            return loadFallbackBitmap(size)
        }
        val drawable: Drawable = try {
            packageManager.getApplicationIcon(callerPackage)
        } catch (_: PackageManager.NameNotFoundException) {
            return loadFallbackBitmap(size)
        } catch (_: RuntimeException) {
            // SecurityException / DeadObjectException etc. from a
            // misbehaving Package service degrade silently per NFR 3.2.
            return loadFallbackBitmap(size)
        }
        return composeOnTile(drawable, size)
    }

    /**
     * Builds the popup-side fallback bitmap: kn_icon_tile_bg painted
     * first, then `ic_key_24` tinted with kn_on_primary in the centre at
     * 20dp / 48dp of the tile (the layout XML proportions).
     */
    fun loadFallbackBitmap(sizePx: Int = defaultSizePx): Bitmap {
        val size = sanitiseSize(sizePx)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBlueTile(canvas, size)
        val keyDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_key_24, null)
        if (keyDrawable != null) {
            val tinted = keyDrawable.mutate()
            tinted.setTint(ResourcesCompat.getColor(resources, R.color.kn_on_primary, null))
            // Match the layout's 20dp / 32dp tile ratio: paint the key
            // at ~62.5% of the tile (20/32 ≈ 0.625) centred.
            val keySize = (size * KEY_TILE_RATIO).toInt().coerceAtLeast(1)
            val left = (size - keySize) / 2
            val top = (size - keySize) / 2
            tinted.setBounds(left, top, left + keySize, top + keySize)
            tinted.draw(canvas)
        }
        return bitmap
    }

    /**
     * Inline-side icon resolver. Mirrors [loadCallerIconBitmap] for the
     * happy path (returns `Icon.createWithBitmap(callerBitmap)` where
     * `callerBitmap` is the caller drawable rasterised at `sizePx`, **
     * without** the blue tile underneath — IME suggestion strips render
     * chips on a translucent background and would look pinched if the
     * tile were baked in).
     *
     * On every failure path the inline fallback uses
     * `Icon.createWithResource(context, R.drawable.ic_key_24)` so the
     * IME can resolve the vector against its own theme tint
     * (requirements §3.3).
     */
    fun loadCallerIconForInline(
        callerPackage: String?,
        sizePx: Int = defaultSizePx,
    ): Icon {
        val size = sanitiseSize(sizePx)
        if (callerPackage.isNullOrBlank()) {
            return fallbackInlineIcon()
        }
        val drawable: Drawable = try {
            packageManager.getApplicationIcon(callerPackage)
        } catch (_: PackageManager.NameNotFoundException) {
            return fallbackInlineIcon()
        } catch (_: RuntimeException) {
            return fallbackInlineIcon()
        }
        val bitmap = rasterise(drawable, size)
        return Icon.createWithBitmap(bitmap)
    }

    private fun fallbackInlineIcon(): Icon =
        Icon.createWithResource(context, R.drawable.ic_key_24)

    private fun composeOnTile(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBlueTile(canvas, sizePx)
        drawCallerDrawable(canvas, drawable, sizePx)
        return bitmap
    }

    /**
     * Rasterise the caller drawable onto a transparent square (no blue
     * tile underneath). Used by the inline path.
     */
    private fun rasterise(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawCallerDrawable(canvas, drawable, sizePx)
        return bitmap
    }

    private fun drawBlueTile(canvas: Canvas, sizePx: Int) {
        val tile = ResourcesCompat.getDrawable(resources, R.drawable.kn_icon_tile_bg, null)
            ?: return
        tile.setBounds(0, 0, sizePx, sizePx)
        tile.draw(canvas)
    }

    /**
     * Draws [drawable] centred onto [canvas]. AdaptiveIconDrawable /
     * VectorDrawable both render correctly when given a square bounds
     * (the framework composes background + foreground layers for us —
     * we deliberately do not apply a custom mask, per requirements
     * "Out of Scope").
     *
     * Plain BitmapDrawables that report sensible intrinsic dimensions
     * are scaled aspect-preserve + center crop so non-square legacy
     * icons do not stretch oddly when stuffed into the square tile.
     */
    private fun drawCallerDrawable(canvas: Canvas, drawable: Drawable, sizePx: Int) {
        val intrinsicW = drawable.intrinsicWidth
        val intrinsicH = drawable.intrinsicHeight
        if (intrinsicW <= 0 || intrinsicH <= 0) {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            return
        }
        // Aspect-preserve center crop: scale so the shorter side fills
        // the tile, then translate so the centre lands at sizePx/2.
        val scale = maxOf(
            sizePx.toFloat() / intrinsicW.toFloat(),
            sizePx.toFloat() / intrinsicH.toFloat(),
        )
        val scaledW = (intrinsicW * scale).toInt()
        val scaledH = (intrinsicH * scale).toInt()
        val left = (sizePx - scaledW) / 2
        val top = (sizePx - scaledH) / 2
        drawable.setBounds(left, top, left + scaledW, top + scaledH)
        drawable.draw(canvas)
    }

    private fun sanitiseSize(sizePx: Int): Int =
        sizePx.coerceIn(1, MAX_SIZE_PX)

    companion object {
        /**
         * Target tile size in dp. Matches the layout's 32dp
         * `kn_icon_tile_sm` tile inflated to 48dp so the rasterised
         * bitmap has enough resolution after RemoteViews IPC and
         * subsequent ImageView upscale (design.md §3).
         */
        const val DEFAULT_ICON_DP: Int = 48

        /**
         * Absolute cap on the bitmap edge length. 192 px keeps a single
         * bitmap below 150 KB (192 × 192 × 4 bytes ≒ 144 KB) so a
         * `FillResponse` carrying N datasets stays comfortably under
         * the ~1 MB Binder transaction limit (design.md §3).
         */
        const val MAX_SIZE_PX: Int = 192

        /**
         * Ratio of the centre key icon's edge to the tile edge. 20/32
         * mirrors the existing layout XML (`20dp` icon inside `32dp`
         * tile via `kn_icon_tile_sm`).
         */
        private const val KEY_TILE_RATIO: Float = 20f / 32f
    }
}
