package io.github.hitoshiichikawa.keynest.credentialprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #90 / T-05: instrumentation-test placeholder for
 * [KeyNestCredentialProviderService].
 *
 * This file exists so that, once CI gains an API 34 emulator (carved out into
 * Issue #94, see design §9.4), the `@Ignore` annotations can be removed and
 * each method filled in with a real service-binding assertion. Until then the
 * unit tests under `app/src/test/.../credentialprovider/` are the primary
 * verification path (design §7.2 / requirements 確認事項 3).
 *
 * The methods are intentionally empty (other than the `@Ignore` skip) — they
 * compile but do not assert anything, so they will not flake even if the
 * surrounding tooling decides to run them.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class KeyNestCredentialProviderServiceInstrumentationTest {

    @Test
    @Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除。")
    fun serviceBinding_returnsEmptyCreateResponse() {
        // TODO(#89 分割案 3 / Issue #94): bind KeyNestCredentialProviderService via
        // ServiceTestRule, dispatch a BeginCreateCredentialRequest, and assert the
        // OutcomeReceiver receives an empty BeginCreateCredentialResponse (req 6.1).
    }

    @Test
    @Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除。")
    fun serviceBinding_returnsEmptyGetResponse() {
        // TODO(#89 分割案 4 / Issue #94): bind the service, dispatch a
        // BeginGetCredentialRequest, and assert the OutcomeReceiver receives a
        // BeginGetCredentialResponse with zero credential / authentication /
        // action entries (req 6.2).
    }

    @Test
    @Ignore("CI に API 34 emulator が組み込まれるまで手動実行。#94 で gating 解除。")
    fun serviceBinding_clearCredentialState_succeeds() {
        // TODO(Issue #94): bind the service, dispatch a
        // ProviderClearCredentialStateRequest, and assert the OutcomeReceiver
        // receives onResult(null) without invoking onError (req 6.3).
    }
}
