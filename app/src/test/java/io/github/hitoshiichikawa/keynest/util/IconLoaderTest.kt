package io.github.hitoshiichikawa.keynest.util

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of [IconLoader.resolve] (the pure resolution path) and
 * [IconLoader.loadInto] (the ImageView binding path with process-wide
 * scope injection).
 *
 * Issue #43 Req 4.1 / 4.2 / 4.3 / 4.4 and Issue #46 Req 2.x. The
 * PackageManager and Resources are mocked with mockk; [android.util.LruCache]
 * is exercised via the real Android stub (Robolectric) because
 * `isReturnDefaultValues = true` would otherwise no-op `LruCache.put` and
 * break the cache-hit assertion.
 *
 * The Resources reference is mocked because [IconLoader] only uses it
 * to look up the kn_blue_500 / kn_on_primary colors and the
 * kn_r_icon_tile dimension — values whose exact numeric form is not
 * material to the cache / fallback / threading logic under test.
 *
 * The [applicationScope] passed in is a [TestScope] backed by an
 * [UnconfinedTestDispatcher] (Issue #46 Req 2.1 verifies that resolves
 * no longer depend on the host ViewHolder's lifecycle owner).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class IconLoaderTest {

    private val resources: Resources = mockk(relaxed = true) {
        every { getColor(any(), any()) } returns 0
        every { getDimension(any()) } returns 0f
    }

    // --- Req 4.1: successful resolve returns PackageManager's Drawable ----

    @Test
    fun resolve_packageInstalled_returnsPackageManagerDrawable() = runTest {
        // Arrange
        val stub: Drawable = mockk(relaxed = true)
        val pm: PackageManager = mockk {
            every { getApplicationIcon("com.example") } returns stub
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Act
        val result = loader.resolve("com.example")

        // Assert
        assertThat(result).isSameInstanceAs(stub)
        verify(exactly = 1) { pm.getApplicationIcon("com.example") }
    }

    // --- Req 4.2: NameNotFoundException -> InitialLetterDrawable ----------

    @Test
    fun resolve_nameNotFound_returnsInitialLetterDrawable() = runTest {
        // Arrange
        val pm: PackageManager = mockk {
            every {
                getApplicationIcon("com.missing")
            } throws PackageManager.NameNotFoundException()
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Act
        val result = loader.resolve("com.missing")

        // Assert
        assertThat(result).isInstanceOf(InitialLetterDrawable::class.java)
        // Verify the computed letter matches the design rule (last segment
        // first char uppercased). The pure helper is covered separately
        // in InitialLetterDrawableTest, this assertion just guards against
        // a future careless rewrite that forwards the wrong package name
        // into computeInitial().
        assertThat(InitialLetterDrawable.computeInitial("com.missing")).isEqualTo("M")
    }

    @Test
    fun resolve_runtimeException_returnsInitialLetterDrawable() = runTest {
        // Arrange: PackageManager can throw SecurityException /
        // DeadObjectException / etc. on misbehaving devices. The Issue 1.4
        // "アイコン取得処理で例外" wording covers these too, so the
        // loader must also map them to the fallback drawable.
        val pm: PackageManager = mockk {
            every { getApplicationIcon("com.broken") } throws SecurityException("denied")
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Act
        val result = loader.resolve("com.broken")

        // Assert
        assertThat(result).isInstanceOf(InitialLetterDrawable::class.java)
    }

    // --- Req 4.3 / NFR 1.2: second call hits the LRU cache ----------------

    @Test
    fun resolve_secondCallSamePackage_usesCacheAndSkipsPackageManager() = runTest {
        // Arrange
        val stub: Drawable = mockk(relaxed = true)
        val pm: PackageManager = mockk {
            every { getApplicationIcon("com.cached") } returns stub
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Act
        val first = loader.resolve("com.cached")
        val second = loader.resolve("com.cached")

        // Assert
        assertThat(first).isSameInstanceAs(stub)
        assertThat(second).isSameInstanceAs(stub)
        verify(exactly = 1) { pm.getApplicationIcon("com.cached") }
    }

    @Test
    fun resolve_fallbackResultIsAlsoCached() = runTest {
        // Arrange: even when the first resolve fell into the fallback
        // branch, the LRU cache should hold the InitialLetterDrawable so
        // the second resolve does not re-invoke PackageManager (which
        // would just throw again). This keeps the cache-hit path 16ms
        // budget intact for the unhappy case too (Req 2.3 / NFR 1.2).
        val pm: PackageManager = mockk {
            every {
                getApplicationIcon("com.missing")
            } throws PackageManager.NameNotFoundException()
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Act
        loader.resolve("com.missing")
        loader.resolve("com.missing")

        // Assert
        verify(exactly = 1) { pm.getApplicationIcon("com.missing") }
    }

    // --- Req 4.4 / Req 1.5: blank / null packageName returns null ---------

    @Test
    fun resolve_blankPackageName_returnsNullWithoutCallingPackageManager() = runTest {
        // Arrange
        val pm: PackageManager = mockk(relaxed = true)
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Act
        val emptyResult = loader.resolve("")
        val whitespaceResult = loader.resolve("   ")
        val nullResult = loader.resolve(null)

        // Assert
        assertThat(emptyResult).isNull()
        assertThat(whitespaceResult).isNull()
        assertThat(nullResult).isNull()
        verify(exactly = 0) { pm.getApplicationIcon(any<String>()) }
    }

    // --- NFR 1.1: LRU cache evicts oldest entry when capacity exceeded ----

    @Test
    fun resolve_cacheCapacityRespected_oldestEvictedFirst() = runTest {
        // Arrange: a cache of capacity 2 so we can exercise eviction
        // without allocating 64 drawables.
        val d1: Drawable = mockk(relaxed = true)
        val d2: Drawable = mockk(relaxed = true)
        val d3: Drawable = mockk(relaxed = true)
        val pm: PackageManager = mockk {
            every { getApplicationIcon("com.a") } returns d1
            every { getApplicationIcon("com.b") } returns d2
            every { getApplicationIcon("com.c") } returns d3
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            cacheCapacity = 2,
        )

        // Act: resolve com.a + com.b -> cache full; resolve com.c evicts
        // com.a (the LRU entry); re-resolving com.a should hit PM again.
        loader.resolve("com.a")
        loader.resolve("com.b")
        loader.resolve("com.c")
        loader.resolve("com.a")

        // Assert: com.a was queried twice (initial + after eviction);
        // com.b / com.c each once.
        verify(exactly = 2) { pm.getApplicationIcon("com.a") }
        verify(exactly = 1) { pm.getApplicationIcon("com.b") }
        verify(exactly = 1) { pm.getApplicationIcon("com.c") }
    }

    // --- Issue #46 Req 2.1: loadInto uses the injected process-wide scope ---

    @Test
    fun loadInto_resolvesWithInjectedScope_independentOfImageViewAttachment() = runTest {
        // Arrange: a fresh ImageView constructed in test code is NOT
        // attached to any window and therefore has no
        // ViewTreeLifecycleOwner. Under Issue #43's implementation
        // findViewTreeLifecycleOwner() returned null and the resolve was
        // silently dropped — the symptom that motivated Issue #46.
        // Issue #46's fix is to inject a process-wide CoroutineScope so
        // attachment status is irrelevant.
        val stub: Drawable = mockk(relaxed = true)
        val pm: PackageManager = mockk {
            every { getApplicationIcon("com.example") } returns stub
        }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = scope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val imageView = ImageView(ApplicationProvider.getApplicationContext())

        // Act
        loader.loadInto(imageView, "com.example")
        advanceUntilIdle()

        // Assert: the drawable was applied even though the ImageView is
        // never attached to a window (no lifecycle owner up the tree).
        assertThat(imageView.drawable).isSameInstanceAs(stub)
        verify(exactly = 1) { pm.getApplicationIcon("com.example") }
    }

    @Test
    fun loadInto_cacheMissResultIsPutIntoLruCache_soSecondBindIsSynchronous() = runTest {
        // Arrange: the first loadInto must populate the cache so the
        // second bind takes the synchronous cache-hit branch (Req 2.3 /
        // NFR 1.2 16ms budget). Issue #43's loadInto path did not write
        // through to the cache; Issue #46 fixes this so repeated scrolls
        // do not re-invoke PackageManager.
        val stub: Drawable = mockk(relaxed = true)
        val pm: PackageManager = mockk {
            every { getApplicationIcon("com.example") } returns stub
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val firstView = ImageView(ctx)
        val secondView = ImageView(ctx)

        // Act
        loader.loadInto(firstView, "com.example")
        advanceUntilIdle()
        loader.loadInto(secondView, "com.example")
        advanceUntilIdle()

        // Assert: both ImageViews got the same drawable, but PM was only
        // queried once (the second bind hit the LruCache).
        assertThat(firstView.drawable).isSameInstanceAs(stub)
        assertThat(secondView.drawable).isSameInstanceAs(stub)
        verify(exactly = 1) { pm.getApplicationIcon("com.example") }
    }

    @Test
    fun loadInto_tagMismatchAfterRebind_doesNotOverwriteRecycledRow() = runTest {
        // Arrange: simulate the ViewHolder recycle race that Issue #43
        // Req 2.4 (and Issue #46 Req 2.3) guards against. After loadInto
        // launches its coroutine, the adapter rebinds the ImageView to a
        // different packageName (or cancels via cancel()); the late
        // result must NOT clobber the newer drawable.
        val stub: Drawable = mockk(relaxed = true)
        val pm: PackageManager = mockk {
            every { getApplicationIcon("com.first") } returns stub
        }
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val imageView = ImageView(ApplicationProvider.getApplicationContext())

        // Act: kick off the resolve, immediately cancel (mimics
        // onViewRecycled), then drain the coroutine queue.
        loader.loadInto(imageView, "com.first")
        loader.cancel(imageView)
        advanceUntilIdle()

        // Assert: cancel() set the drawable to null and cleared the tag.
        // The late resolve completion sees a tag mismatch and does NOT
        // re-apply the stub drawable.
        assertThat(imageView.drawable).isNull()
    }

    @Test
    fun loadInto_blankPackageName_clearsImageView_andDoesNotCallPackageManager() = runTest {
        // Arrange: Req 1.5 — blank / null packageName must clear the
        // ImageView (so the parent's kn_icon_tile_bg shows through) and
        // must NOT spin up an async resolve.
        val pm: PackageManager = mockk(relaxed = true)
        val loader = IconLoader(
            pm = pm,
            resources = resources,
            applicationScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val imageView = ImageView(ApplicationProvider.getApplicationContext())
        // Pre-populate the ImageView with a non-null drawable so we can
        // observe that loadInto clears it.
        imageView.setImageDrawable(mockk<Drawable>(relaxed = true))

        // Act
        loader.loadInto(imageView, "")
        loader.loadInto(imageView, null)
        loader.loadInto(imageView, "   ")
        advanceUntilIdle()

        // Assert
        assertThat(imageView.drawable).isNull()
        verify(exactly = 0) { pm.getApplicationIcon(any<String>()) }
    }
}
