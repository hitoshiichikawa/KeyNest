package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #100 / T-07: instrumentation-test placeholder for
 * [PasskeyAuthActivity].
 *
 * This file exists so that, once CI gains an API 34 emulator (carved out
 * into Issue #94), the `@Ignore` annotations can be removed and each
 * method filled in with a real Activity-driven assertion. Until then the
 * unit tests under
 * `app/src/test/.../credentialprovider/authentication/` are the primary
 * verification path (design §10.2 / requirements 確認事項).
 *
 * The methods are intentionally empty (other than the `@Ignore` skip) —
 * they compile but do not assert anything, so they will not flake even
 * if the surrounding tooling decides to run them.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PasskeyAuthActivityInstrumentationTest {

    @Test
    @Ignore("API 34 emulator が CI に揃うまで手動実行 — #94 完了後に @Ignore 解除")
    fun activityLaunch_biometricSuccess_returnsAuthenticationResponse() {
        // TODO(#94): launch via ActivityScenario.launch(PasskeyAuthActivity::class.java)
        // with a real GetPublicKeyCredentialOption + PasskeyEntity seeded
        // into Room, drive BiometricPrompt → success, then assert that
        // PendingIntentHandler.retrieveCredentialManagerCallbackResponse
        // hands back a PublicKeyCredential with a non-empty
        // authenticationResponseJson on the result intent.
        ActivityScenario.launch(PasskeyAuthActivity::class.java).use { /* placeholder */ }
    }

    @Test
    @Ignore("API 34 emulator が CI に揃うまで手動実行 — #94 完了後に @Ignore 解除")
    fun activityLaunch_biometricCancel_returnsCancellationException() {
        // TODO(#94): drive BiometricPrompt → cancel and assert
        // PendingIntentHandler.setGetCredentialException was called with
        // GetCredentialCancellationException.
    }

    @Test
    @Ignore("API 34 emulator が CI に揃うまで手動実行 — #94 完了後に @Ignore 解除")
    fun activityLaunch_signCountIncrementsOnSuccess_andRollsBackOnFailure() {
        // TODO(#94): assert Room signCount column transitions match the
        // Option A contract end-to-end (decrypt failure on tampered blob
        // → rollback / fresh sign success → +1).
    }
}
