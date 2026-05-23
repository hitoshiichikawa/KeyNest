package io.github.hitoshiichikawa.keynest.ui.settings.passkey

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.hitoshiichikawa.keynest.domain.model.PasskeyProviderStatus

/**
 * Resolves the current KeyNest PassKey provider registration state by
 * consulting Android OS surfaces (Build.VERSION + Settings.Secure).
 *
 * Issue #103 Req 3.1 / 3.2 / 3.3 / 3.4 / 3.5 / 3.8 / NFR 2.1.
 *
 * Boundary: this is the only component allowed to touch the OS Framework
 * (Build.VERSION / Settings.Secure / ContentResolver) for PassKey provider
 * discovery. SettingsViewModel must consume only the resulting
 * [PasskeyProviderStatus] enum.
 *
 * Contract:
 * - Pure (no state, no side effects beyond log lines at DEBUG level).
 * - Never throws — every exception path returns [PasskeyProviderStatus.Disabled]
 *   (Req 3.5, "never falsely report Enabled").
 * - Does NOT emit provider package names or the raw `credential_service`
 *   value at `Log.i` or above (Req 3.8 / NFR 2.1) so third-party providers
 *   cannot leak via KeyNest logs.
 */
interface CredentialProviderStatusChecker {
    /**
     * Returns the current state as [PasskeyProviderStatus].
     *
     * - Req 3.3: SDK_INT < 34 → returns [Unsupported] immediately without
     *   touching any Credential Manager API.
     * - Req 3.4: SDK_INT >= 34 → consults OS surfaces and normalises to
     *   [Enabled] / [Disabled].
     * - Req 3.5: any throwable from the OS surface produces [Disabled].
     * - Req 3.6: this call is synchronous and is expected to complete in
     *   the 1–10ms range (single Settings.Secure read + string split).
     *   Callers (SettingsViewModel) still wrap it with `withContext(IO)`
     *   defensively because Settings.Secure can involve IPC.
     */
    fun check(): PasskeyProviderStatus
}

/**
 * Default implementation backed by [Settings.Secure] key
 * `"credential_service"` (Issue #103 design.md §9.1-1, PoC pattern A —
 * see `docs/specs/103-feat-passkey-passkey-os/poc-notes.md`).
 *
 * Algorithm (API 34+):
 *  1. `Settings.Secure.getString(resolver, "credential_service")`
 *  2. Split on `:` (each entry is "pkg/component" from
 *     `ComponentName.flattenToString()`).
 *  3. For each entry, take `substringBefore("/")` (package name) and
 *     compare against [Context.getPackageName].
 *  4. Match → [PasskeyProviderStatus.Enabled]; otherwise [Disabled].
 *  5. Any throwable → [Disabled] (Req 3.5).
 *
 * The `"credential_service"` key is an AOSP internal key (not exposed as a
 * public `Settings.Secure` constant), so we reference it as a string
 * literal. design.md §9.1-1 documents the reasoning and the fallback path
 * if OEMs strip / rename the key.
 */
internal class DefaultCredentialProviderStatusChecker(
    private val context: Context,
) : CredentialProviderStatusChecker {

    override fun check(): PasskeyProviderStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Req 3.3: never call Credential Manager API on API 33-.
            return PasskeyProviderStatus.Unsupported
        }
        return checkOnApi34Plus()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun checkOnApi34Plus(): PasskeyProviderStatus {
        return try {
            val raw = Settings.Secure.getString(
                context.contentResolver,
                CREDENTIAL_SERVICE_KEY,
            )
            if (raw.isNullOrBlank()) {
                PasskeyProviderStatus.Disabled
            } else {
                val ourPackage = context.packageName
                val matched = raw.split(SEPARATOR).any { entry ->
                    entry.substringBefore(COMPONENT_SEPARATOR) == ourPackage
                }
                if (matched) PasskeyProviderStatus.Enabled
                else PasskeyProviderStatus.Disabled
            }
        } catch (t: Throwable) {
            // Req 3.5 fallback. catch Throwable because Settings.Secure can
            // raise SecurityException / RuntimeException / NPE depending on
            // device / version.
            //
            // Req 3.8 / NFR 2.1: never log at info level or above. The raw
            // `credential_service` value may contain third-party provider
            // package names; emitting them would leak which other PassKey
            // providers the user has selected. Log.d is allowed (stripped
            // in release builds) but we deliberately do not include the raw
            // value or `t.message` here for that reason.
            Log.d(TAG, "PassKey provider status probe failed; falling back to Disabled")
            PasskeyProviderStatus.Disabled
        }
    }

    private companion object {
        private const val TAG = "PassKeyProviderStatus"

        /**
         * AOSP internal `Settings.Secure` key holding the Credential
         * Manager provider selection. Not a public constant — see
         * design.md §9.1-1 and `poc-notes.md`.
         */
        private const val CREDENTIAL_SERVICE_KEY = "credential_service"

        /** ComponentName separator inside `credential_service`. */
        private const val SEPARATOR = ":"

        /** ComponentName package / class separator inside each entry. */
        private const val COMPONENT_SEPARATOR = "/"
    }
}
