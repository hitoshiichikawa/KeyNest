package inc.goodanswers.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #24: source-level pinning of the Material 2 -> Material 3 theme
 * migration that resolves the
 * `IllegalArgumentException: This component requires that you specify a
 * valid TextAppearance attribute` crash on cold start of
 * `CredentialListActivity`.
 *
 * The crash root cause was Theme.KeyNest inheriting from
 * Theme.MaterialComponents.* (M2) while the 4 migrated layouts
 * (credential_list_activity / settings_activity / danger_zone_activity /
 * oss_licenses_item) reference Widget.Material3.* widgets and M3
 * TextAppearance attributes (?attr/textAppearanceTitleMedium / BodyLarge /
 * etc.) that the M2 parent theme cannot resolve.
 *
 * Source-level pinning is chosen for the same reason as
 * FontTypefaceWiringTest: the artefact under test is a pure XML resource,
 * so checking the textual content is sufficient and avoids the cost of a
 * Robolectric Activity spin-up.
 *
 * Mapped AC coverage:
 * - Req 2.1: Theme.KeyNest parent is the Material 3 DayNight NoActionBar.
 * - Req 2.2: the 13 textAppearance attribute slots inside Theme.KeyNest
 *   are renamed to their M3 type-scale names.
 * - Req 2.3: the 13 TextAppearance.KeyNest.* styles inherit from
 *   TextAppearance.Material3.* (not the M2 .MaterialComponents.* variants).
 * - Req 2.4: each of the 13 TextAppearance.KeyNest.* styles still carries
 *   the @font/manrope override on both fontFamily attribute namespaces.
 * - Req 2.5: the M2->M3 mapping follows the Material Design public
 *   type-scale correspondence (Headline1 -> DisplayLarge, Subtitle1 ->
 *   TitleMedium, Body1 -> BodyLarge, etc.).
 * - Req 3.5: Theme.KeyNest.Translucent is left alone (parent unchanged)
 *   so the AutofillUnlockActivity transparent-launch behaviour is
 *   preserved across this fix.
 * - Req 5.2: existing FontTypefaceWiringTest assertions were updated to
 *   reflect the new attribute names instead of being relaxed.
 *
 * Indirect coverage for Req 1.x / Req 4.x: the layout-time crash
 * (`IllegalArgumentException ... valid TextAppearance attribute`) is
 * deterministically resolved when (a) the parent theme is M3 and (b) the
 * 13 M3 attribute slot names are all defined on Theme.KeyNest. Per-screen
 * cold-start verification on a real device / emulator is out of scope for
 * unit tests and is documented in impl-notes.md "確認事項" for human
 * reviewer follow-up.
 */
class Material3ThemeMigrationTest {

    private val themesFile: File = File("src/main/res/values/themes.xml")

    /**
     * Material Design M2 -> M3 type-scale correspondence (Req 2.5). The
     * triple is:
     *   - the TextAppearance.KeyNest.* style name (kept on M2 names per
     *     Issue #24 Out of Scope: style names themselves are NOT renamed)
     *   - the new M3 parent style (TextAppearance.Material3.*)
     *   - the new M3 attribute slot name used inside Theme.KeyNest
     */
    private val expectedMigrations: List<Triple<String, String, String>> = listOf(
        Triple("TextAppearance.KeyNest.Headline1", "TextAppearance.Material3.DisplayLarge", "textAppearanceDisplayLarge"),
        Triple("TextAppearance.KeyNest.Headline2", "TextAppearance.Material3.DisplayMedium", "textAppearanceDisplayMedium"),
        Triple("TextAppearance.KeyNest.Headline3", "TextAppearance.Material3.DisplaySmall", "textAppearanceDisplaySmall"),
        Triple("TextAppearance.KeyNest.Headline4", "TextAppearance.Material3.HeadlineLarge", "textAppearanceHeadlineLarge"),
        Triple("TextAppearance.KeyNest.Headline5", "TextAppearance.Material3.HeadlineMedium", "textAppearanceHeadlineMedium"),
        Triple("TextAppearance.KeyNest.Headline6", "TextAppearance.Material3.HeadlineSmall", "textAppearanceHeadlineSmall"),
        Triple("TextAppearance.KeyNest.Subtitle1", "TextAppearance.Material3.TitleMedium", "textAppearanceTitleMedium"),
        Triple("TextAppearance.KeyNest.Subtitle2", "TextAppearance.Material3.TitleSmall", "textAppearanceTitleSmall"),
        Triple("TextAppearance.KeyNest.Body1", "TextAppearance.Material3.BodyLarge", "textAppearanceBodyLarge"),
        Triple("TextAppearance.KeyNest.Body2", "TextAppearance.Material3.BodyMedium", "textAppearanceBodyMedium"),
        Triple("TextAppearance.KeyNest.Button", "TextAppearance.Material3.LabelLarge", "textAppearanceLabelLarge"),
        Triple("TextAppearance.KeyNest.Caption", "TextAppearance.Material3.BodySmall", "textAppearanceBodySmall"),
        Triple("TextAppearance.KeyNest.Overline", "TextAppearance.Material3.LabelSmall", "textAppearanceLabelSmall"),
    )

