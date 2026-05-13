package com.example.keynest.util

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

    private fun startSafely(activity: Activity, intent: Intent): Result<Unit> {
        return try {
            activity.startActivity(intent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(e)
        }
    }
}
