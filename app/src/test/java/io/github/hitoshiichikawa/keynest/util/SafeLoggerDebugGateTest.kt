package io.github.hitoshiichikawa.keynest.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.BuildConfig
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Issue #137: [SafeLogger.debug] が BuildConfig.DEBUG ゲートで制御される
 * ことの検証。release ビルド（gate=false）では DEBUG 行が logcat に
 * 一切出ないことが、IconLoader 等の pkg 名入り debug ログを release から
 * 落とす根拠になる（R8/minify は無効のため、このゲートが唯一の機構）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SafeLoggerDebugGateTest {

    @After
    fun tearDown() {
        // 他テストへ release シミュレーションを漏らさない。
        SafeLogger.debugLogsEnabled = BuildConfig.DEBUG
        ShadowLog.clear()
    }

    @Test
    fun debug_whenGateDisabled_emitsNothing() {
        // Arrange: release ビルド相当（BuildConfig.DEBUG=false）を再現
        SafeLogger.debugLogsEnabled = false
        ShadowLog.clear()

        // Act
        SafeLogger.debug(tag = "GateTest", message = "should-not-appear pkg=com.example.secret")

        // Assert
        val lines = ShadowLog.getLogsForTag("GateTest")
        assertThat(lines).isEmpty()
    }

    @Test
    fun debug_whenGateEnabled_emitsMessage() {
        // Arrange: debug ビルド相当
        SafeLogger.debugLogsEnabled = true
        ShadowLog.clear()

        // Act
        SafeLogger.debug(tag = "GateTest", message = "visible-in-debug")

        // Assert
        val lines = ShadowLog.getLogsForTag("GateTest")
        assertThat(lines).hasSize(1)
        assertThat(lines.single().msg).isEqualTo("visible-in-debug")
    }

    @Test
    fun warnAndError_areNotAffectedByDebugGate() {
        // Arrange: gate を落としても WARN / ERROR は出続ける（運用診断は
        // release でも必要。出してよい内容かは各呼び出し側の責務）。
        SafeLogger.debugLogsEnabled = false
        ShadowLog.clear()

        // Act
        SafeLogger.warn(tag = "GateTest", message = "warn-stays")
        SafeLogger.error(tag = "GateTest", message = "error-stays")

        // Assert
        val lines = ShadowLog.getLogsForTag("GateTest")
        assertThat(lines.map { it.msg }).containsExactly("warn-stays", "error-stays").inOrder()
    }
}