    @Test
    fun themeKeyNest_inheritsFromMaterial3DayNightNoActionBar() {
        // Req 2.1: parent must be the M3 DayNight NoActionBar so M3
        // widgets and M3 attribute references resolve correctly.
        val themes = themesFile.readText()
        assertThat(themes).contains(
            "<style name=\"Theme.KeyNest\" parent=\"Theme.Material3.DayNight.NoActionBar\">"
        )
    }

    @Test
    fun themeKeyNest_doesNotInheritFromMaterialComponents() {
        // Req 2.1 / Req 1.4: the regression guard. If a future edit
        // accidentally reverts the parent the crash returns instantly.
        // We assert on the Theme.KeyNest declaration specifically rather
        // than the whole file because Theme.KeyNest.Translucent is
        // intentionally still on the M2 parent (Out of Scope).
        val themes = themesFile.readText()
        val keyNestDecl = themes.substringAfter("<style name=\"Theme.KeyNest\"")
            .substringBefore(">")
        assertThat(keyNestDecl).doesNotContain("Theme.MaterialComponents")
    }

    @Test
    fun every13TextAppearanceKeyNestStyle_inheritsFromItsMaterial3Counterpart() {
        // Req 2.3 + Req 2.5: each of the 13 styles must point at its M3
        // type-scale counterpart. Verifying the full 13 in one test (with
        // a single failure summary) keeps the test list focused while
        // still exercising every mapping.
        val themes = themesFile.readText()
        val missing = mutableListOf<String>()
        for ((styleName, m3Parent, _) in expectedMigrations) {
            val expectedDecl =
                "<style name=\"$styleName\"\n        parent=\"$m3Parent\">"
            if (!themes.contains(expectedDecl)) {
                missing += "$styleName -> $m3Parent"
            }
        }
        assertWithMessage("missing M2 -> M3 parent migrations")
            .that(missing).isEmpty()
    }

    @Test
    fun every13TextAppearanceKeyNestStyle_keepsManropeFontFamilyOverride() {
        // Req 2.4: even after the parent moves to M3, each style must
        // still override fontFamily / android:fontFamily with
        // @font/manrope. If a future refactor (e.g. switching style
        // names) drops the override the bundled typeface stops applying.
        val themes = themesFile.readText()
        val missing = mutableListOf<String>()
        for ((styleName, _, _) in expectedMigrations) {
            val block = themes.substringAfter("\"$styleName\"")
                .substringBefore("</style>")
            val hasAndroidNs =
                block.contains("<item name=\"android:fontFamily\">@font/manrope</item>")
            val hasAppNs =
                block.contains("<item name=\"fontFamily\">@font/manrope</item>")
            if (!hasAndroidNs || !hasAppNs) {
                missing += styleName
            }
        }
        assertWithMessage("styles missing @font/manrope override")
            .that(missing).isEmpty()
    }

