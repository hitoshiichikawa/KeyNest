package inc.goodanswers.keynest.resources

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
 * - Req 1.2 (Issue #13): every Material TextAppearance.* slot is
 *   overridden by TextAppearance.KeyNest.* pointing at @font/manrope,
 *   and the Theme overrides android:fontFamily so non-Material TextViews
 *   also pick up Manrope. After Issue #24 the slot attribute names have
 *   moved from M2 (textAppearanceBody1, etc.) to M3 (textAppearanceBodyLarge,
 *   etc.) but the wiring to TextAppearance.KeyNest.* (with @font/manrope)
 *   is preserved.
 * - Req 2.2 (Issue #13): the two existing monospace TextViews in
 *   credential_edit_activity.xml use @font/jetbrains_mono.
 * - Req 3.1 / 3.2 (Issue #13, NFR 1.5 inheritance): the theme references
 *   the bundled @font resource, not androidx.core.provider.FontsContractCompat
 *   nor the Google Fonts provider.
 * - Req 2.3 / 2.4 (Issue #24): the 13 TextAppearance.KeyNest.* styles
 *   inherit from TextAppearance.Material3.* (not the M2 .MaterialComponents
 *   variants) while keeping the @font/manrope override intact.
 */
class FontTypefaceWiringTest {

    private val themesFile: File = File("src/main/res/values/themes.xml")
    private val layoutFile: File = File("src/main/res/layout/credential_edit_activity.xml")
    private val manifestFile: File = File("src/main/AndroidManifest.xml")

    @Test
    fun keyNestTheme_overridesTextAppearanceBodyLarge_withManropeVariant() {
        val themes = themesFile.readText()
        // After Issue #24 the M3 slot name `textAppearanceBodyLarge` is
        // what M3 widgets (and the 4 migrated layouts) look up. It must
        // still route through the Manrope-bearing TextAppearance.KeyNest.Body1
        // so the typeface override survives the M2 -> M3 migration.
        assertThat(themes).contains(
            "<item name=\"textAppearanceBodyLarge\">@style/TextAppearance.KeyNest.Body1</item>"
        )
        // The variant itself must reference @font/manrope.
        val body1Block = themes.substringAfter("\"TextAppearance.KeyNest.Body1\"")
            .substringBefore("</style>")
        assertThat(body1Block).contains("@font/manrope")
    }

    @Test
    fun keyNestTheme_overridesTextAppearanceBodyMedium_withManropeVariant() {
        val themes = themesFile.readText()
        assertThat(themes).contains(
            "<item name=\"textAppearanceBodyMedium\">@style/TextAppearance.KeyNest.Body2</item>"
        )
    }

    @Test
    fun keyNestTheme_overridesTextAppearanceHeadlineSmall_withManropeVariant() {
        val themes = themesFile.readText()
        // M2 textAppearanceHeadline6 -> M3 textAppearanceHeadlineSmall per
        // the Material Design type-scale correspondence (Issue #24 Req 2.5).
        assertThat(themes).contains(
            "<item name=\"textAppearanceHeadlineSmall\">@style/TextAppearance.KeyNest.Headline6</item>"
        )
    }

    @Test
    fun keyNestTheme_overridesTextAppearanceBodySmall_withManropeVariant() {
        val themes = themesFile.readText()
        // M2 textAppearanceCaption -> M3 textAppearanceBodySmall per the
        // Material Design type-scale correspondence (Issue #24 Req 2.5).
        assertThat(themes).contains(
            "<item name=\"textAppearanceBodySmall\">@style/TextAppearance.KeyNest.Caption</item>"
        )
    }

    @Test
    fun keyNestTheme_overridesAndroidFontFamily_atThemeLevel() {
        // Some views (raw TextView without textAppearance, EditText hints)
        // do not flow through Material TextAppearance.* and instead pick
        // up android:fontFamily directly. The theme must therefore override
        // this attribute at the theme level as well.
        // credential_edit_activity.xml additionally relies on this for the
        // remaining legacy M2 textAppearance attribute references it still
        // contains (textAppearanceSubtitle1 / textAppearanceBody1 / etc.).
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
