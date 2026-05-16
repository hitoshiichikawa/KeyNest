package inc.goodanswers.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #34: source-level pinning of the Autofill dataset popup layout
 * (`dataset_presentation.xml`) and its supporting drawables / strings.
 *
 * Mirrors [SettingsLayoutTokensTest] (Issue #33) /
 * [PackagePickerLayoutTokensTest] (Issue #32): we read the resource XML
 * directly so the assertions stay textual and don't require Robolectric
 * for resource inflation. The intent is to guarantee that a future
 * careless refactor cannot silently revert any of the JSX `ScreenDataset`
 * visual contract points (KEYNEST header / signature chip / dataset row /
 * footer "新規作成" row), the existing View ID hooks
 * (`dataset_label` / `dataset_subtitle`) that the autofill builder
 * depends on, or the dark-mode contrast safety net for the footer row.
 *
 * Mapped AC coverage (requirements.md):
 * - Req 1.x: root surface uses RemoteViews-safe Views only / kn_surface
 *   background / kn_* token references / 2 sections vertical / existing
 *   View IDs preserved.
 * - Req 2.x: KEYNEST eyebrow header — kn_dataset_header_bg fill +
 *   kn_surface_2 / kn_space_* padding / KNMark via ic_keynest_mark_24 +
 *   kn_primary tint / eyebrow inline TextAppearance (11sp / bold /
 *   letterSpacing / textAllCaps) / kn_success_soft pill chip + shield
 *   icon + signature_match string + kn_success text.
 * - Req 3.x: Dataset row — minHeight 48dp / paddings / 32dp icon tile via
 *   kn_icon_tile_sm + kn_icon_tile_bg / 24dp key vector center / label
 *   13sp bold kn_text ellipsize / subtitle 11sp kn_text_2 ellipsize /
 *   trailing 16dp ic_lock_outline_16 with kn_text_2 tint.
 * - Req 4.x: drawable resources — ic_keynest_mark_24 / ic_key_24 /
 *   ic_plus_24 reused; ic_lock_outline_16 added; kn_dataset_create_row_bg
 *   uses @color/kn_blue_50 with no #RRGGBB direct literals.
 * - Req 5.x: footer "新規作成" row — kn_dataset_create_row_bg fill /
 *   plus icon 16dp + kn_primary tint / label 13sp bold kn_primary /
 *   string reference to autofill_dataset_create_new.
 * - Req 6.x: dark mode safety net — drawable-night override for
 *   kn_dataset_create_row_bg via kn_primary_container.
 * - Req 7.x: existing View ID hooks (`dataset_label` / `dataset_subtitle`)
 *   remain so DatasetPresentationFactory.build does not break.
 * - NFR 1.x: RemoteViews compliance — no `?attr/...` attribute references
 *   in the layout file.
 * - NFR 2.x: new string keys present in both en + ja resource tables.
 * - NFR 4.x: decorative icons declare `@null` contentDescription or
 *   `importantForAccessibility="no"`; the lock icon advertises its
 *   meaning via autofill_dataset_row_lock_a11y.
 */
class DatasetPresentationLayoutTokensTest {

    private val layoutFile: File =
        File("src/main/res/layout/dataset_presentation.xml")
    private val createRowBgLight: File =
        File("src/main/res/drawable/kn_dataset_create_row_bg.xml")
    private val createRowBgNight: File =
        File("src/main/res/drawable-night/kn_dataset_create_row_bg.xml")
    private val headerBg: File =
        File("src/main/res/drawable/kn_dataset_header_bg.xml")
    private val lockIcon: File =
        File("src/main/res/drawable/ic_lock_outline_16.xml")
    private val stringsEn: File =
        File("src/main/res/values/strings.xml")
    private val stringsJa: File =
        File("src/main/res/values-ja/strings.xml")

    /**
     * Returns the layout XML with comment blocks removed so that slice
     * extraction (xml.indexOf("...") -> xml.lastIndexOf("<TagName", idx))
     * cannot accidentally land inside the documentation header. Several
     * tokens (`?attr/`, `dataset_label`, `ic_keynest_mark_24` ...) are
     * intentionally mentioned in the file's leading comment block.
     */
    private fun layoutXml(): String = stripXmlComments(layoutFile.readText())

    // ---- Req 1.x / Req 7: existing View IDs preserved ----------------------

    @Test
    fun layout_preservesDatasetLabelAndSubtitleViewIds() {
        // Req 1.5 / 3.9 / 3.13 / 7.2: DatasetPresentationFactory.build relies
        // on these IDs to setTextViewText. Removal would break Autofill UI.
        val xml = layoutFile.readText()
        assertWithMessage("dataset_presentation.xml must declare @+id/dataset_label " +
            "(Req 1.5 / 3.9 / 7.2)")
            .that(xml).contains("@+id/dataset_label")
        assertWithMessage("dataset_presentation.xml must declare @+id/dataset_subtitle " +
            "(Req 1.5 / 3.13 / 7.2)")
            .that(xml).contains("@+id/dataset_subtitle")
    }

    @Test
    fun layout_rootContainerUsesKnSurfaceBackground() {
        // Req 1.2 / 6.6: root must paint a deterministic background so the
        // host activity's color does not bleed through.
        val xml = layoutFile.readText()
        // Find the root LinearLayout opening tag (first <LinearLayout in file).
        val rootStart = xml.indexOf("<LinearLayout")
        assertWithMessage("root LinearLayout must be present (Req 1.1)")
            .that(rootStart).isAtLeast(0)
        val rootEnd = xml.indexOf(">", rootStart)
        val rootBlock = xml.substring(rootStart, rootEnd + 1)
        assertWithMessage("root LinearLayout must reference @color/kn_surface as background " +
            "(Req 1.2)")
            .that(rootBlock).contains("@color/kn_surface")
    }

    @Test
    fun layout_usesRemoteViewsCompatibleViewTypesOnly() {
        // Req 1.1 / NFR 1.3: only LinearLayout / FrameLayout / RelativeLayout /
        // TextView / ImageView / Button / ProgressBar etc. are remotable.
        // We assert the layout contains no `<View` element with banned types.
        val xml = layoutFile.readText()
        val forbidden = listOf(
            "<androidx.",
            "<com.google.android.material",
            "<inc.goodanswers.keynest",
            "<MaterialButton",
            "<MaterialCardView",
            "<ConstraintLayout",
        )
        forbidden.forEach { token ->
            assertWithMessage("dataset_presentation.xml must NOT use $token " +
                "(RemoteViews limit / Req 1.1 / NFR 1.3)")
                .that(xml).doesNotContain(token)
        }
    }

    @Test
    fun layout_doesNotReferenceAttrColorTokens() {
        // Req 1.1 / NFR 1.3: ?attr/... is forbidden because host activity
        // theme may not resolve Material3 attributes. Strip XML comments
        // first so the documentation block can freely mention the banned
        // syntax without false positives.
        val xml = stripXmlComments(layoutFile.readText())
        val attrPattern = Regex("\\?attr/")
        assertWithMessage("dataset_presentation.xml must NOT reference ?attr/... " +
            "(host theme unsafe / Req 1.1 / NFR 1.3)")
            .that(attrPattern.containsMatchIn(xml)).isFalse()
    }

    private fun stripXmlComments(xml: String): String =
        Regex("<!--[\\s\\S]*?-->").replace(xml, "")

    @Test
    fun layout_doesNotHardcodeHexColorsInAttributes() {
        // Req 1.3 / Req 4.5: layout must reference @color/kn_* not #RRGGBB.
        val xml = layoutFile.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("dataset_presentation.xml must not hardcode hex colors " +
            "(Req 1.3 / 4.5)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    @Test
    fun layout_isVerticalLinearLayoutAtRoot() {
        // Req 1.4: 2 段構成 (header + dataset 行). vertical orientation is the
        // structural prerequisite — Architect chose to add the optional
        // footer row as a 3rd section per Req 5.1 (which sits below the
        // dataset row).
        val xml = layoutFile.readText()
        val rootStart = xml.indexOf("<LinearLayout")
        val rootEnd = xml.indexOf(">", rootStart)
        val rootBlock = xml.substring(rootStart, rootEnd + 1)
        assertWithMessage("root LinearLayout orientation must be vertical (Req 1.4)")
            .that(rootBlock).contains("android:orientation=\"vertical\"")
    }

    // ---- Req 2.x: KEYNEST eyebrow header -----------------------------------

    @Test
    fun layout_headerUsesKnDatasetHeaderBgFill() {
        // Req 2.2: header background = kn_dataset_header_bg (kn_surface_2 fill).
        val xml = layoutFile.readText()
        assertWithMessage("header must reference @drawable/kn_dataset_header_bg (Req 2.2)")
            .that(xml).contains("@drawable/kn_dataset_header_bg")
    }

    @Test
    fun headerBgDrawable_usesKnSurface2Fill() {
        // Req 2.2: kn_surface_2 semantic token (= kn_ink_50 light / kn_ink_800 dark).
        val xml = headerBg.readText()
        assertWithMessage("header bg drawable must reference @color/kn_surface_2 (Req 2.2)")
            .that(xml).contains("@color/kn_surface_2")
    }

    @Test
    fun layout_headerReferencesKnMarkAndTintsItPrimary() {
        // Req 2.4 / 2.5: KN mark vector at 16dp tinted @color/kn_primary.
        // Slice extraction operates on the comment-stripped XML so the
        // documentation block at the top of the file cannot shadow the
        // first real <ImageView occurrence.
        val xml = layoutXml()
        assertWithMessage("header must reference @drawable/ic_keynest_mark_24 (Req 2.4)")
            .that(xml).contains("@drawable/ic_keynest_mark_24")
        val markIdx = xml.indexOf("@drawable/ic_keynest_mark_24")
        val openIdx = xml.lastIndexOf("<ImageView", markIdx)
        val closeIdx = xml.indexOf("/>", markIdx)
        assertWithMessage("KN mark ImageView open tag must be located (Req 2.4)")
            .that(openIdx).isAtLeast(0)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("KN mark ImageView must declare android:tint=@color/kn_primary " +
            "(Req 2.5)")
            .that(block).contains("@color/kn_primary")
        assertWithMessage("KN mark ImageView must be 16dp wide (Req 2.4)")
            .that(block).contains("android:layout_width=\"16dp\"")
    }

    @Test
    fun layout_headerEyebrowDeclaresInlineTextAppearanceTokens() {
        // Req 2.7 / 2.8: 11sp / bold / 0.08 letterSpacing / textAllCaps /
        // kn_text. We assert these inline values exist in the layout for the
        // eyebrow text reference (autofill_dataset_brand_eyebrow).
        val xml = layoutXml()
        val refIdx = xml.indexOf("@string/autofill_dataset_brand_eyebrow")
        assertWithMessage("layout must reference @string/autofill_dataset_brand_eyebrow (Req 2.6)")
            .that(refIdx).isAtLeast(0)
        // Find the surrounding TextView.
        val openIdx = xml.lastIndexOf("<TextView", refIdx)
        val closeIdx = xml.indexOf("/>", refIdx)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("eyebrow TextView must declare textSize=11sp (Req 2.7)")
            .that(block).contains("android:textSize=\"11sp\"")
        assertWithMessage("eyebrow TextView must declare textAllCaps=true (Req 2.7)")
            .that(block).contains("android:textAllCaps=\"true\"")
        assertWithMessage("eyebrow TextView must declare letterSpacing >= 0.04 (Req 2.7)")
            .that(block).contains("android:letterSpacing=\"0.08\"")
        assertWithMessage("eyebrow TextView must declare textStyle=bold to mimic weight 700 " +
            "(Req 2.7)")
            .that(block).contains("android:textStyle=\"bold\"")
        assertWithMessage("eyebrow TextView textColor must reference @color/kn_text (Req 2.8)")
            .that(block).contains("@color/kn_text")
    }

    @Test
    fun layout_signatureChipUsesSuccessSoftPillAndShield() {
        // Req 2.10 / 2.11 / 2.12 / 2.13 / 2.14: kn_success_soft pill + shield
        // icon + signature_match + kn_success text.
        val xml = layoutXml()
        assertWithMessage("signature chip must reuse @drawable/kn_signature_chip_bg_success " +
            "(Req 2.10 / 2.14)")
            .that(xml).contains("@drawable/kn_signature_chip_bg_success")
        assertWithMessage("signature chip must reference @drawable/ic_shield_fill_16 (Req 2.12)")
            .that(xml).contains("@drawable/ic_shield_fill_16")
        assertWithMessage("signature chip must reference @string/signature_match (Req 2.13)")
            .that(xml).contains("@string/signature_match")
        // Locate the chip's TextView (the one whose text is signature_match).
        val refIdx = xml.indexOf("@string/signature_match")
        val openIdx = xml.lastIndexOf("<TextView", refIdx)
        val closeIdx = xml.indexOf("/>", refIdx)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("signature chip label textColor must reference @color/kn_success " +
            "(Req 2.11)")
            .that(block).contains("@color/kn_success")
    }

    // ---- Req 3.x: Dataset row ---------------------------------------------

    @Test
    fun layout_datasetRowDeclaresMinHeight48Dp() {
        // Req 3.3 / NFR 4.3: row minHeight >= 48dp to keep tap target.
        val xml = layoutXml()
        // The dataset row LinearLayout is the one that encloses @+id/dataset_label.
        val labelIdx = xml.indexOf("@+id/dataset_label")
        assertWithMessage("@+id/dataset_label must appear in the layout (Req 1.5)")
            .that(labelIdx).isAtLeast(0)
        // Scan backward from labelIdx to find the enclosing dataset row
        // LinearLayout opening tag. We walk outward up to 3 LinearLayout
        // ancestors to cover label column / dataset row / root container.
        var cursor = labelIdx
        var found48 = false
        repeat(3) {
            val openStart = xml.lastIndexOf("<LinearLayout", cursor)
            if (openStart < 0) return@repeat
            val openEnd = xml.indexOf(">", openStart)
            val block = xml.substring(openStart, openEnd + 1)
            if (block.contains("48dp")) {
                found48 = true
            }
            cursor = openStart - 1
        }
        assertWithMessage("an ancestor LinearLayout of dataset_label must declare " +
            "android:minHeight=\"48dp\" (Req 3.3 / NFR 4.3)")
            .that(found48).isTrue()
    }

    @Test
    fun layout_datasetRowIconTileUsesKnIconTileSmAndTileBg() {
        // Req 3.4 / 3.5 / 3.6: 32dp tile (kn_icon_tile_sm) with kn_icon_tile_bg.
        val xml = layoutFile.readText()
        assertWithMessage("dataset row icon tile must size to @dimen/kn_icon_tile_sm (Req 3.4)")
            .that(xml).contains("@dimen/kn_icon_tile_sm")
        assertWithMessage("dataset row icon tile must use @drawable/kn_icon_tile_bg (Req 3.6)")
            .that(xml).contains("@drawable/kn_icon_tile_bg")
    }

    @Test
    fun layout_datasetRowIconTileCenterReferencesKeyVector() {
        // Req 3.7: icon tile center renders a key vector (Architect chose
        // the static key path over dynamic letter to preserve Req 7.1).
        val xml = layoutFile.readText()
        assertWithMessage("icon tile center must reference @drawable/ic_key_24 (Req 3.7)")
            .that(xml).contains("@drawable/ic_key_24")
    }

    @Test
    fun layout_datasetLabelDeclaresInlineTextAppearanceTokens() {
        // Req 3.10 / 3.11 / 3.12: 13sp, kn_text textColor, ellipsize=end,
        // maxLines=1, bold (Text.KeyNest.BodyS の weight 500-700 範囲).
        val xml = layoutXml()
        val labelIdx = xml.indexOf("@+id/dataset_label")
        val openIdx = xml.lastIndexOf("<TextView", labelIdx)
        val closeIdx = xml.indexOf("/>", labelIdx)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("dataset_label TextView must declare textSize=13sp (Req 3.10)")
            .that(block).contains("android:textSize=\"13sp\"")
        assertWithMessage("dataset_label TextView textColor must reference @color/kn_text " +
            "(Req 3.11)")
            .that(block).contains("@color/kn_text")
        assertWithMessage("dataset_label TextView must declare maxLines=1 (Req 3.12)")
            .that(block).contains("android:maxLines=\"1\"")
        assertWithMessage("dataset_label TextView must declare ellipsize=end (Req 3.12)")
            .that(block).contains("android:ellipsize=\"end\"")
    }

    @Test
    fun layout_datasetSubtitleDeclaresInlineTextAppearanceTokens() {
        // Req 3.14 / 3.15 / 3.16: small textSize, kn_text_2 textColor,
        // ellipsize=end, maxLines=1.
        val xml = layoutXml()
        val labelIdx = xml.indexOf("@+id/dataset_subtitle")
        val openIdx = xml.lastIndexOf("<TextView", labelIdx)
        val closeIdx = xml.indexOf("/>", labelIdx)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("dataset_subtitle TextView textColor must reference @color/kn_text_2 " +
            "(Req 3.15)")
            .that(block).contains("@color/kn_text_2")
        assertWithMessage("dataset_subtitle TextView must declare maxLines=1 (Req 3.16)")
            .that(block).contains("android:maxLines=\"1\"")
        assertWithMessage("dataset_subtitle TextView must declare ellipsize=end (Req 3.16)")
            .that(block).contains("android:ellipsize=\"end\"")
    }

    @Test
    fun layout_datasetRowTrailingLockIconReferencesLockOutline16() {
        // Req 3.17 / 3.18 / 3.19 / 3.20: 16dp lock vector tinted kn_text_2.
        val xml = layoutXml()
        assertWithMessage("dataset row must reference @drawable/ic_lock_outline_16 (Req 3.20)")
            .that(xml).contains("@drawable/ic_lock_outline_16")
        val lockIdx = xml.indexOf("@drawable/ic_lock_outline_16")
        val openIdx = xml.lastIndexOf("<ImageView", lockIdx)
        val closeIdx = xml.indexOf("/>", lockIdx)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("lock ImageView must declare 16dp width (Req 3.19)")
            .that(block).contains("android:layout_width=\"16dp\"")
        assertWithMessage("lock ImageView tint must reference @color/kn_text_2 (Req 3.18)")
            .that(block).contains("@color/kn_text_2")
    }

    // ---- Req 5.x: footer "新規作成" row -----------------------------------

    @Test
    fun layout_footerRowUsesKnDatasetCreateRowBgDrawable() {
        // Req 5.2: kn_blue_50 fill is achieved via the dedicated drawable so
        // the dark-mode counterpart can swap to kn_primary_container.
        val xml = layoutXml()
        assertWithMessage("footer row must reference @drawable/kn_dataset_create_row_bg " +
            "(Req 5.2 / 6.5)")
            .that(xml).contains("@drawable/kn_dataset_create_row_bg")
    }

    @Test
    fun createRowBgLight_referencesKnBlue50Fill() {
        // Req 5.2: light drawable solid-fills with @color/kn_blue_50 (#EAF2FE).
        val xml = createRowBgLight.readText()
        assertWithMessage("kn_dataset_create_row_bg.xml (light) must reference " +
            "@color/kn_blue_50 (Req 5.2)")
            .that(xml).contains("@color/kn_blue_50")
    }

    @Test
    fun createRowBgNight_referencesKnPrimaryContainerForContrast() {
        // Req 6.5: dark variant swaps to kn_primary_container so kn_primary
        // text keeps a 4.5:1 contrast ratio.
        val xml = createRowBgNight.readText()
        assertWithMessage("kn_dataset_create_row_bg.xml (night) must reference " +
            "@color/kn_primary_container (Req 6.5)")
            .that(xml).contains("@color/kn_primary_container")
    }

    @Test
    fun layout_footerRowPlusIconTintsToPrimary() {
        // Req 5.4 / 5.5: plus icon 16dp tinted @color/kn_primary.
        val xml = layoutXml()
        assertWithMessage("footer row must reference @drawable/ic_plus_24 (Req 5.4)")
            .that(xml).contains("@drawable/ic_plus_24")
        val plusIdx = xml.indexOf("@drawable/ic_plus_24")
        val openIdx = xml.lastIndexOf("<ImageView", plusIdx)
        val closeIdx = xml.indexOf("/>", plusIdx)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("footer plus ImageView must declare 16dp width (Req 5.4)")
            .that(block).contains("android:layout_width=\"16dp\"")
        assertWithMessage("footer plus ImageView tint must reference @color/kn_primary " +
            "(Req 5.5)")
            .that(block).contains("@color/kn_primary")
    }

    @Test
    fun layout_footerRowLabelUsesPrimaryColorAndInlineTextTokens() {
        // Req 5.7 / 5.8: 13sp / weight 600 (bold) / kn_primary.
        val xml = layoutXml()
        val refIdx = xml.indexOf("@string/autofill_dataset_create_new")
        assertWithMessage("layout must reference @string/autofill_dataset_create_new (Req 5.9)")
            .that(refIdx).isAtLeast(0)
        val openIdx = xml.lastIndexOf("<TextView", refIdx)
        val closeIdx = xml.indexOf("/>", refIdx)
        val block = xml.substring(openIdx, closeIdx + 2)
        assertWithMessage("footer label TextView textColor must reference @color/kn_primary " +
            "(Req 5.8)")
            .that(block).contains("@color/kn_primary")
        assertWithMessage("footer label TextView must declare textSize=13sp (Req 5.7)")
            .that(block).contains("android:textSize=\"13sp\"")
        assertWithMessage("footer label TextView must declare textStyle=bold to mimic weight " +
            "500-700 (Req 5.7)")
            .that(block).contains("android:textStyle=\"bold\"")
    }

    // ---- Req 4.x: drawable resource integrity ------------------------------

    @Test
    fun lockIconDrawable_doesNotHardcodeHexColors() {
        // Req 4.5: fillColor references must be @color/kn_* or @android:color/*
        // (no raw #RRGGBB).
        val xml = lockIcon.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("ic_lock_outline_16.xml must not hardcode hex colors (Req 4.5)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    @Test
    fun createRowBgLight_doesNotHardcodeHexColors() {
        val xml = createRowBgLight.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("kn_dataset_create_row_bg.xml (light) must not hardcode hex " +
            "colors (Req 4.5)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    @Test
    fun createRowBgNight_doesNotHardcodeHexColors() {
        val xml = createRowBgNight.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("kn_dataset_create_row_bg.xml (night) must not hardcode hex " +
            "colors (Req 4.5)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    @Test
    fun headerBg_doesNotHardcodeHexColors() {
        val xml = headerBg.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("kn_dataset_header_bg.xml must not hardcode hex colors (Req 4.5)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    // ---- NFR 2.x: new strings in en + ja -----------------------------------

    @Test
    fun stringsResource_declaresAutofillDatasetCreateNewInBothLocales() {
        // NFR 2.1 / 5.10: "新規作成" 行のラベルは en + ja 双方で同一キー。
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        assertWithMessage("values/strings.xml must declare autofill_dataset_create_new " +
            "(NFR 2.1)")
            .that(en).contains("name=\"autofill_dataset_create_new\"")
        assertWithMessage("values-ja/strings.xml must declare autofill_dataset_create_new " +
            "(NFR 2.1 / Req 5.10)")
            .that(ja).contains("name=\"autofill_dataset_create_new\"")
    }

    @Test
    fun stringsResource_brandEyebrowIsTranslatableFalse() {
        // NFR 2.2: "KEYNEST" eyebrow はラテン大文字固定 (translatable=false).
        val en = stringsEn.readText()
        // The key declaration line must include translatable="false".
        val idx = en.indexOf("autofill_dataset_brand_eyebrow")
        assertWithMessage("values/strings.xml must declare autofill_dataset_brand_eyebrow " +
            "(NFR 2.2)")
            .that(idx).isAtLeast(0)
        val startTag = en.lastIndexOf("<string", idx)
        val endTag = en.indexOf(">", idx)
        val block = en.substring(startTag, endTag + 1)
        assertWithMessage("autofill_dataset_brand_eyebrow must be translatable=\"false\" " +
            "(NFR 2.2)")
            .that(block).contains("translatable=\"false\"")
    }

    @Test
    fun stringsResource_signatureMatchIsPreserved() {
        // NFR 2.3 / Req 2.13: 既存の signature_match キーは Issue #34 で
        // rename / delete しない。
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        assertWithMessage("values/strings.xml must keep name=signature_match (NFR 2.3)")
            .that(en).contains("name=\"signature_match\"")
        assertWithMessage("values-ja/strings.xml must keep name=signature_match (NFR 2.3)")
            .that(ja).contains("name=\"signature_match\"")
    }

    @Test
    fun stringsResource_declaresA11yLabelsInBothLocales() {
        // NFR 4.1: lock icon の contentDescription とブランドマークの label
        // を en + ja 双方に置く。
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        val keys = listOf(
            "autofill_dataset_brand_mark_a11y",
            "autofill_dataset_row_lock_a11y",
        )
        keys.forEach { key ->
            assertWithMessage("values/strings.xml must declare $key (NFR 4.1)")
                .that(en).contains("name=\"$key\"")
            assertWithMessage("values-ja/strings.xml must declare $key (NFR 4.1)")
                .that(ja).contains("name=\"$key\"")
        }
    }

    // ---- NFR 4.x: accessibility ---------------------------------------------

    @Test
    fun layout_decorativeIconsDeclareEmptyContentDescriptionOrNoImportant() {
        // NFR 4.1: shield / plus / center key icons are decorative.
        val xml = layoutXml()
        // shield (signature chip)
        val shieldIdx = xml.indexOf("@drawable/ic_shield_fill_16")
        val shieldOpen = xml.lastIndexOf("<ImageView", shieldIdx)
        val shieldClose = xml.indexOf("/>", shieldIdx)
        val shieldBlock = xml.substring(shieldOpen, shieldClose + 2)
        assertWithMessage("shield ImageView must declare " +
            "importantForAccessibility=no OR contentDescription=@null (NFR 4.1)")
            .that(
                shieldBlock.contains("importantForAccessibility=\"no\"") ||
                    shieldBlock.contains("android:contentDescription=\"@null\"")
            )
            .isTrue()
        // plus (footer row)
        val plusIdx = xml.indexOf("@drawable/ic_plus_24")
        val plusOpen = xml.lastIndexOf("<ImageView", plusIdx)
        val plusClose = xml.indexOf("/>", plusIdx)
        val plusBlock = xml.substring(plusOpen, plusClose + 2)
        assertWithMessage("plus ImageView must declare " +
            "importantForAccessibility=no OR contentDescription=@null (NFR 4.1)")
            .that(
                plusBlock.contains("importantForAccessibility=\"no\"") ||
                    plusBlock.contains("android:contentDescription=\"@null\"")
            )
            .isTrue()
        // center key (icon tile)
        val keyIdx = xml.indexOf("@drawable/ic_key_24")
        val keyOpen = xml.lastIndexOf("<ImageView", keyIdx)
        val keyClose = xml.indexOf("/>", keyIdx)
        val keyBlock = xml.substring(keyOpen, keyClose + 2)
        assertWithMessage("center key ImageView must declare " +
            "importantForAccessibility=no OR contentDescription=@null (NFR 4.1)")
            .that(
                keyBlock.contains("importantForAccessibility=\"no\"") ||
                    keyBlock.contains("android:contentDescription=\"@null\"")
            )
            .isTrue()
    }

    @Test
    fun layout_lockIconAdvertisesLockedContentDescription() {
        // NFR 4.1: lock icon は機能を伝える要素なので contentDescription
        // (autofill_dataset_row_lock_a11y) を指定する。
        val xml = layoutXml()
        val lockIdx = xml.indexOf("@drawable/ic_lock_outline_16")
        val lockOpen = xml.lastIndexOf("<ImageView", lockIdx)
        val lockClose = xml.indexOf("/>", lockIdx)
        val lockBlock = xml.substring(lockOpen, lockClose + 2)
        assertWithMessage("lock ImageView must reference " +
            "@string/autofill_dataset_row_lock_a11y as contentDescription (NFR 4.1)")
            .that(lockBlock).contains("@string/autofill_dataset_row_lock_a11y")
    }

    @Test
    fun layout_brandMarkAdvertisesKeyNestContentDescription() {
        // NFR 4.1: brand mark は KeyNest 提示元を伝えるため contentDescription
        // (autofill_dataset_brand_mark_a11y) を指定する。装飾扱いではない。
        val xml = layoutXml()
        val markIdx = xml.indexOf("@drawable/ic_keynest_mark_24")
        val markOpen = xml.lastIndexOf("<ImageView", markIdx)
        val markClose = xml.indexOf("/>", markIdx)
        val markBlock = xml.substring(markOpen, markClose + 2)
        assertWithMessage("brand mark ImageView must reference " +
            "@string/autofill_dataset_brand_mark_a11y as contentDescription (NFR 4.1)")
            .that(markBlock).contains("@string/autofill_dataset_brand_mark_a11y")
    }

    // ---- Sanity check / Req 1.4 structure ----------------------------------

    @Test
    fun layout_containsAtLeastThreeChildLinearLayouts() {
        // Req 1.4 + Req 5.1: root LinearLayout vertical contains header +
        // dataset row + footer row (3 children).
        val xml = layoutFile.readText()
        // Count <LinearLayout occurrences. Root + 3 immediate children +
        // 1 label column + 1 signature chip wrapper = 6 total.
        val count = Regex("<LinearLayout").findAll(xml).count()
        assertThat(count).isAtLeast(4)
    }
}
