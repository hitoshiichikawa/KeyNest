package com.example.keynest.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies [AppInfoProvider.get] reads `versionName` from the installed
 * package metadata.
 *
 * Issue #10 Req 5.1.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AppInfoProviderTest {

    @Test
    fun get_returnsVersionNameFromBuildGradle() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = AppInfoProvider(context)

        // Act
        val info = provider.get()

        // Assert: versionName from app/build.gradle.kts (currently "0.1.0").
        assertThat(info.versionName).isEqualTo("0.1.0")
        // versionCode currently 1.
        assertThat(info.versionCode).isEqualTo(1L)
    }
}
