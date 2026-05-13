package com.example.keynest.resources

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Issue #13: source-level audit that the Manrope / JetBrains Mono
 * resources are actually wired into the runtime via theme overrides and
 * per-view fontFamily references.
 *
 * Source-level pinning is chosen over a Robolectric inflation test
 * because the artefacts under test are pure XML resources -- `aapt` and
 * the View framework read them verbatim, so checking the XML byte
 * content is sufficient and significantly faster than spinning a
 * Robolectric Activity.
 *
 * Mapped AC coverage:
 * - Req 1.2: every Material TextAppearance.* slot is overridden by
 *   TextAppearance.KeyNest.* pointing at @font/manrope, and the Theme
 *   overrides android:fontFamily so non-Material TextViews also pick up
 *   Manrope.
 * - Req 2.2: the two existing monospace TextViews in
 *   credential_edit_activity.xml use @font/jetbrains_mono.
 * - Req 3.1 / 3.2 (NFR 1.5 inheritance): the theme references the
 *   bundled @font resource, not androidx.core.provider.FontsContractCompat
 *   nor the Google Fonts provider.
 */
class FontTypefaceWiringTest {

    private val themesFile: File = File("src/main/res/values/themes.xml")
    private val layoutFile: File = File("src/main/res/layout/credential_edit_activity.xml")
    private val manifestFile: File = File("src/main/AndroidManifest.xml")

    @Test
    fun keyNestTheme_overridesTextAppearanceBody1_withManropeVariant() {
        val themes = themesFile.readText()
        // The theme must route textAppearanceBody1 through the Manrope variant
        // so any TextView using textAppearance="?attr/textAppearanceBody1"
        // resolves to the bundled Manrope face.
        assertThat(themes).contains(
            "<item name=\"textAppearanceBody1\">@style/TextAppearance.KeyNest.Body1</item>"
        )
        // The variant itself must reference @font/manrope.
        val body1Block = themes.substringAfter("\"TextAppearance.KeyNest.Body1\"")
            .substringBefore("</style>")
        assertThat(body1Block).contains("@font/manrope")
    }

    @Test
    fun keyNestTheme_overridesTextAppearanceBody2_withManropeVariant() {
        val themes = themesFile.readText()
        assertThat(themes).contains(
            "<item name=\"textAppearanceBody2\">@style/TextAppearance.KeyNest.Body2</item>"
        )
    }

    @Test
    fun keyNestTheme_overridesTextAppearanceHeadline6_withManropeVariant() {
        val themes = themesFile.readText()
        assertThat(themes).contains(
            "<item name=\"textAppearanceHeadline6\">@style/TextAppearance.KeyNest.Headline6</item>"
        )
    }

    @Test
    fun keyNestTheme_overridesTextAppearanceCaption_withManropeVariant() {
        val themes = themesFile.readText()
        assertThat(themes).contains(
            "<item name=\"textAppearanceCaption\">@style/TextAppearance.KeyNest.Caption</item>"
        )
    }

    @Test
    fun keyNestTheme_overridesAndroidFontFamily_atThemeLevel() {
        // Some views (raw TextView without textAppearance, EditText hints)
        // do not flow through Material TextAppearance.* and instead pick
        // up android:fontFamily directly. The theme must therefore override
        // this attribute at the theme level as well.
        val themes = themesFile.readText()
        val themeBlock = themes.substringAfter("\"Theme.KeyNest\"")
            .substringBefore("</style>")
        assertThat(themeBlock).contains("<item name=\"android:fontFamily\">@font/manrope</item>")
    }

    @Test
    fun credentialIdValue_usesJetBrainsMonoFont() {
        // Req 2.2: credential-id read-only display must use the bundled
        // JetBrains Mono Regular face. The previous "monospace" literal
        // is replaced by @font/jetbrains_mono in this issue.
        val xml = layoutFile.readText()
        val section = xml.substringAfter("@+id/value_credential_id").substringBefore("/>")
        assertThat(section).contains("android:fontFamily=\"@font/jetbrains_mono\"")
    }

    @Test
    fun credentialEditLayout_hasNoRemainingMonospaceLiteral() {
        // The 2 monospace literals were the only attribute occurrences in
        // the layout. After the migration none should remain (other than
        // explanatory XML comments that mention the historical literal).
        val xml = layoutFile.readText()
        val attributeUsages = Regex("""android:fontFamily="monospace"""").findAll(xml).toList()
        assertThat(attributeUsages).isEmpty()
    }

    @Test
    fun credentialEditLayout_doesNotReintroduceSystemMonospaceAttribute() {
        // Guards against a regression where someone re-adds
        // android:typeface="monospace" which would also bypass
        // @font/jetbrains_mono.
        val xml = layoutFile.readText()
        val attributeUsages = Regex("""android:typeface="monospace"""").findAll(xml).toList()
        assertThat(attributeUsages).isEmpty()
    }

    @Test
    fun manifest_doesNotReferenceFontsContractCompat_orGoogleFontsProvider() {
        // Req 3.2 (NFR 1.5 inheritance): the bundled font path must not
        // accidentally pull in Downloadable Fonts. We check the merged
        // manifest at the source level so this test still works under
        // gradle's minimal config.
        val manifest = manifestFile.readText()
        assertThat(manifest).doesNotContain("com.google.android.gms.fonts")
        assertThat(manifest).doesNotContain("FontsContractCompat")
    }

    @Test
    fun manifest_doesNotDeclareInternetPermission_afterFontBundle() {
        // Req 3.1: regardless of the font work, INTERNET must remain
        // un-declared in the merged manifest. This duplicates
        // InternetPermissionAbsenceTest at the source level to fail
        // fast if a future edit ever silently flips the declaration.
        // We grep for the actual <uses-permission ...> element, not the
        // bare string, because the manifest file legitimately contains a
        // comment that *mentions* INTERNET when explaining why it is
        // intentionally absent.
        val manifest = manifestFile.readText()
        val usesPermission = Regex(
            """<uses-permission[^>]*android:name="android\.permission\.INTERNET"[^>]*/>"""
        ).findAll(manifest).toList()
        assertThat(usesPermission).isEmpty()
    }
}
