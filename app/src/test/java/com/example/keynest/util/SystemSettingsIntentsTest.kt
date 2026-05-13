package com.example.keynest.util

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Behaviour of [SystemSettingsIntents].
 *
 * Issue #10 Req 2.4, 2.6, 3.4, 3.6.
 *
 * Uses Robolectric so we can drive `startActivity` from a real
 * [Activity] and inspect the dispatched [Intent] via the shadow.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SystemSettingsIntentsTest {

    private fun newActivity(): Activity =
        Robolectric.buildActivity(Activity::class.java).create().get()

    @Test
    fun openAutofillServiceChooser_dispatchesRequestSetAutofillServiceIntent() {
        // Arrange
        val activity = newActivity()

        // Act
        val result = SystemSettingsIntents.openAutofillServiceChooser(activity)

        // Assert
        assertThat(result.isSuccess).isTrue()
        val dispatched: Intent = shadowOf(activity).nextStartedActivity
            ?: error("expected an Intent to be dispatched")
        assertThat(dispatched.action).isEqualTo(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
        // Req 2.4: package:<thispkg> URI nudges Settings to pre-select KeyNest.
        assertThat(dispatched.data.toString()).isEqualTo("package:${activity.packageName}")
    }

    @Test
    fun openSecuritySettings_dispatchesSecuritySettingsIntent() {
        val activity = newActivity()

        val result = SystemSettingsIntents.openSecuritySettings(activity)

        assertThat(result.isSuccess).isTrue()
        val dispatched = shadowOf(activity).nextStartedActivity
            ?: error("expected an Intent to be dispatched")
        assertThat(dispatched.action).isEqualTo(Settings.ACTION_SECURITY_SETTINGS)
    }

    @After
    fun resetCheckActivities() {
        // Restore the default (off) so other tests do not see spurious throws.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).checkActivities(false)
    }

    @Test
    fun openAutofillServiceChooser_returnsFailure_whenActivityNotFound() {
        // Arrange: Robolectric's "checkActivities" mode makes
        // Activity.startActivity raise ActivityNotFoundException for any
        // Intent that does not resolve. Neither
        // ACTION_REQUEST_SET_AUTOFILL_SERVICE nor ACTION_SECURITY_SETTINGS
        // is registered in the KeyNest manifest, so flipping the flag
        // simulates the Req 2.6 graceful-degrade path.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).checkActivities(true)
        val activity = newActivity()

        // Act
        val result = SystemSettingsIntents.openAutofillServiceChooser(activity)

        // Assert
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ActivityNotFoundException::class.java)
    }

    @Test
    fun openSecuritySettings_returnsFailure_whenActivityNotFound() {
        // Req 3.6 mirror of the autofill case.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).checkActivities(true)
        val activity = newActivity()

        val result = SystemSettingsIntents.openSecuritySettings(activity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ActivityNotFoundException::class.java)
    }
}
