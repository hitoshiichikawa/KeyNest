package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #99 / T-10: instrumentation-test placeholder for
 * [PasskeyCreateActivity].
 *
 * This file exists so that, once CI gains an API 34 emulator (carved
 * out into Issue #94), the `@Ignore` annotations can be removed and
 * each method filled in with a real Activity-driven assertion. Until
 * then the unit tests under
 * `app/src/test/.../credentialprovider/registration/` are the primary
 * verification path (design §10.3 / requirements 確認事項).
 *
 * The methods are intentionally empty (other than the `@Ignore` skip) —
 * they compile but do not assert anything, so they will not flake even
 * if the surrounding tooling decides to run them.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PasskeyCreateActivityInstrumentationTest {

    @Test
    @Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除。")
    fun activityLaunch_displaysConfirmationUi() {
        // TODO(#94): launch via ActivityScenario.launch(PasskeyCreateActivity::class.java),
        // mock the BiometricPrompt to succeed, and assert that
        // PendingIntentHandler.retrieveProviderCreateCredentialRequest finds
        // a CreatePublicKeyCredentialResponse with a non-empty
        // registrationResponseJson on the result intent.
        ActivityScenario.launch(PasskeyCreateActivity::class.java).use { /* placeholder */ }
    }

    @Test
    @Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除。")
    fun activityLaunch_biometricSuccess_returnsRegistrationResponse() {
        // TODO(#94): drive BiometricPrompt → success, then assert the result
        // intent contains a CreatePublicKeyCredentialResponse via
        // PendingIntentHandler.
    }

    @Test
    @Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除。")
    fun activityLaunch_biometricCancel_returnsCancellationException() {
        // TODO(#94): drive BiometricPrompt → cancel and assert
        // PendingIntentHandler.setCreateCredentialException was called with
        // CreateCredentialCancellationException.
    }
}
