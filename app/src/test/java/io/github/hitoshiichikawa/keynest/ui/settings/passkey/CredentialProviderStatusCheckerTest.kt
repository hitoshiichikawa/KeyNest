package io.github.hitoshiichikawa.keynest.ui.settings.passkey

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.PasskeyProviderStatus
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Unit tests for [DefaultCredentialProviderStatusChecker].
 *
 * Issue #103 Req 3.3 / 3.4 / 3.5 / 3.8 / NFR 2.1.
 *
 * - Req 3.3 is covered by [check_returnsUnsupported_onApi33] (SDK 33,
 *   no Settings.Secure interaction).
 * - Req 3.4 is covered by [check_returnsEnabled_whenPackageInCredentialService]
 *   plus two Disabled variants (other package only / null value).
 * - Req 3.5 is covered by [check_returnsDisabled_whenSettingsSecureThrows]
 *   — the OS surface raises SecurityException and the checker must NOT
 *   propagate it (never falsely report Enabled).
 * - Req 3.8 / NFR 2.1 is covered by [check_doesNotLogProviderListAtInfoLevel]
 *   — the failure path is logged at most at Log.d, and the raw
 *   credential_service value never appears in info/warn/error sinks.
 */
@RunWith(AndroidJUnit4::class)
class CredentialProviderStatusCheckerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowLog.reset()
        ShadowLog.stream = null
    }

    @After
    fun tearDown() {
        ShadowLog.reset()
    }

    // ---- Req 3.3: SDK 33 path -----------------------------------------

    @Test
    @Config(sdk = [33])
    fun check_returnsUnsupported_onApi33() {
        // Arrange: on API 33, Settings.Secure["credential_service"] would
        // normally still be readable but the checker must not even look
        // it up.
        val checker = DefaultCredentialProviderStatusChecker(context)

        // Act
        val result = checker.check()

        // Assert
        assertThat(result).isEqualTo(PasskeyProviderStatus.Unsupported)
    }

    @Test
    @Config(sdk = [33])
    fun check_returnsUnsupported_onApi33_evenWhenCredentialServiceContainsSelf() {
        // Arrange: even if the secure setting somehow lists self on API 33
        // (which should never happen in practice), the SDK gate forces
        // Unsupported.
        Settings.Secure.putString(
            context.contentResolver,
            "credential_service",
            "${context.packageName}/x.Y",
        )
        val checker = DefaultCredentialProviderStatusChecker(context)

        val result = checker.check()

        assertThat(result).isEqualTo(PasskeyProviderStatus.Unsupported)
    }

    // ---- Req 3.4: SDK 34 happy paths ----------------------------------

    @Test
    @Config(sdk = [34])
    fun check_returnsEnabled_whenPackageInCredentialService() {
        // Arrange: ":" separated entries, KeyNest first (Pixel emulator
        // typical layout when KeyNest is the active provider).
        val keynest = context.packageName
        Settings.Secure.putString(
            context.contentResolver,
            "credential_service",
            "$keynest/x.KeyNestCredentialProviderService:com.example/x.Other",
        )
        val checker = DefaultCredentialProviderStatusChecker(context)

        val result = checker.check()

        assertThat(result).isEqualTo(PasskeyProviderStatus.Enabled)
    }

    @Test
    @Config(sdk = [34])
    fun check_returnsEnabled_whenPackageIsLastEntry() {
        // Arrange: KeyNest appears as a later entry — position must not
        // matter (entry order is OS-controlled).
        val keynest = context.packageName
        Settings.Secure.putString(
            context.contentResolver,
            "credential_service",
            "com.google.android.gms/x.Other:$keynest/x.S",
        )
        val checker = DefaultCredentialProviderStatusChecker(context)

        val result = checker.check()

        assertThat(result).isEqualTo(PasskeyProviderStatus.Enabled)
    }

    @Test
    @Config(sdk = [34])
    fun check_returnsDisabled_whenPackageNotInCredentialService() {
        // Arrange: only other providers selected.
        Settings.Secure.putString(
            context.contentResolver,
            "credential_service",
            "com.google.android.gms/x.PasswordsService:com.example.other/x.S",
        )
        val checker = DefaultCredentialProviderStatusChecker(context)

        val result = checker.check()

        assertThat(result).isEqualTo(PasskeyProviderStatus.Disabled)
    }

    @Test
    @Config(sdk = [34])
    fun check_returnsDisabled_whenCredentialServiceIsNull() {
        // Arrange: key is absent. (Robolectric's in-memory Secure store
        // is empty by default unless we putString.)
        // No setup — ensure key is unset.
        val checker = DefaultCredentialProviderStatusChecker(context)

        val result = checker.check()

        assertThat(result).isEqualTo(PasskeyProviderStatus.Disabled)
    }

    @Test
    @Config(sdk = [34])
    fun check_returnsDisabled_whenCredentialServiceIsBlank() {
        // Arrange: empty string treated the same as null per
        // `isNullOrBlank()`.
        Settings.Secure.putString(
            context.contentResolver,
            "credential_service",
            "   ",
        )
        val checker = DefaultCredentialProviderStatusChecker(context)

        val result = checker.check()

        assertThat(result).isEqualTo(PasskeyProviderStatus.Disabled)
    }

    // ---- Req 3.5: exception fallback ---------------------------------

    @Test
    @Config(sdk = [34])
    fun check_returnsDisabled_whenSettingsSecureThrows() {
        // Arrange: simulate a hostile OS surface that throws on any
        // Settings.Secure.getString call (some MDM-locked builds /
        // future OS versions where the key is access-restricted).
        // We use the injectable reader rather than mockkStatic because
        // JDK 17 refuses to retransform Settings.Secure (class
        // redefinition failed: attempted to change the class modifiers).
        val checker = DefaultCredentialProviderStatusChecker(
            context = context,
            credentialServiceReader = { throw SecurityException("denied by policy") },
        )

        val result = checker.check()

        // Req 3.5: must NEVER falsely report Enabled on exception.
        assertThat(result).isEqualTo(PasskeyProviderStatus.Disabled)
    }

    @Test
    @Config(sdk = [34])
    fun check_returnsDisabled_whenSettingsSecureThrowsRuntimeException() {
        // Arrange: broader Throwable coverage — generic RuntimeException
        // also routes to Disabled (catch is on Throwable, not just
        // SecurityException).
        val checker = DefaultCredentialProviderStatusChecker(
            context = context,
            credentialServiceReader = { throw RuntimeException("boom") },
        )

        val result = checker.check()

        assertThat(result).isEqualTo(PasskeyProviderStatus.Disabled)
    }

    // ---- Req 3.8 / NFR 2.1: log suppression --------------------------

    @Test
    @Config(sdk = [34])
    fun check_doesNotLogProviderListAtInfoLevel() {
        // Arrange: configure a credential_service value containing a
        // third-party provider package, then run check() — both the
        // success path AND the exception path must not log this value
        // at Log.i / w / e.
        val sensitiveProviderPackage = "com.acme.thirdparty.provider"
        Settings.Secure.putString(
            context.contentResolver,
            "credential_service",
            "$sensitiveProviderPackage/x.S:com.example.another/x.S",
        )
        val checker = DefaultCredentialProviderStatusChecker(context)

        // Act
        checker.check()

        // Assert: no Log.i / w / e contains the third-party package name.
        // (Log.d is allowed — release builds strip it; in tests Robolectric
        // captures it but we only forbid info-and-above.)
        val infoOrAbove = ShadowLog.getLogs().filter { it.type >= android.util.Log.INFO }
        infoOrAbove.forEach { item ->
            assertThat(item.msg ?: "").doesNotContain(sensitiveProviderPackage)
            assertThat(item.msg ?: "").doesNotContain("credential_service")
        }
    }

    @Test
    @Config(sdk = [34])
    fun check_doesNotLogProviderPackageAtInfoLevel_onException() {
        // Arrange: same assertion for the failure path. We force the
        // injected reader to throw, but the SecurityException message
        // intentionally embeds a third-party provider package name so
        // we can prove that path does not leak it via Log.i+.
        val sensitiveProviderPackage = "com.acme.thirdparty.provider"
        val checker = DefaultCredentialProviderStatusChecker(
            context = context,
            credentialServiceReader = {
                throw SecurityException(
                    "denied while reading $sensitiveProviderPackage/x.S",
                )
            },
        )

        checker.check()

        val infoOrAbove = ShadowLog.getLogs().filter { it.type >= android.util.Log.INFO }
        infoOrAbove.forEach { item ->
            assertThat(item.msg ?: "").doesNotContain(sensitiveProviderPackage)
            assertThat(item.throwable?.message ?: "").doesNotContain(sensitiveProviderPackage)
        }
    }
}
