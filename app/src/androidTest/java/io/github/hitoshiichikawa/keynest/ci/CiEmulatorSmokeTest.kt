package io.github.hitoshiichikawa.keynest.ci

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #94 — CI smoke test for the Android 14 (API 34) emulator workflow.
 *
 * This test exists solely to prove that the GitHub Actions
 * `instrumentation-test.yml` workflow can:
 *   1. Boot an API 34 AVD via `reactivecircus/android-emulator-runner`.
 *   2. Install the debug + androidTest APKs.
 *   3. Run the AndroidJUnit4 runner against `app/src/androidTest/`.
 *   4. Surface a passing result back to the GitHub PR status check.
 *
 * Why this test is needed:
 *   - The pre-existing `KeyNestCredentialProviderServiceInstrumentationTest`
 *     placeholder (added by #90) is `@Ignore`d until each feature Issue
 *     (#89 split case 3 / 4) wires up the real bind verification. Without
 *     this smoke test the emulator job would have *zero* runnable
 *     instrumentation tests on its first CI run, making it impossible to
 *     prove the workflow actually executed anything.
 *   - The implementation here is also a *reference pattern* for how the
 *     `@Ignore` placeholder tests should look once they are activated
 *     (same `AndroidJUnit4` runner, same Truth assertion style — see
 *     requirements §4.3).
 *
 * Lifecycle:
 *   - Per requirements §4.4 this class is deletable as soon as the real
 *     `KeyNestCredentialProviderServiceInstrumentationTest` methods drop
 *     their `@Ignore`. It should **not** be removed in this Issue.
 *
 * Constraints respected (requirements §4.2):
 *   - No new dependencies (Truth + `androidx.test.ext.junit` are already
 *     on the `androidTestImplementation` classpath via
 *     `app/build.gradle.kts`).
 *   - No `@SdkSuppress` annotation: the workflow currently pins
 *     `api-level: 34`, but the assertions below touch nothing that is
 *     gated on API 34+, so the test stays valid if a lower-API matrix is
 *     ever introduced.
 */
@RunWith(AndroidJUnit4::class)
class CiEmulatorSmokeTest {

    /**
     * Sanity check that the JUnit runner is alive on the emulator. Uses
     * arithmetic on purpose so it cannot regress for any environmental
     * reason (no Context / no Keystore / no Manifest dependency).
     */
    @Test
    fun jvmArithmetic_isFunctional() {
        assertThat(1 + 1).isEqualTo(2)
    }

    /**
     * Confirms the `InstrumentationRegistry` is wired up correctly and the
     * target app under test is actually `io.github.hitoshiichikawa.keynest`.
     * This is the canonical "is this really our APK?" smoke test and is
     * the exact pattern future bind tests will need before they can call
     * `ServiceTestRule.bindService(...)` against
     * `KeyNestCredentialProviderService`.
     */
    @Test
    fun instrumentationRegistry_targetContext_isOurApp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertThat(targetContext.packageName).isEqualTo("io.github.hitoshiichikawa.keynest")
    }
}