    @Test
    fun themeKeyNest_declaresEveryMaterial3TypeScaleAttributeSlot() {
        // Req 2.2: each of the 13 M3 attribute slot names must be
        // declared on Theme.KeyNest and point at the corresponding
        // TextAppearance.KeyNest.* style. Without this, M3 widgets and
        // M3-attribute-using TextViews would fall back to the M3 parent
        // theme's default TextAppearance and drop the Manrope override.
        val themes = themesFile.readText()
        val themeBlock = themes.substringAfter("\"Theme.KeyNest\" parent")
            .substringBefore("</style>")
        val missing = mutableListOf<String>()
        for ((styleName, _, m3Attr) in expectedMigrations) {
            val expectedItem = "<item name=\"$m3Attr\">@style/$styleName</item>"
            if (!themeBlock.contains(expectedItem)) {
                missing += "$m3Attr -> $styleName"
            }
        }
        assertWithMessage("missing M3 textAppearance slot wiring")
            .that(missing).isEmpty()
    }

    @Test
    fun themeKeyNest_doesNotRetainAnyMaterialComponentsTextAppearanceParent() {
        // Req 2.3 regression guard: ensure no TextAppearance.KeyNest.*
        // style still inherits from the M2 .MaterialComponents.*
        // variant. A leftover M2 parent would re-introduce the
        // pre-migration sizing/spacing on that one slot while everything
        // else uses M3 metrics, producing inconsistent typography.
        val themes = themesFile.readText()
        // We only check the body of the 13 TextAppearance.KeyNest.*
        // declarations (between their <style ...> tag and </style>).
        // Theme.KeyNest itself doesn't have a parent= attribute pointing
        // at MaterialComponents after the migration, but
        // Theme.KeyNest.Translucent legitimately still does so we cannot
        // do a plain file-wide assertion.
        val offending = mutableListOf<String>()
        for ((styleName, _, _) in expectedMigrations) {
            val decl = themes.substringAfter("<style name=\"$styleName\"")
                .substringBefore(">")
            if (decl.contains("TextAppearance.MaterialComponents")) {
                offending += styleName
            }
        }
        assertWithMessage("styles still inheriting from M2 parent")
            .that(offending).isEmpty()
    }

    @Test
    fun themeKeyNest_doesNotDeclareAnyMaterial2TextAppearanceSlotName() {
        // Req 2.2 regression guard: ensure the M2 slot names
        // (textAppearanceHeadline1..6, textAppearanceSubtitle1/2,
        // textAppearanceBody1/2, textAppearanceButton, textAppearanceCaption,
        // textAppearanceOverline) are NOT redeclared on Theme.KeyNest.
        // Leaving stale M2 attribute names alongside the new M3 names
        // would (a) bloat the theme and (b) hide latent breakage if the
        // M2 attribute resolution behaviour ever changes in a future
        // Material library bump.
        val themes = themesFile.readText()
        val themeBlock = themes.substringAfter("\"Theme.KeyNest\" parent")
            .substringBefore("</style>")
        val m2Slots = listOf(
            "textAppearanceHeadline1",
            "textAppearanceHeadline2",
            "textAppearanceHeadline3",
            "textAppearanceHeadline4",
            "textAppearanceHeadline5",
            "textAppearanceHeadline6",
            "textAppearanceSubtitle1",
            "textAppearanceSubtitle2",
            "textAppearanceBody1",
            "textAppearanceBody2",
            "textAppearanceButton",
            "textAppearanceCaption",
            "textAppearanceOverline",
        )
        val stillPresent = m2Slots.filter { slot ->
            // Use the full <item name="..."> token so we don't false-match
            // similarly named M3 slots (textAppearanceBodyLarge contains
            // the substring textAppearanceBody but is a different token).
            themeBlock.contains("<item name=\"$slot\">")
        }
        assertWithMessage("residual M2 textAppearance slot names")
            .that(stillPresent).isEmpty()
    }

    @Test
    fun themeKeyNestTranslucent_isUnchangedByThisMigration() {
        // Req 3.5 + Out of Scope: Theme.KeyNest.Translucent's parent must
        // remain on the M2 DayNight NoActionBar because the migration is
        // scoped to Theme.KeyNest only. The translucent theme is used
        // by AutofillUnlockActivity, which never inflates the M3 widgets
        // that triggered the crash, so leaving it alone preserves its
        // transparent-launch behaviour bit-for-bit.
        val themes = themesFile.readText()
        assertThat(themes).contains(
            "<style name=\"Theme.KeyNest.Translucent\" " +
                "parent=\"Theme.MaterialComponents.DayNight.NoActionBar\">"
        )
    }
}
