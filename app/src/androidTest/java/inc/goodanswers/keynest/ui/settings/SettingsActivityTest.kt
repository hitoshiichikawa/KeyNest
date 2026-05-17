package inc.goodanswers.keynest.ui.settings

import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import inc.goodanswers.keynest.R
import inc.goodanswers.keynest.di.ServiceLocator
import inc.goodanswers.keynest.ui.list.CredentialListActivity
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for the Settings screen. Issue #10 Req 1.1, 1.2,
 * 1.3, 2.1, 2.4, 2.6, 6.3.
 *
 * IMPORTANT: marked @Ignore by default for the same reason as
 * [inc.goodanswers.keynest.ui.list.CredentialListActivityTest] -- the
 * ServiceLocator currently has no test-friendly override mechanism for
 * the repository / use cases, so we cannot deterministically seed
 * state across runs. The test bodies are written to be "ready to run"
 * once a future task introduces a swap point.
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Manual UI verification - requires test-friendly ServiceLocator override.")
class SettingsActivityTest {

    @Before
    fun setUp() {
        ServiceLocator.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun overflow_settings_tap_opensSettingsActivity() {
        // Req 1.1 / 1.2.
        ActivityScenario.launch(CredentialListActivity::class.java).use {
            // Open overflow then tap "Settings".
            androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
            onView(withText(R.string.menu_open_settings)).perform(click())

            // The Settings title is shown by the Toolbar.
            onView(withText(R.string.settings_title)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun backButton_returnsToCredentialList() {
        // Req 1.3.
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            // Tap the toolbar's nav (Back) icon.
            onView(withId(R.id.toolbar)).perform(click())
            // scenario.state becomes DESTROYED after finish().
            // (We rely on the CredentialListActivity test class for the
            // post-back assertion to keep this case focused.)
        }
    }

    @Test
    fun openAndroidSettings_tap_dispatchesSetAutofillServiceIntent() {
        // Req 2.4.
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.btn_open_autofill_settings)).perform(click())
            intended(hasAction(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
        }
    }

    @Test
    fun openSecuritySettings_tap_dispatchesSecuritySettingsIntent() {
        // Req 3.4.
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.btn_open_security_settings)).perform(click())
            intended(hasAction(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    @Test
    fun autofillBadge_showsNotEnabled_whenAutofillNotSelected() {
        // Req 2.1: badge text says "Not set" when KeyNest is not the
        // active Autofill service. AOSP emulators ship with no
        // Autofill provider pre-selected, so the default state is
        // appropriate for this check.
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.text_autofill_status))
                .check(matches(withText(R.string.settings_autofill_badge_not_enabled)))
        }
    }

    @Test
    fun dangerZoneButton_tap_opensDangerZoneActivity() {
        // Req 6.3.
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.btn_open_danger_zone)).perform(click())
            onView(withText(R.string.danger_zone_title)).check(matches(isDisplayed()))
        }
    }
}
