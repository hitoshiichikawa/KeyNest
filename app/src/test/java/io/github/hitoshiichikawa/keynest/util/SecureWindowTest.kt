package io.github.hitoshiichikawa.keynest.util

import android.app.Activity
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Issue #135: [SecureWindow.apply] がウィンドウに FLAG_SECURE を立てる
 * 挙動の検証。対象 Activity 側の適用漏れは
 * [io.github.hitoshiichikawa.keynest.security.FlagSecureSourceAuditTest]
 * がソーススキャンで担保する（役割分担）。
 *
 * `@Config(sdk = [34])`: Robolectric 4.13 は SDK 35 (targetSdk) 未対応の
 * ため、既存テスト群（KeyNestCredentialProviderServiceTest 等）と同じく
 * 34 へ pin する。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SecureWindowTest {

    @Test
    fun apply_setsFlagSecureOnWindow() {
        // Arrange
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Act
        SecureWindow.apply(activity.window)

        // Assert
        val flags = activity.window.attributes.flags
        assertThat(flags and WindowManager.LayoutParams.FLAG_SECURE)
            .isEqualTo(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test
    fun apply_isIdempotentWhenCalledTwice() {
        // Arrange
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Act
        SecureWindow.apply(activity.window)
        SecureWindow.apply(activity.window)

        // Assert
        val flags = activity.window.attributes.flags
        assertThat(flags and WindowManager.LayoutParams.FLAG_SECURE)
            .isEqualTo(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test
    fun beforeApply_flagSecureIsAbsent() {
        // Arrange / Act: apply せずに観測（前提確認 — このテストが落ちる場合、
        // フラグ既定値が変わっており他 2 テストの検証意味が崩れている）
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Assert
        val flags = activity.window.attributes.flags
        assertThat(flags and WindowManager.LayoutParams.FLAG_SECURE).isEqualTo(0)
    }
}
