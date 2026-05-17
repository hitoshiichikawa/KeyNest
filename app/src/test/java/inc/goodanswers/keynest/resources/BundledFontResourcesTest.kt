package inc.goodanswers.keynest.resources

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import inc.goodanswers.keynest.R
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #13: Mechanical verification that the Manrope / JetBrains Mono
 * font files and their SIL Open Font License 1.1 texts are present in
 * the merged APK resources.
 *
 * - Backs Req 1.1 (Manrope bundled) and Req 2.1 (JetBrains Mono bundled)
 *   by resolving each individual `R.font.*` raw TTF asset.
 * - Backs Req 1.3 / Req 2.4 by resolving the `R.raw.ofl_*` license
 *   resources that ship the OFL.txt body next to the fonts.
 *
 * Test fails fast if any resource id cannot be resolved or if the
 * referenced file is empty / unreadable.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class BundledFontResourcesTest {

    private val context get() =
        ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun manrope_regular_isBundledAsNonEmptyAsset() {
        assertFontResourceIsBundled(R.font.manrope_regular, "manrope_regular")
    }

    @Test
    fun manrope_medium_isBundledAsNonEmptyAsset() {
        assertFontResourceIsBundled(R.font.manrope_medium, "manrope_medium")
    }

    @Test
    fun manrope_semibold_isBundledAsNonEmptyAsset() {
        assertFontResourceIsBundled(R.font.manrope_semibold, "manrope_semibold")
    }

    @Test
    fun manrope_bold_isBundledAsNonEmptyAsset() {
        assertFontResourceIsBundled(R.font.manrope_bold, "manrope_bold")
    }

    @Test
    fun jetbrainsMono_regular_isBundledAsNonEmptyAsset() {
        assertFontResourceIsBundled(R.font.jetbrains_mono_regular, "jetbrains_mono_regular")
    }

    @Test
    fun manrope_fontFamily_isBundledAsResource() {
        // The font-family XML aggregates the 4 weights. If it is absent the
        // theme-level `@font/manrope` reference would fail to inflate.
        assertResourceIsBundled(R.font.manrope, "font", "manrope")
    }

    @Test
    fun jetbrainsMono_fontFamily_isBundledAsResource() {
        // The font-family XML aggregates the single Regular weight. If it
        // is absent the credential_edit `@font/jetbrains_mono` reference
        // would fail to inflate.
        assertResourceIsBundled(R.font.jetbrains_mono, "font", "jetbrains_mono")
    }

    @Test
    fun manrope_oflLicenseText_isBundledAsNonEmptyRawAsset() {
        assertRawAssetIsNonEmpty(R.raw.ofl_manrope, "ofl_manrope")
    }

    @Test
    fun jetbrainsMono_oflLicenseText_isBundledAsNonEmptyRawAsset() {
        assertRawAssetIsNonEmpty(R.raw.ofl_jetbrains_mono, "ofl_jetbrains_mono")
    }

    @Test
    fun manrope_oflLicense_referencesSilOpenFontLicense() {
        // NFR 2.1 / 2.2: the bundled license body must be SIL Open Font
        // License 1.1, not a placeholder. We do not pin the exact byte
        // content (font authors may revise the OFL header), only that the
        // SIL OFL marker is present.
        val body = context.resources.openRawResource(R.raw.ofl_manrope).bufferedReader()
            .use { it.readText() }
        assertThat(body).contains("SIL Open Font License")
    }

    @Test
    fun jetbrainsMono_oflLicense_referencesSilOpenFontLicense() {
        val body = context.resources.openRawResource(R.raw.ofl_jetbrains_mono).bufferedReader()
            .use { it.readText() }
        assertThat(body).contains("SIL Open Font License")
    }

    private fun assertFontResourceIsBundled(resId: Int, name: String) {
        // R.font.* either points at a raw .ttf or at an aggregating
        // font-family XML. Both inflate via openRawResource and must
        // produce non-empty bytes.
        assertThat(resId).isNotEqualTo(0)
        val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
        // Issue #24: migrated from deprecated `.named("…")` (removed in
        // Truth 1.4) to `assertWithMessage("…")`. Same diagnostic text,
        // same isGreaterThan(0) assertion -- no relaxation of intent.
        assertWithMessage("size of R.font.$name").that(bytes.size).isGreaterThan(0)
    }

    private fun assertRawAssetIsNonEmpty(resId: Int, name: String) {
        assertThat(resId).isNotEqualTo(0)
        val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
        // Issue #24: same migration as above.
        assertWithMessage("size of R.raw.$name").that(bytes.size).isGreaterThan(0)
    }

    private fun assertResourceIsBundled(resId: Int, type: String, name: String) {
        // Issue #24: same migration as above.
        assertWithMessage("R.$type.$name resource id").that(resId).isNotEqualTo(0)
        val resolvedName = context.resources.getResourceEntryName(resId)
        assertThat(resolvedName).isEqualTo(name)
    }
}
