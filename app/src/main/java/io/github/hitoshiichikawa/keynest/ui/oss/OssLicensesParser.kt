package io.github.hitoshiichikawa.keynest.ui.oss

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses `app/src/main/assets/oss_licenses.json` into a list of
 * [OssEntry] rows.
 *
 * Issue #10 Req 5.3, 5.4. Kept as a separate object (rather than
 * inlined in [OssLicensesActivity]) so the parsing logic can be unit
 * tested under plain JUnit without an Android context.
 *
 * The parser is **tolerant**:
 * - Unknown JSON keys are ignored.
 * - Missing `url` is rendered as null (some entries ship only the
 *   bundled text).
 * - Missing `text` is replaced with an empty string so the row still
 *   renders.
 *
 * The parser is **strict** about the top-level shape: anything other
 * than a JSON array throws [JSONException], which the Activity catches
 * to drive the Req 5.4 graceful-degrade Snackbar.
 */
internal object OssLicensesParser {

    fun parse(json: String): List<OssEntry> {
        val array = JSONArray(json)
        return List(array.length()) { i -> entryFrom(array.getJSONObject(i)) }
    }

    private fun entryFrom(obj: JSONObject): OssEntry = OssEntry(
        name = obj.optString("name", ""),
        license = obj.optString("license", ""),
        url = if (obj.has("url") && !obj.isNull("url")) obj.optString("url") else null,
        text = obj.optString("text", ""),
    )
}
