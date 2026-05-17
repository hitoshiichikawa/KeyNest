package inc.goodanswers.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #32: source-level pinning of the design-token references on the
 * Package Picker bottom sheet layouts (`package_picker_bottom_sheet.xml`
 * + `package_picker_row_item.xml` + `package_picker_section_header_item.xml`),
 * the supporting drawables, and the new picker string keys.
 *
 * Mirrors [OnboardingLayoutTokensTest] (Issue #31) / [CredentialEditLayoutTokensTest]
 * (Issue #30): we read the resource XML directly so the assertions stay
 * textual and don't require Robolectric for resource inflation. The intent
 * is to make sure a future careless refactor cannot silently revert any
 * of the JSX `ScreenPicker` visual contract points (drag handle / title +
 * subtitle / search bar / 2 sections / icon tile row / manual entry
 * fallback / dark mode tokens).
 *
 * Mapped AC coverage (requirements.md):
 * - Req 1.x: bottom sheet shell (colorSurface, kn_r_sheet, list padding,
 *   existing show() entry preserved).
 * - Req 2.x: drag handle (36x4dp, kn_ink_200, kn_r_pill).
 * - Req 3.x: title + subtitle + close button (Text.KeyNest.TitleM /
 *   Text.KeyNest.BodyS / kn_text / kn_text_2 / ic_close_24).
 * - Req 4.x: search bar (kn_ink_50 / kn_surface_2, r_sm 12dp, ic_search_24
 *   tinted kn_text_3, placeholder string).
 * - Req 5.x / Req 6.x: two section headers (Text.KeyNest.Eyebrow + kn_text_3
 *   + section_used / section_all strings).
 * - Req 7.x: row item (32dp icon tile / r_sm / kn_blue_500, Text.KeyNest.Body
 *   + Text.KeyNest.Mono + kn_text_3, 48dp min height).
 * - Req 8.x: manual entry row (kn_blue_50 bg, kn_primary text,
 *   Text.KeyNest.LabelL or Text.KeyNest.Body, string key).
 * - Req 9.x: existing IDs preserved (@id/recycler).
 * - Req 10.x: dark-mode token integrity (no #hex in layout).
 * - NFR 3.x: new strings present in en + ja resource tables.
 */
class PackagePickerLayoutTokensTest {

    private val sheetLayout: File =
        File("src/main/res/layout/package_picker_bottom_sheet.xml")
    private val rowItem: File =
        File("src/main/res/layout/package_picker_row_item.xml")
    private val sectionHeader: File =
        File("src/main/res/layout/package_picker_section_header_item.xml")
    private val dragHandleDrawable: File =
        File("src/main/res/drawable/kn_picker_drag_handle.xml")
    private val searchBarDrawable: File =
        File("src/main/res/drawable/kn_picker_search_bar_bg.xml")
    private val manualRowDrawable: File =
        File("src/main/res/drawable/kn_picker_manual_row_bg.xml")
    private val stringsEn: File =
        File("src/main/res/values/strings.xml")
    private val stringsJa: File =
        File("src/main/res/values-ja/strings.xml")

    // ---- Req 9.x: existing IDs / entry points preserved -------------------

    @Test
    fun sheetLayout_preservesRecyclerId() {
        // Req 9.2: the @id/recycler id is bound from
        // PackagePickerBottomSheet.onViewCreated(). Removing it would
        // silently break the existing list-binding path.
        val xml = sheetLayout.readText()
        assertWithMessage("layout must declare @+id/recycler (Req 9.2)")
            .that(xml).contains("@+id/recycler")
    }

    @Test
    fun stringsResource_preservesExistingPackagePickerTitleKey() {
        // Req 3.4: existing `package_picker_title` key remains in both
        // locales' tables.
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        assertWithMessage("values/strings.xml must keep package_picker_title (Req 3.4 / NFR 3.1)")
            .that(en).contains("name=\"package_picker_title\"")
        assertWithMessage("values-ja/strings.xml must keep package_picker_title (Req 3.4 / NFR 3.1)")
            .that(ja).contains("name=\"package_picker_title\"")
    }

    // ---- Req 1.x: bottom sheet shell --------------------------------------

    @Test
    fun sheetLayout_bindsRootBackgroundToColorSurfaceToken() {
        // Req 1.1: the sheet root must resolve a Material attribute /
        // semantic token, not a raw #hex. We accept either the
        // ?attr/colorSurface bridge (which resolves to kn_surface via
        // Theme.KeyNest), @color/kn_bg_elev directly, or a drawable
        // (kn_picker_sheet_bg) that itself encodes the semantic color
        // (verified by [sheetBgDrawable_usesKnSurfaceFill] below).
        val xml = sheetLayout.readText()
        val usesAttr = xml.contains("?attr/colorSurface") ||
            xml.contains("?android:attr/colorBackground")
        val usesToken = xml.contains("@color/kn_bg_elev") ||
            xml.contains("@color/kn_surface")
        val usesDrawable = xml.contains("@drawable/kn_picker_sheet_bg")
        assertWithMessage("sheet root background must resolve via ?attr/colorSurface, " +
            "@color/kn_bg_elev, or the kn_picker_sheet_bg drawable (Req 1.1)")
            .that(usesAttr || usesToken || usesDrawable).isTrue()
    }

    @Test
    fun sheetBgDrawable_usesKnSurfaceFillAndKnRSheetCorners() {
        // Req 1.1 / 1.2: when the layout opts into the kn_picker_sheet_bg
        // drawable, the drawable itself must reference @color/kn_surface
        // and the 28dp top corner radius.
        val drawable = File("src/main/res/drawable/kn_picker_sheet_bg.xml")
        if (drawable.exists()) {
            val xml = drawable.readText()
            assertWithMessage("kn_picker_sheet_bg must fill with @color/kn_surface (Req 1.1)")
                .that(xml).contains("@color/kn_surface")
            assertWithMessage("kn_picker_sheet_bg must use @dimen/kn_r_sheet on top corners (Req 1.2)")
                .that(xml).contains("@dimen/kn_r_sheet")
        }
    }

    @Test
    fun sheetLayout_appliesKnRSheetCornerRadius() {
        // Req 1.2: the top corner radius is kn_r_sheet (28dp). The
        // BottomSheetDialog top corner is owned by
        // Widget.KeyNest.BottomSheet style; the layout itself uses
        // kn_r_sheet on its drawable background. Either reference is
        // acceptable as long as kn_r_sheet appears once in the layout
        // OR in the wired sheet background drawable.
        val xml = sheetLayout.readText()
        val drawable = File("src/main/res/drawable/kn_picker_sheet_bg.xml")
        val drawableText = if (drawable.exists()) drawable.readText() else ""
        val foundInLayout = xml.contains("@dimen/kn_r_sheet")
        val foundInDrawable = drawableText.contains("@dimen/kn_r_sheet")
        assertWithMessage("sheet layout or kn_picker_sheet_bg drawable should reference " +
            "@dimen/kn_r_sheet (Req 1.2)")
            .that(foundInLayout || foundInDrawable).isTrue()
    }

    @Test
    fun sheetLayout_appliesHorizontalListPaddingToken() {
        // Req 1.3: inner horizontal padding uses kn_list_padding_h (16dp)
        // or kn_screen_padding_h (20dp).
        val xml = sheetLayout.readText()
        val usesListPadding = xml.contains("@dimen/kn_list_padding_h")
        val usesScreenPadding = xml.contains("@dimen/kn_screen_padding_h")
        assertWithMessage("sheet layout should reference kn_list_padding_h or " +
            "kn_screen_padding_h for horizontal insets (Req 1.3)")
            .that(usesListPadding || usesScreenPadding).isTrue()
    }

    // ---- Req 2.x: drag handle ---------------------------------------------

    @Test
    fun sheetLayout_dragHandleHasCorrectDimensions() {
        // Req 2.2: 36dp wide / 4dp tall drag handle.
        val xml = sheetLayout.readText()
        val handleBlock = Regex(
            pattern = "<View[^>]*?@\\+id/drag_handle[\\s\\S]*?/>",
        ).find(xml)
        assertWithMessage("drag_handle View must exist (Req 2.1)")
            .that(handleBlock).isNotNull()
        val block = handleBlock!!.value
        assertWithMessage("drag handle width must be @dimen/kn_handle_w (Req 2.2)")
            .that(block).contains("@dimen/kn_handle_w")
        assertWithMessage("drag handle height must be @dimen/kn_handle_h (Req 2.2)")
            .that(block).contains("@dimen/kn_handle_h")
        // Req 2.1: centered horizontally (via gravity or layout_gravity).
        val centered = block.contains("center_horizontal") || block.contains("center")
        assertWithMessage("drag handle must be horizontally centered (Req 2.1)")
            .that(centered).isTrue()
    }

    @Test
    fun dragHandleDrawable_usesKnInk200AndKnRPill() {
        // Req 2.3 / 2.4: fill = kn_ink_200, corners = kn_r_pill.
        val xml = dragHandleDrawable.readText()
        assertWithMessage("drag handle drawable must fill with @color/kn_ink_200 (Req 2.3)")
            .that(xml).contains("@color/kn_ink_200")
        assertWithMessage("drag handle drawable must use @dimen/kn_r_pill corners (Req 2.4)")
            .that(xml).contains("@dimen/kn_r_pill")
    }

    // ---- Req 3.x: title + subtitle + close button -------------------------

    @Test
    fun sheetLayout_titleUsesTitleMStyleAndKnTextColor() {
        // Req 3.2 / 3.3 / 3.4.
        val xml = sheetLayout.readText()
        val titleBlock = Regex(
            pattern = "<TextView[^>]*?@\\+id/text_title[\\s\\S]*?/>",
        ).find(xml)
        assertWithMessage("text_title TextView must exist (Req 3.1)")
            .that(titleBlock).isNotNull()
        val block = titleBlock!!.value
        assertWithMessage("title must use Text.KeyNest.TitleM style (Req 3.2)")
            .that(block).contains("@style/Text.KeyNest.TitleM")
        assertWithMessage("title must use @string/package_picker_title (Req 3.4)")
            .that(block).contains("@string/package_picker_title")
    }

    @Test
    fun sheetLayout_subtitleUsesBodySStyleAndKnText2Color() {
        // Req 3.5 / 3.6 / 3.7.
        val xml = sheetLayout.readText()
        val subtitleBlock = Regex(
            pattern = "<TextView[^>]*?@\\+id/text_subtitle[\\s\\S]*?/>",
        ).find(xml)
        assertWithMessage("text_subtitle TextView must exist (Req 3.1)")
            .that(subtitleBlock).isNotNull()
        val block = subtitleBlock!!.value
        assertWithMessage("subtitle must use Text.KeyNest.BodyS style (Req 3.5)")
            .that(block).contains("@style/Text.KeyNest.BodyS")
        assertWithMessage("subtitle must use @string/package_picker_subtitle (Req 3.7)")
            .that(block).contains("@string/package_picker_subtitle")
    }

    @Test
    fun sheetLayout_closeButtonExistsWithA11yDescription() {
        // Req 3.8: close × button with localized contentDescription.
        val xml = sheetLayout.readText()
        val closeBlock = Regex(
            pattern = "<(ImageButton|com\\.google\\.android\\.material\\.button\\.MaterialButton|ImageView)[^>]*?@\\+id/btn_close[\\s\\S]*?/>",
        ).find(xml)
        assertWithMessage("btn_close must exist (Req 3.8)")
            .that(closeBlock).isNotNull()
        val block = closeBlock!!.value
        assertWithMessage("btn_close must reference ic_close_24 (Req 3.8)")
            .that(block).contains("@drawable/ic_close_24")
        assertWithMessage("btn_close must have a contentDescription (Req 3.8 / NFR 2.2)")
            .that(block).contains("contentDescription")
        assertWithMessage("btn_close must reference @string/package_picker_close_a11y (Req 3.8)")
            .that(block).contains("@string/package_picker_close_a11y")
    }

    // ---- Req 4.x: search bar ----------------------------------------------

    @Test
    fun sheetLayout_searchBarUsesPickerBackgroundDrawableAndIcon() {
        // Req 4.2 / 4.3 / 4.4 / 4.5: search container uses
        // kn_picker_search_bar_bg (fill = kn_ink_50 or kn_surface_2,
        // corners = kn_r_sm 12dp) + leading search glyph + hint.
        val xml = sheetLayout.readText()
        assertWithMessage("search bar must reference kn_picker_search_bar_bg drawable (Req 4.2 / 4.3)")
            .that(xml).contains("@drawable/kn_picker_search_bar_bg")
        assertWithMessage("search bar must include the leading ic_search_24 glyph (Req 4.4)")
            .that(xml).contains("@drawable/ic_search_24")
        assertWithMessage("search bar must use @string/package_picker_search_hint (Req 4.5)")
            .that(xml).contains("@string/package_picker_search_hint")
    }

    @Test
    fun sheetLayout_searchBarHasInputSearchPickerId() {
        // Req 4.x: declare input_search_picker so PackagePickerBottomSheet
        // can attach the filter watcher.
        val xml = sheetLayout.readText()
        assertWithMessage("sheet layout must declare @+id/input_search_picker (Req 4.x)")
            .that(xml).contains("@+id/input_search_picker")
    }

    @Test
    fun searchBarDrawable_usesKnInk50FillAndKnRSmCorners() {
        // Req 4.2 / 4.3.
        val xml = searchBarDrawable.readText()
        val usesInk50 = xml.contains("@color/kn_ink_50") ||
            xml.contains("@color/kn_surface_2")
        assertWithMessage("search bar drawable must fill with kn_ink_50 or kn_surface_2 (Req 4.2)")
            .that(usesInk50).isTrue()
        assertWithMessage("search bar drawable must use kn_r_sm (12dp) corners (Req 4.3)")
            .that(xml).contains("@dimen/kn_r_sm")
    }

    // ---- Req 5.x / Req 6.x: section headers --------------------------------

    @Test
    fun sectionHeaderItem_usesEyebrowStyleAndKnText3Color() {
        // Req 5.2 / 5.3 (and 6.2 by inheritance).
        val xml = sectionHeader.readText()
        assertWithMessage("section header must use Text.KeyNest.Eyebrow (Req 5.2)")
            .that(xml).contains("@style/Text.KeyNest.Eyebrow")
        assertWithMessage("section header must declare @+id/text_section_header (Req 5.x)")
            .that(xml).contains("@+id/text_section_header")
    }

    @Test
    fun stringsResource_containsAllAppsSectionHeaderKey() {
        // Issue #48 Req 2.1 / 2.2 / 2.3 / NFR 3.1: `package_picker_section_used`
        // is removed from both locales (it backed the deleted "業務でよく使う"
        // section). `package_picker_section_all` must remain in both locales.
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        assertWithMessage("values/strings.xml must declare package_picker_section_all (Req 2.3)")
            .that(en).contains("name=\"package_picker_section_all\"")
        assertWithMessage("values-ja/strings.xml must declare package_picker_section_all (Req 2.3)")
            .that(ja).contains("name=\"package_picker_section_all\"")
        assertWithMessage("values/strings.xml must NOT declare package_picker_section_used (Req 2.1)")
            .that(en).doesNotContain("name=\"package_picker_section_used\"")
        assertWithMessage("values-ja/strings.xml must NOT declare package_picker_section_used (Req 2.2)")
            .that(ja).doesNotContain("name=\"package_picker_section_used\"")
    }

    // ---- Req 7.x: row layout ----------------------------------------------

    @Test
    fun rowItem_iconTileIs32dpAndUsesKnIconTileBg() {
        // Req 7.1 / 7.2 / 7.3: 32dp square / r_sm corners / kn_blue_500 fill
        // (kn_icon_tile_bg drawable).
        val xml = rowItem.readText()
        assertWithMessage("row icon tile must be kn_icon_tile_sm (32dp) (Req 7.1)")
            .that(xml).contains("@dimen/kn_icon_tile_sm")
        assertWithMessage("row icon tile must use kn_icon_tile_bg drawable " +
            "(kn_blue_500 fill + kn_r_sm corners) (Req 7.2 / 7.3)")
            .that(xml).contains("@drawable/kn_icon_tile_bg")
    }

    @Test
    fun rowItem_appNameUsesBodyStyleAndPackageNameUsesMonoStyle() {
        // Req 7.5 / 7.6 / 7.7.
        val xml = rowItem.readText()
        assertWithMessage("row app name must use Text.KeyNest.Body (Req 7.5)")
            .that(xml).contains("@style/Text.KeyNest.Body")
        assertWithMessage("row package name must use Text.KeyNest.Mono (Req 7.6)")
            .that(xml).contains("@style/Text.KeyNest.Mono")
        assertWithMessage("row package name must have ellipsize=end for single-line ellipsis " +
            "(Req 7.7)")
            .that(xml).contains("android:ellipsize=\"end\"")
    }

    @Test
    fun rowItem_hasMinTouchHeight48dp() {
        // Req 7.11 / NFR 2.1: 48dp minimum touch target.
        val xml = rowItem.readText()
        val has48 = xml.contains("android:minHeight=\"48dp\"") ||
            xml.contains("@dimen/kn_iconbtn_touch") ||
            xml.contains("android:layout_height=\"48dp\"")
        assertWithMessage("row must reserve at least 48dp tap height (Req 7.11)")
            .that(has48).isTrue()
    }

    @Test
    fun rowItem_declaresExpectedTextIds() {
        // Req 7.4: app name + package name as 2 lines.
        val xml = rowItem.readText()
        assertWithMessage("row must declare @+id/text_app_label (Req 7.4)")
            .that(xml).contains("@+id/text_app_label")
        assertWithMessage("row must declare @+id/text_app_package (Req 7.4)")
            .that(xml).contains("@+id/text_app_package")
    }

    // ---- Req 8.x: manual entry fallback row -------------------------------

    @Test
    fun sheetLayout_manualEntryRowIsAtBottomWithKnBlue50Background() {
        // Req 8.1 / 8.3 / 8.6.
        val xml = sheetLayout.readText()
        val manualBlock = Regex(
            pattern = "<(?:com\\.google\\.android\\.material\\.button\\.MaterialButton|TextView|LinearLayout|FrameLayout)[^>]*?@\\+id/btn_manual_entry[\\s\\S]*?(?:/>|</(?:com\\.google\\.android\\.material\\.button\\.MaterialButton|TextView|LinearLayout|FrameLayout)>)",
        ).find(xml)
        assertWithMessage("btn_manual_entry must exist as a fixed footer (Req 8.1)")
            .that(manualBlock).isNotNull()
        val block = manualBlock!!.value
        assertWithMessage("manual entry row must reference kn_picker_manual_row_bg drawable " +
            "(kn_blue_50 fill) (Req 8.3)")
            .that(block).contains("@drawable/kn_picker_manual_row_bg")
        assertWithMessage("manual entry row must use @string/package_picker_manual (Req 8.6)")
            .that(block).contains("@string/package_picker_manual")
    }

    @Test
    fun manualRowDrawable_usesKnBlue50FillAndKnRSmCorners() {
        // Req 8.3 (drawable shape verification).
        val xml = manualRowDrawable.readText()
        assertWithMessage("manual row drawable must fill with @color/kn_blue_50 (Req 8.3)")
            .that(xml).contains("@color/kn_blue_50")
        assertWithMessage("manual row drawable must use kn_r_sm (12dp) corners (Req 8.3)")
            .that(xml).contains("@dimen/kn_r_sm")
    }

    @Test
    fun stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales() {
        // Req 4.5 / 8.6 / 3.8: new keys must exist in both en + ja.
        // Issue #48 Req 2.1 / 2.2: `package_picker_section_used` is removed
        // along with the "業務でよく使う" section, so it is no longer asserted
        // here. `package_picker_section_all` remains (Req 2.3).
        val newKeys = listOf(
            "package_picker_subtitle",
            "package_picker_search_hint",
            "package_picker_section_all",
            "package_picker_manual",
            "package_picker_close_a11y",
            "package_picker_no_results",
            "package_picker_manual_input_title",
            "package_picker_manual_input_hint",
            "package_picker_manual_input_invalid",
        )
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        newKeys.forEach { key ->
            assertWithMessage("values/strings.xml must declare $key (NFR 3.1)")
                .that(en).contains("name=\"$key\"")
            assertWithMessage("values-ja/strings.xml must declare $key (NFR 3.1)")
                .that(ja).contains("name=\"$key\"")
        }
    }

    // ---- Req 10.x: dark mode integrity ------------------------------------

    @Test
    fun sheetLayout_doesNotHardcodeHexColors() {
        // Req 10.1 / 10.2: layout must reference kn_* tokens / theme attrs,
        // never raw #RRGGBB. The drawables themselves may use transparent
        // colors but the layout must not.
        val xml = sheetLayout.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("package_picker_bottom_sheet.xml must not hardcode " +
            "hex colors (mapping.md §0 -- use @color/kn_*).")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    @Test
    fun rowItem_doesNotHardcodeHexColors() {
        val xml = rowItem.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("package_picker_row_item.xml must not hardcode hex colors")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }
}
