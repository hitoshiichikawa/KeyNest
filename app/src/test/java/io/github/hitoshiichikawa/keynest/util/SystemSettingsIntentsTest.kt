package io.github.hitoshiichikawa.keynest.util

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

    // ---- Issue #103: openPasskeyProviderSettings -----------------------

    @Test
    fun openPasskeyProviderSettings_dispatchesCredentialProviderIntent_onSuccess() {
        // Req 6.5: with the resolver in default (permissive) mode the
        // primary ACTION_CREDENTIAL_PROVIDER intent dispatches and we
        // observe it as the next started activity.
        val activity = newActivity()

        val result = SystemSettingsIntents.openPasskeyProviderSettings(activity)

        assertThat(result.isSuccess).isTrue()
        val dispatched = shadowOf(activity).nextStartedActivity
            ?: error("expected an Intent to be dispatched")
        // Use the string literal — the constant Settings.ACTION_CREDENTIAL_PROVIDER
        // is API 34+ and the production code references the literal too.
        assertThat(dispatched.action).isEqualTo("android.settings.CREDENTIAL_PROVIDER")
    }

    @Test
    fun openPasskeyProviderSettings_fallsBackToActionSettings_whenPrimaryFails() {
        // Req 6.6: when the primary intent cannot be resolved, the helper
        // must fall back to ACTION_SETTINGS in the same call.
        //
        // We mark only the primary action as unresolvable by combining
        // checkActivities(true) with a PackageManager match for
        // ACTION_SETTINGS — but Robolectric's checkActivities flips on
        // BOTH, so we instead exercise the path by registering a resolver
        // for ACTION_SETTINGS and leaving ACTION_CREDENTIAL_PROVIDER
        // unhandled.
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Register ACTION_SETTINGS so the fallback resolves while the
        // primary action does not. Without `addResolveInfoForIntent`
        // Robolectric would still resolve in permissive mode, but with
        // checkActivities(true) it forces strict resolution.
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
        val resolveInfo = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.Settings"
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(fallbackIntent, resolveInfo)
        shadowOf(app).checkActivities(true)
        val activity = newActivity()

        val result = SystemSettingsIntents.openPasskeyProviderSettings(activity)

        assertThat(result.isSuccess).isTrue()
        // The next started activity should be the fallback ACTION_SETTINGS,
        // not the primary action.
        val dispatched = shadowOf(activity).nextStartedActivity
            ?: error("expected an Intent to be dispatched")
        assertThat(dispatched.action).isEqualTo(Settings.ACTION_SETTINGS)
    }

    @Test
    fun openPasskeyProviderSettings_returnsFailure_whenBothFail() {
        // Req 2.5 / 6.6 mirror: when neither the primary nor the fallback
        // resolves, the helper returns Result.failure so the caller can
        // surface the shared "settings unavailable" Snackbar.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).checkActivities(true)
        val activity = newActivity()

        val result = SystemSettingsIntents.openPasskeyProviderSettings(activity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ActivityNotFoundException::class.java)
    }
}
