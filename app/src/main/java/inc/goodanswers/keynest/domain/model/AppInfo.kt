package inc.goodanswers.keynest.domain.model

/**
 * Identifying information for the installed KeyNest build.
 *
 * Issue #10 Req 5.1. Comes from
 * [inc.goodanswers.keynest.util.AppInfoProvider] which reads
 * `PackageManager.getPackageInfo` and never goes off-device (NFR 1.1).
 *
 * @property versionName user-facing version (e.g. "0.1.0").
 * @property versionCode monotonically increasing integer build number.
 */
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
)
