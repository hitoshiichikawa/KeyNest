package io.github.hitoshiichikawa.keynest.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helpers that launch the Android system Settings activities used by
 * the KeyNest Settings screen.
 *
 * Issue #10 Req 2.4, 2.6, 3.4, 3.6.
 *
 * Each helper returns a `Result<Unit>`:
 * - `Result.success(Unit)` when the Intent dispatched successfully
 * - `Result.failure(<ActivityNotFoundException or pre-O guard>)` when
 *   the device cannot resolve the Intent. Callers surface this as a
 *   Snackbar without finishing the Settings screen (graceful degrade,
 *   mirrors the AutofillEnableActivity.launchSettings pattern).
 *
 * All calls remain on-device; no Intent target is queried over the
 * network (NFR 1.1).
 */
object SystemSettingsIntents {

    /**
     * Launches `Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE` with the
     * caller's package URI so the system pre-selects KeyNest in the
     * Autofill chooser. Req 2.4.
     *
     * Returns `Result.failure` when the device does not ship the Autofill
     * settings activity (Req 2.6) or when running on a pre-O device
     * (minSdk is 26 today but the guard is defensive).
     */
    fun openAutofillServiceChooser(activity: Activity): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(IllegalStateException("autofill settings require API 26+"))
        }
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        return startSafely(activity, intent)
    }

    /**
     * Launches `Settings.ACTION_SECURITY_SETTINGS`. Req 3.4.
     *
     * Returns `Result.failure` with the underlying
     * `ActivityNotFoundException` when the device strips the Security
     * settings entry (rare, but seen on some MDM-locked enterprise
     * builds -- Req 3.6).
     */
    fun openSecuritySettings(activity: Activity): Result<Unit> {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        return startSafely(activity, intent)
    }

    /**
     * Issue #103 Req 2.3 / 2.4 / 2.5.
     *
     * Launches the OS Credential Manager settings screen with a 2-step
     * fallback so the caller only sees a single `Result<Unit>`:
     *
     *  1. Primary: `Intent("android.settings.CREDENTIAL_PROVIDER")` —
     *     the deep-link added in Android 14 / API 34. The constant
     *     `Settings.ACTION_CREDENTIAL_PROVIDER` is API 34+ so we use the
     *     literal string to avoid a `NewApi` lint warning on minSdk 26
     *     (design.md §4.4).
     *  2. Fallback: `Settings.ACTION_SETTINGS` — the legacy "Android
     *     Settings" entry. Always present on AOSP / Pixel; missing only
     *     on some MDM-locked builds.
     *
     * Returns `Result.failure(ActivityNotFoundException)` only when BOTH
     * intents fail to resolve. Callers (SettingsActivity) surface this
     * via the shared `showIntentUnavailableSnackbar()` UX (Req 2.5).
     *
     * Note: API 33 and below devices reach the fallback branch because
     * the primary action is unknown to the resolver, which yields
     * "Android Settings". SettingsActivity hides the launching button on
     * API 33- so this method is normally not called there (Req 2.2);
     * the fallback is still safe if it is.
     */
    fun openPasskeyProviderSettings(activity: Activity): Result<Unit> {
        // Primary: ACTION_CREDENTIAL_PROVIDER (Android 14 / API 34+).
        val primary = Intent(ACTION_CREDENTIAL_PROVIDER)
        val primaryResult = startSafely(activity, primary)
        if (primaryResult.isSuccess) return primaryResult

        // Fallback: legacy "Android Settings" root entry.
        val fallback = Intent(Settings.ACTION_SETTINGS)
        return startSafely(activity, fallback)
    }

    /**
     * `Settings.ACTION_CREDENTIAL_PROVIDER` (value
     * `"android.settings.CREDENTIAL_PROVIDER"`) was added in Android 14 /
     * API 34. Referenced as a string literal so the helper itself stays
     * callable on the minSdk = 26 surface without a `NewApi` lint
     * suppression. See design.md §4.4.
     */
    private const val ACTION_CREDENTIAL_PROVIDER = "android.settings.CREDENTIAL_PROVIDER"

    private fun startSafely(activity: Activity, intent: Intent): Result<Unit> {
        return try {
            activity.startActivity(intent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(e)
        }
    }
}
