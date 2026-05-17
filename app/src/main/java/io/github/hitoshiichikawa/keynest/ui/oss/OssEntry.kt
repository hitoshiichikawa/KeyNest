package io.github.hitoshiichikawa.keynest.ui.oss

/**
 * One row in the OSS license list.
 *
 * Issue #10 Req 5.2, 5.3. The fields map directly to the JSON schema
 * inside `app/src/main/assets/oss_licenses.json`:
 *
 * ```json
 * [{ "name": "...", "license": "...", "url": "...", "text": "..." }, ...]
 * ```
 *
 * @property name library / project name (e.g. "androidx.room:room-runtime").
 * @property license short license identifier (e.g. "Apache 2.0").
 * @property url canonical project URL. Nullable because some entries
 *   may only ship the bundled text.
 * @property text the full license text. Long; the UI accordion-collapses
 *   it by default.
 * @property isExpanded UI-only flag toggled by tap. Defaults to false so
 *   the initial list shows compact rows.
 */
data class OssEntry(
    val name: String,
    val license: String,
    val url: String?,
    val text: String,
    val isExpanded: Boolean = false,
)
