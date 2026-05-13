package com.example.keynest.util

import android.content.Context
import android.os.Build
import com.example.keynest.domain.model.AppInfo

/**
 * Resolves the installed KeyNest build's [AppInfo] via [android.content.
 * pm.PackageManager].
 *
 * Issue #10 Req 5.1.
 *
 * `open` so JVM unit tests can stub the [get] call without setting up a
 * full Robolectric application context. The default implementation
 * uses `PackageManager.getPackageInfo` and is exercised by
 * [com.example.keynest.util.AppInfoProviderTest].
 *
 * No data leaves the device (NFR 1.1).
 */
open class AppInfoProvider(private val context: Context) {

    /**
     * Returns the [AppInfo] for the calling package. On API 28+ the
     * version code is read from `longVersionCode`; on older devices the
     * deprecated `versionCode` is widened.
     *
     * If `versionName` is null (rare -- usually only in stub builds)
     * the returned value is an empty string so the UI never renders a
     * literal "null".
     */
    open fun get(): AppInfo {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return AppInfo(
            versionName = info.versionName ?: "",
            versionCode = versionCode,
        )
    }
}
