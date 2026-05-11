package com.example.keynest.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager

/**
 * Determines whether the calling app is the active Autofill service.
 *
 * Requirements: 6.1, 6.3
 *
 * Why not just [AutofillManager.hasEnabledAutofillServices]?
 *
 * `hasEnabledAutofillServices()` is implemented as a binder call into
 * `AutofillManagerService`, which is user-scoped and initialised lazily.
 * Immediately after a cold process start the binder query can return
 * `false` even when the user has KeyNest selected as the system autofill
 * service, because the service-side state has not been loaded yet. This
 * caused the "enable autofill" guidance screen to appear intermittently
 * on launch.
 *
 * Reading the secure setting `autofill_service` is backed by the
 * persistent settings file and does not depend on the autofill manager
 * service binder being ready, so it gives a stable answer even on cold
 * start. We OR the two sources so that either signal — whichever is
 * available first — counts as "enabled".
 */
internal object AutofillServiceStatus {

    /**
     * Returns true if this app is the current system Autofill provider.
     * On pre-O devices Autofill does not exist; we return true to suppress
     * the guidance screen which would otherwise be meaningless.
     */
    fun isCurrentService(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true

        val manager = context.getSystemService(AutofillManager::class.java)
        if (manager?.hasEnabledAutofillServices() == true) return true

        // Fallback: read the secure setting directly so the answer is
        // available even before AutofillManagerService finishes binding.
        val flattened = try {
            Settings.Secure.getString(context.contentResolver, SETTING_AUTOFILL_SERVICE)
        } catch (_: Throwable) {
            null
        }
        return flattened != null && flattened.startsWith("${context.packageName}/")
    }

    /**
     * The secure-settings key holding the currently selected autofill
     * service as a flattened ComponentName (e.g. "pkg/.ServiceClass").
     * Mirrors `Settings.Secure.AUTOFILL_SERVICE`, which is `@SystemApi`
     * and therefore not visible from the public SDK.
     */
    private const val SETTING_AUTOFILL_SERVICE = "autofill_service"
}
