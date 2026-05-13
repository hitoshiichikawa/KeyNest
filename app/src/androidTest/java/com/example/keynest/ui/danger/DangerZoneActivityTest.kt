package com.example.keynest.ui.danger

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.keynest.R
import com.example.keynest.di.ServiceLocator
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for the Danger Zone screen. Issue #10 Req 7.1,
 * 7.2, 7.3, 7.4, 7.5, 7.6, 7.9.
 *
 * IMPORTANT: marked @Ignore by default. Driving BiometricPrompt in
 * Espresso requires a test-friendly BiometricAuthenticator double,
 * which depends on the same ServiceLocator override mechanism as
 * SettingsActivityTest / CredentialListActivityTest. The test bodies
 * below document the intended assertions so the un-ignore PR is
 * mechanical once the override lands.
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Manual UI verification - requires test-friendly ServiceLocator override.")
class DangerZoneActivityTest {

    @Before
    fun setUp() {
        ServiceLocator.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun screen_showsTitleAndDestructiveButton() {
        // Req 7.1: the screen exposes exactly one destructive trigger.
        ActivityScenario.launch(DangerZoneActivity::class.java).use {
            onView(withText(R.string.danger_zone_title)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_clear)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun clearButton_tap_triggersBiometricPrompt() {
        // Req 7.2: the destructive button MUST go through the
        // BiometricPrompt before any DB write. Asserting the prompt
        // is visible requires a stubbed BiometricAuthenticator.
        ActivityScenario.launch(DangerZoneActivity::class.java).use {
            onView(withId(R.id.btn_clear)).perform(click())
            // TODO(test override): assert the stub BiometricAuthenticator
            // was invoked with title=danger_zone_biometric_title.
        }
    }

    @Test
    fun authCancelled_keepsCredentialsInVault() {
        // Req 7.3: cancellation must NOT clear the DB. Requires a
        // stubbed BiometricAuthenticator that returns AuthResult.Cancelled.
        // After the test we re-read the DAO to assert observeCount > 0.
    }

    @Test
    fun confirmedClear_removesAllCredentials_andFinishes() {
        // Req 7.5 / 7.6: after a successful auth + confirm dialog OK,
        // ClearVaultUseCase runs and the screen finishes. The DAO
        // count should be 0 and the AndroidKeyStore alias absent.
    }

    @Test
    fun clearedVault_yieldsEmptyAutofillCandidates() {
        // Req 7.9: after clear, ResolveAutofillCandidatesUseCase returns
        // an empty list because the DAO is empty. Exercised end-to-end
        // by AutofillFlowTest, but kept here as a documentation marker
        // so the Danger Zone integration coverage is explicit.
    }
}
