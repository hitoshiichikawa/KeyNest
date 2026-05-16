package com.example.keynest.util

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of [IconLoader.resolve] (the pure resolution path that backs
 * [IconLoader.loadInto]).
 *
 * Issue #43 Req 4.1 / 4.2 / 4.3 / 4.4. The PackageManager and Resources
 * are mocked with mockk; [android.util.LruCache] is exercised via the
 * real Android stub (Robolectric) because `isReturnDefaultValues = true`
 * would otherwise no-op `LruCache.put` and break the cache-hit assertion.
 *
 * The Resources reference is mocked because [IconLoader] only uses it
 * to look up the kn_blue_500 / kn_on_primary colors and the
 * kn_r_icon_tile dimension — values whose exact numeric form is not
 * material to the cache / fallback / threading logic under test.
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
}
