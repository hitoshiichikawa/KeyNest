package inc.goodanswers.keynest.ui.oss

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of [OssLicensesParser]. Issue #10 Req 5.3, 5.4.
 *
 * Runs on Robolectric so that `org.json.*` provides the real
 * Android-compatible implementation (the JVM stubs in the unit-test
 * classpath return defaults under `isReturnDefaultValues = true`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class OssLicensesParserTest {

    @Test
    fun parse_returnsEmptyList_forEmptyJsonArray() {
        val parsed = OssLicensesParser.parse("[]")
        assertThat(parsed).isEmpty()
    }

    @Test
    fun parse_readsFullSchemaIntoEntry() {
        // Arrange
        val json = """
            [
              {
                "name": "androidx-room",
                "license": "Apache 2.0",
                "url": "https://developer.android.com/jetpack/androidx",
                "text": "Copyright The Android Open Source Project..."
              }
            ]
        """.trimIndent()

        // Act
        val parsed = OssLicensesParser.parse(json)

        // Assert
        assertThat(parsed).hasSize(1)
        val entry = parsed.single()
        assertThat(entry.name).isEqualTo("androidx-room")
        assertThat(entry.license).isEqualTo("Apache 2.0")
        assertThat(entry.url).isEqualTo("https://developer.android.com/jetpack/androidx")
        assertThat(entry.text).startsWith("Copyright")
        assertThat(entry.isExpanded).isFalse()
    }

    @Test
    fun parse_tolerates_missingUrlField() {
        // Some entries only ship bundled text (no canonical URL). The
        // parser must return null in that case so the UI can hide the
        // URL button (vs. rendering an empty link).
        val json = """[{ "name":"x", "license":"MIT", "text":"hello" }]"""

        val parsed = OssLicensesParser.parse(json)

        assertThat(parsed.single().url).isNull()
    }

    @Test
    fun parse_tolerates_nullUrlField() {
        val json = """[{ "name":"x", "license":"MIT", "url": null, "text":"hello" }]"""

        val parsed = OssLicensesParser.parse(json)

        assertThat(parsed.single().url).isNull()
    }

    @Test
    fun parse_ignores_unknownFields() {
        val json = """[{ "name":"x", "license":"MIT", "text":"y", "unknown":"value" }]"""

        val parsed = OssLicensesParser.parse(json)

        assertThat(parsed.single().name).isEqualTo("x")
    }

    @Test(expected = JSONException::class)
    fun parse_throws_whenTopLevelIsNotArray() {
        // Req 5.4: malformed JSON drives the graceful-degrade Snackbar
        // path in the Activity. Throwing on shape mismatch is the
        // signal.
        OssLicensesParser.parse("""{ "name": "x" }""")
    }

    @Test(expected = JSONException::class)
    fun parse_throws_whenJsonIsMalformed() {
        OssLicensesParser.parse("not even json")
    }
}
