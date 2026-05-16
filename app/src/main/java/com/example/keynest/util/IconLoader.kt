package com.example.keynest.util

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import com.example.keynest.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared resolver that paints each row's app icon (the kn_blue_500 tile
 * is left in the layout as a fallback background; this loader paints the
 * actual [Drawable] on top via `ImageView.setImageDrawable`).
 *
 * Issue #43 / Phase 2 follow-up, Issue #46 hotfix. Centralises the
 * [PackageManager.getApplicationIcon] call + initial-letter fallback +
 * LRU caching so that:
 *   - [com.example.keynest.ui.list.CredentialListAdapter],
 *   - [com.example.keynest.ui.list.RecentlyUsedCarouselAdapter] and
 *   - [com.example.keynest.ui.edit.PackagePickerBottomSheet]
 *     (`SectionAdapter` -> `RowVH`)
 *   share a single cache and a single race-prevention policy.
 *
 * Threading model:
 *   - [loadInto] is called from the main thread (RecyclerView bind path).
 *   - cache hit -> synchronous `setImageDrawable` (Req 2.3 / NFR 1.2,
 *     16ms budget never exceeded because this is just an [LruCache.get]
 *     + a single `ImageView` mutation).
 *   - cache miss -> coroutine on [applicationScope] which switches to
 *     [ioDispatcher] for the PackageManager call (NFR 1.3); on completion
 *     the result is applied back on the main thread iff the `ImageView`'s
 *     [R.id.icon_loader_request_tag] still equals the originally
 *     requested package name (Req 2.4 race prevention against ViewHolder
 *     recycling).
 *
 * Process-wide scope (Issue #46 Req 2.x):
 *   - Previously this class depended on
 *     `imageView.findViewTreeLifecycleOwner()?.lifecycleScope`. When the
 *     ViewHolder's ImageView was not yet attached at bind time
 *     (RecyclerView pre-warm / pooled rebind), `findViewTreeLifecycleOwner`
 *     returned `null`, the early-return silently dropped the resolve, and
 *     the row stayed on the kn_blue_500 background tile only.
 *   - The hotfix takes a single, process-wide [CoroutineScope] (held by
 *     [com.example.keynest.di.ServiceLocator] with
 *     `SupervisorJob() + Dispatchers.Main.immediate`) so resolves run
 *     regardless of attach state. The Req 2.4 tag check still guards
 *     against ViewHolder recycle races, and `SupervisorJob` keeps a
 *     single failed resolve from cancelling siblings.
 *
 * Cache:
 *   - [LruCache] of size [cacheCapacity] (defaults to 64 per NFR 1.1).
 *   - Holds both real `getApplicationIcon` results and
 *     [InitialLetterDrawable] fallbacks so the unhappy path also enjoys
 *     16ms cache-hit returns on subsequent binds (otherwise scrolling
 *     past a missing package would re-throw `NameNotFoundException`
 *     every frame).
 *
 * Diagnostic logs (Issue #46 Req 4.x):
 *   - All routes log at most one line tagged [LOG_TAG] so a Logcat
 *     `tag:IconLoader` filter is enough to isolate cache hit / miss /
 *     success / fallback / cancel paths. The body intentionally contains
 *     only the packageName + a result classifier (no user content,
 *     signature SHA, ciphertext etc.) per Req 4.6 / NFR 3.x.
 *   - Normal paths use [Log.d]; the unexpected-cancel / exception path
 *     uses [Log.w] so production filter rules can keep noise low
 *     (NFR 2.1 / 2.2).
 *
 * Out of scope (per requirements.md > Out of Scope):
 *   - Coil / Glide / external image loaders.
 *   - Web favicon / icon-pack resolution.
 *   - Adaptive-icon custom mask drawing (we hand the framework `Drawable`
 *     straight to `ImageView.setImageDrawable`).
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3,
 * 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, NFR 1.1, NFR 1.2, NFR 1.3, NFR 2.1,
 * NFR 2.2, NFR 3.1, NFR 3.2.
 */
class IconLoader(
    private val pm: PackageManager,
    private val resources: Resources,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
    private val fallbackIntrinsicSizePx: Int = DEFAULT_FALLBACK_INTRINSIC_SIZE_PX,
) {

    // LruCache is thread-safe internally; we never expose it. Capacity is
    // measured in entry count (each entry being a Drawable reference).
    private val cache: LruCache<String, Drawable> = LruCache(cacheCapacity)

    /**
     * Bind the icon for [packageName] into [imageView].
     *
     *   * Blank or null `packageName` -> clear the imageView (no fallback
     *     drawing; the kn_icon_tile_bg behind it stays visible, Req 1.5).
     *   * Cache hit -> synchronous `setImageDrawable` (Req 2.3 / NFR 1.2).
     *   * Cache miss -> resolve on [applicationScope] (process-wide,
     *     attachment-independent); on success the result is applied iff
     *     the imageView's tag still equals the original packageName
     *     (Req 2.4).
     *
     * Must be called from the main thread.
     */
    fun loadInto(imageView: ImageView, packageName: String?) {
        if (packageName.isNullOrBlank()) {
            // Req 1.5: no fallback drawing — leave the bg-only tile.
            imageView.setTag(R.id.icon_loader_request_tag, null)
            imageView.setImageDrawable(null)
            return
        }

        Log.d(LOG_TAG, "loadInto: request pkg=$packageName")
        imageView.setTag(R.id.icon_loader_request_tag, packageName)

        val cached = cache.get(packageName)
        if (cached != null) {
            Log.d(LOG_TAG, "loadInto: cacheHit pkg=$packageName")
            imageView.setImageDrawable(cached)
            return
        }

        // While resolving, clear any leftover drawable so the row does
        // not flash the previous (recycled) ViewHolder's icon. The
        // background tile (@drawable/kn_icon_tile_bg) remains visible
        // because it sits on the parent FrameLayout.
        imageView.setImageDrawable(null)

        applicationScope.launch {
            val drawable = withContext(ioDispatcher) {
                resolveOrFallback(packageName)
            }
            cache.put(packageName, drawable)
            // Race-prevention: only apply if this ImageView is still
            // bound to the same packageName (Req 2.4 / Issue #43).
            if (imageView.getTag(R.id.icon_loader_request_tag) == packageName) {
                imageView.setImageDrawable(drawable)
            } else {
                // Tag mismatch: the ViewHolder was recycled / re-bound
                // before the resolve completed. The cached drawable is
                // still useful for a future bind, but we surface this so
                // a repeating "icon never appears" symptom in the field
                // can be distinguished from a resolve failure (Req 4.5).
                Log.w(LOG_TAG, "loadInto: cancelledByRecycle pkg=$packageName")
            }
        }
    }

    /**
     * Marks the imageView as no longer interested in any in-flight
     * resolve. Adapters MUST call this from `onViewRecycled` so a result
     * that arrives after recycling is not applied to the now-rebound row
     * (Req 2.4).
     */
    fun cancel(imageView: ImageView) {
        imageView.setTag(R.id.icon_loader_request_tag, null)
        imageView.setImageDrawable(null)
    }

    /**
     * Pure resolution path. Exposed for unit tests so the cache /
     * fallback / threading logic can be exercised without inflating an
     * actual `ImageView`. Internally jumps to [ioDispatcher] for the
     * PackageManager call so callers from the main thread do not have
     * to wrap each invocation in `withContext`.
     *
     *   - Blank or null `packageName` -> `null` (Req 1.5).
     *   - Cache hit -> the cached drawable, no PackageManager call
     *     (Req 4.2 / NFR 1.2).
     *   - Cache miss + success -> the framework `Drawable` returned by
     *     `PackageManager.getApplicationIcon` (Req 4.3).
     *   - Cache miss + `NameNotFoundException` or any [RuntimeException]
     *     -> [InitialLetterDrawable] (Req 1.4 / Req 4.4). The fallback
     *     is ALSO cached so repeated binds against a missing package do
     *     not re-throw every frame.
     */
    suspend fun resolve(packageName: String?): Drawable? {
        if (packageName.isNullOrBlank()) return null
        cache.get(packageName)?.let {
            Log.d(LOG_TAG, "resolve: cacheHit pkg=$packageName")
            return it
        }
        val resolved = withContext(ioDispatcher) { resolveOrFallback(packageName) }
        cache.put(packageName, resolved)
        return resolved
    }

    /**
     * The PM call + fallback synthesis, invoked on the IO dispatcher.
     * Catches [PackageManager.NameNotFoundException] explicitly per
     * Req 1.4, and also the broader [RuntimeException] umbrella so that
     * device-specific [SecurityException] / `DeadObjectException` etc.
     * from misbehaving Package services degrade to the fallback letter
     * instead of crashing the host adapter (Req 4.4 / Req 4.5).
     */
    private fun resolveOrFallback(packageName: String): Drawable {
        return try {
            val icon = pm.getApplicationIcon(packageName)
            Log.d(LOG_TAG, "resolveOrFallback: success pkg=$packageName")
            icon
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(
                LOG_TAG,
                "resolveOrFallback: fallback pkg=$packageName reason=${e.javaClass.simpleName}",
            )
            buildFallback(packageName)
        } catch (e: RuntimeException) {
            Log.w(
                LOG_TAG,
                "resolveOrFallback: fallback pkg=$packageName reason=${e.javaClass.simpleName}",
            )
            buildFallback(packageName)
        }
    }

    private fun buildFallback(packageName: String): Drawable {
        // Theme is intentionally null: the colors we look up are static
        // Resources entries that do not vary by theme attribute.
        @Suppress("DEPRECATION")
        val tileColor = resources.getColor(R.color.kn_blue_500, null)

        @Suppress("DEPRECATION")
        val textColor = resources.getColor(R.color.kn_on_primary, null)

        val cornerRadiusPx = resources.getDimension(R.dimen.kn_r_icon_tile)
        val letter = InitialLetterDrawable.computeInitial(packageName)
        return InitialLetterDrawable(
            letter = letter,
            tileColor = tileColor,
            textColor = textColor,
            cornerRadiusPx = cornerRadiusPx,
            intrinsicSizePx = fallbackIntrinsicSizePx,
        )
    }

    companion object {
        /**
         * Default LRU cache capacity. NFR 1.1 fixes this at 64 — adapters
         * never override it; the constructor parameter only exists so
         * unit tests can exercise the eviction policy with a small cache.
         */
        const val DEFAULT_CACHE_CAPACITY: Int = 64

        /**
         * Default intrinsic size (in px) used by the fallback
         * [InitialLetterDrawable] when no explicit value is passed. Set to
         * 132 px so that on every screen density above ldpi the value is
         * comfortably larger than the widest tile in use
         * (`kn_icon_tile_lg` = 44dp, i.e. 66 px at hdpi, 88 px at xhdpi,
         * 132 px at xxhdpi). The exact value only matters insofar as it
         * is positive — `ImageView.scaleType=fitCenter` then scales the
         * drawable to its measured bounds (the tile FrameLayout sets the
         * final visual size).
         *
         * Tag for Req 3.1 / 3.2.
         */
        const val DEFAULT_FALLBACK_INTRINSIC_SIZE_PX: Int = 132

        /**
         * Logcat tag used by every diagnostic log line emitted by this
         * class (Issue #46 Req 4.x). Kept short and constant so a
         * `tag:IconLoader` filter is a one-step Logcat triage.
         */
        const val LOG_TAG: String = "IconLoader"
    }
}
