package io.github.hitoshiichikawa.keynest.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.BuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies [AppInfoProvider.get] reads `versionName` / `versionCode` from
 * the installed package metadata.
 *
 * Issue #10 Req 5.1 / Issue #139.
 *
 * 期待値は [BuildConfig] と突き合わせる。比較対象（PackageManager 経由の
 * PackageInfo）と比較元（コンパイル時定数）は別経路なので tautology には
 * ならず、リリースで versionName / versionCode が変わってもテストの手更新が
 * 不要になる（#132 の 1.1.0 bump でハードコード期待値が腐った再発防止）。
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

        // Assert: PackageManager 経由の値がビルド宣言（BuildConfig）と一致する。
        assertThat(info.versionName).isEqualTo(BuildConfig.VERSION_NAME)
        assertThat(info.versionCode).isEqualTo(BuildConfig.VERSION_CODE.toLong())
    }

    @Test
    fun get_versionNameIsNotBlank() {
        // Arrange: 空文字 fallback（versionName=null 時）が常態化していないこと
        // を別観点で押さえる（BuildConfig 比較が両辺空で偽陽性 pass になる事故の保険）。
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = AppInfoProvider(context)

        // Act
        val info = provider.get()

        // Assert
        assertThat(info.versionName).isNotEmpty()
    }
}
