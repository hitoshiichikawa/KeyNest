package com.example.keynest.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.keynest.di.ServiceLocator
import com.example.keynest.domain.usecase.NewCredentialInput
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T10.1 end-to-end Autofill flow. Backs Req 3.3, 3.4, 5.2, 5.3.
 *
 * The full happy-path (credential register -> Autofill candidate -> Biometric
 * prompt -> fill into target form) requires a real device with a registered
 * biometric or device credential AND a separate test target app. That kind
 * of setup is outside the MVP local test loop, so the test is marked
 * @Ignore by default. It still serves as documentation of the exact steps
 * Reviewer / QA should walk through during release verification.
 *
 * The currently-runnable sub-paths (signature filtering, encryption round
 * trip, list rendering) are exercised by the per-component instrumented
 * tests:
 *   - AesGcmCipherTest               -> 5.3 decrypt round trip
 *   - SignatureMismatchTest          -> 3.3 candidate selection
 *   - SafeLoggerAuditTest            -> NFR 1.3
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Manual E2E - requires biometric enrollment and a separate target app. See class KDoc.")
class AutofillFlowTest {

    @Before
    fun setUp() {
        ServiceLocator.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun fullFlow_save_then_unlock_then_fillForm() = runBlocking {
        // Step 1: save a credential via SaveCredentialUseCase
        val save = ServiceLocator.saveCredentialUseCase
        val result = save(
            NewCredentialInput(
                packageName = "com.example.target",
                username = "alice",
                password = "p@ssw0rd".toCharArray(),
                label = "Example",
            ),
        )
        assertThat(result.isSuccess).isTrue()

        // Step 2 (manual): with KeyNest set as the Autofill service, open
        // the target app, tap the username field, accept the BiometricPrompt
        // and verify that the username / password are filled.

        // Step 3 (manual): re-tap the same field after process death and
        // verify the same flow still works.
    }
}
