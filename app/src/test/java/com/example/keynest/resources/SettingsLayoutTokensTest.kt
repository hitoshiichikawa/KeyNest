package com.example.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #33: source-level pinning of the design-token references on the
 * Settings screen layouts (`settings_activity.xml` + the destructive
 * `danger_zone_activity.xml` + the OSS rows `oss_licenses_item.xml`),
 * the supporting drawables, and the new settings string keys.
 *
 * Mirrors [PackagePickerLayoutTokensTest] (Issue #32) /
 * [OnboardingLayoutTokensTest] (Issue #31): we read the resource XML
 * directly so the assertions stay textual and don't require Robolectric
 * for resource inflation. The intent is to make sure a future careless
 * refactor cannot silently revert any of the JSX `ScreenSettings` visual
 * contract points (Autofill hero gradient / SettingGroup card / SettingRow
 * structure / Danger zone destructive group / 既存 View ID / 文字列キー).
 *
 * Mapped AC coverage (requirements.md):
 * - Req 1.x: root surface / horizontal padding / Toolbar title TextAppearance.
 * - Req 2.x: Autofill status hero (gradient drawable / chip / hero title +
 *   description + CTA / Text.KeyNest.* TextAppearance).
 * - Req 3.x: SettingGroup card (kn_settings_group_bg / Eyebrow header +
 *   accessibilityHeading).
 * - Req 4.x: SettingRow primitives (kn_settings_row_icon_tile_bg /
 *   chevron / divider drawable / Text.KeyNest.Body label /
 *   Text.KeyNest.Caption sub / 48dp minHeight).
 * - Req 5.x: Security group (lock status row, fingerprint icon, chevron,
 *   既存 ID btn_open_security_settings / text_lock_status).
 * - Req 6.x: Vault group (count / latest_updated / storage の 3 行, key /
 *   clock / database アイコン, 既存 ID text_vault_*).
 * - Req 7.x: About group (KeyNest mark / OSS licenses with chevron /
 *   Privacy row + sub, 既存 ID text_app_version / btn_oss_licenses).
 * - Req 8.x: Danger zone group (kn_settings_group_bg_danger / warning icon /
 *   kn_danger label / 既存 ID btn_open_danger_zone).
 * - Req 9.x: DangerZoneActivity (kn_settings_danger_card_bg /
 *   Widget.KeyNest.Button.Destructive / Widget.KeyNest.Button.Text).
 * - Req 10.x: OssLicensesActivity item (Widget.KeyNest.Card /
 *   Text.KeyNest.TitleS / Text.KeyNest.BodyS / Widget.KeyNest.Button.Text).
 * - Req 11.x: dark-mode token integrity (no #hex in layouts).
 * - Req 12.x: 既存 View ID 全 10 件保持.
 * - NFR 2.x: 48dp minHeight / accessibilityHeading / contentDescription.
 * - NFR 3.x: new strings present in en + ja resource tables.
 */
class SettingsLayoutTokensTest {

    private val settingsLayout: File =
        File("src/main/res/layout/settings_activity.xml")
    private val dangerZoneLayout: File =
        File("src/main/res/layout/danger_zone_activity.xml")
    private val ossItemLayout: File =
        File("src/main/res/layout/oss_licenses_item.xml")
    private val heroGradient: File =
        File("src/main/res/drawable/kn_settings_hero_gradient_enabled.xml")
    private val heroNotEnabled: File =
        File("src/main/res/drawable/kn_settings_hero_bg_notenabled.xml")
    private val heroChipEnabled: File =
        File("src/main/res/drawable/kn_settings_hero_chip_bg_enabled.xml")
    private val heroChipNotEnabled: File =
        File("src/main/res/drawable/kn_settings_hero_chip_bg_notenabled.xml")
    private val heroCtaEnabled: File =
        File("src/main/res/drawable/kn_settings_hero_cta_bg_enabled.xml")
    private val groupBg: File =
        File("src/main/res/drawable/kn_settings_group_bg.xml")
    private val groupBgDanger: File =
        File("src/main/res/drawable/kn_settings_group_bg_danger.xml")
    private val rowDivider: File =
        File("src/main/res/drawable/kn_settings_row_divider.xml")
    private val rowIconTileBg: File =
        File("src/main/res/drawable/kn_settings_row_icon_tile_bg.xml")
    private val dangerCardBg: File =
        File("src/main/res/drawable/kn_settings_danger_card_bg.xml")
    private val stringsEn: File =
        File("src/main/res/values/strings.xml")
    private val stringsJa: File =
        File("src/main/res/values-ja/strings.xml")

    // ---- Req 12.x: existing IDs preserved ----------------------------------

    @Test
    fun settingsLayout_preservesAllExistingViewIds() {
        // Req 12.4 (and Req 5.9 / 5.10 / 6.4 / 6.6 / 6.8 / 7.6 / 7.13 / 8.11
        // by inheritance): the 11 existing view ids that SettingsActivity.kt
        // binding paths reference must all remain.
        val xml = settingsLayout.readText()
        val ids = listOf(
            "@+id/toolbar",
            "@+id/btn_open_autofill_settings",
            "@+id/btn_open_security_settings",
            "@+id/btn_oss_licenses",
            "@+id/btn_open_danger_zone",
            "@+id/text_autofill_status",
            "@+id/text_lock_status",
            "@+id/text_vault_count",
            "@+id/text_vault_latest_updated",
            "@+id/text_vault_storage",
            "@+id/text_app_version",
        )
        ids.forEach { id ->
            assertWithMessage("settings_activity.xml must declare $id (Req 12.4)")
                .that(xml).contains(id)
        }
    }

    @Test
    fun dangerZoneLayout_preservesAllExistingViewIds() {
        // Req 9.6: btn_clear / btn_retry / group_progress / text_section_heading
        // / text_description / toolbar must remain.
        val xml = dangerZoneLayout.readText()
        val ids = listOf(
            "@+id/toolbar",
            "@+id/btn_clear",
            "@+id/btn_retry",
            "@+id/group_progress",
            "@+id/text_section_heading",
            "@+id/text_description",
        )
        ids.forEach { id ->
            assertWithMessage("danger_zone_activity.xml must declare $id (Req 9.6)")
                .that(xml).contains(id)
        }
    }

    @Test
    fun ossLicensesItem_preservesAllExistingViewIds() {
        // Req 10.5: text_name / text_license / btn_url / text_body must remain.
        val xml = ossItemLayout.readText()
        val ids = listOf(
            "@+id/text_name",
            "@+id/text_license",
            "@+id/btn_url",
            "@+id/text_body",
        )
        ids.forEach { id ->
            assertWithMessage("oss_licenses_item.xml must declare $id (Req 10.5)")
                .that(xml).contains(id)
        }
    }

    // ---- Req 1.x: root surface & horizontal padding ------------------------

    @Test
    fun settingsLayout_bindsRootBackgroundToColorBackgroundAttr() {
        // Req 1.1: ?android:attr/colorBackground resolves to kn_bg via
        // Theme.KeyNest android:colorBackground binding.
        val xml = settingsLayout.readText()
        assertWithMessage("settings_activity.xml root must resolve via " +
            "?android:attr/colorBackground (Req 1.1)")
            .that(xml).contains("?android:attr/colorBackground")
    }

    @Test
    fun settingsLayout_appliesScreenHorizontalPaddingToken() {
        // Req 1.2: kn_screen_padding_h (20dp).
        val xml = settingsLayout.readText()
        assertWithMessage("settings_activity.xml content area must use " +
            "@dimen/kn_screen_padding_h (Req 1.2)")
            .that(xml).contains("@dimen/kn_screen_padding_h")
    }

    @Test
    fun settingsLayout_preservesToolbar() {
        // Req 1.3: existing MaterialToolbar / settings_title is kept.
        val xml = settingsLayout.readText()
        assertWithMessage("toolbar must keep app:title=@string/settings_title (Req 1.3)")
            .that(xml).contains("@string/settings_title")
        assertWithMessage("toolbar must keep app:navigationContentDescription " +
            "= @string/settings_back_a11y (Req 1.3)")
            .that(xml).contains("@string/settings_back_a11y")
    }

    @Test
    fun settingsLayout_toolbarTitleUsesTitleSStyle() {
        // Req 1.4: Toolbar title TextAppearance = Text.KeyNest.TitleS.
        val xml = settingsLayout.readText()
        assertWithMessage("Toolbar app:titleTextAppearance must be " +
            "Text.KeyNest.TitleS (Req 1.4)")
            .that(xml).contains("@style/Text.KeyNest.TitleS")
    }

    // ---- Req 2.x: Autofill status hero --------------------------------------

    @Test
    fun settingsLayout_heroBackgroundReferencesGradientDrawable() {
        // Req 2.1 / 2.2 / 2.4: hero exists with kn_r_lg corners via the
        // gradient drawable.
        val xml = settingsLayout.readText()
        assertWithMessage("settings_activity.xml must reference " +
            "@drawable/kn_settings_hero_gradient_enabled (Req 2.4)")
            .that(xml).contains("@drawable/kn_settings_hero_gradient_enabled")
    }

    @Test
    fun heroGradientDrawable_usesPrimaryAndBlue700AndKnRLgCorners() {
        // Req 2.2: kn_r_lg (20dp) angled gradient.
        // Req 2.4: kn_primary -> kn_blue_700 endpoints.
        val xml = heroGradient.readText()
        assertWithMessage("hero gradient must reference @color/kn_primary (Req 2.4)")
            .that(xml).contains("@color/kn_primary")
        assertWithMessage("hero gradient must reference @color/kn_blue_700 (Req 2.4)")
            .that(xml).contains("@color/kn_blue_700")
        assertWithMessage("hero gradient must use @dimen/kn_r_lg corners (Req 2.2)")
            .that(xml).contains("@dimen/kn_r_lg")
        assertWithMessage("hero gradient must declare an angle attribute (Req 2.4 " +
            "linear-gradient(140deg ...) 相当)")
            .that(xml).contains("android:angle=")
    }

    @Test
    fun heroNotEnabledDrawable_usesWarningSoftAndKnRLgCorners() {
        // Req 2.7: kn_warning_soft fill, kn_r_lg corners.
        val xml = heroNotEnabled.readText()
        assertWithMessage("not-enabled hero bg must fill with @color/kn_warning_soft (Req 2.7)")
            .that(xml).contains("@color/kn_warning_soft")
        assertWithMessage("not-enabled hero bg must use @dimen/kn_r_lg corners (Req 2.2)")
            .that(xml).contains("@dimen/kn_r_lg")
    }

    @Test
    fun settingsLayout_heroAppliesCardPaddingLg() {
        // Req 2.3: @dimen/kn_card_padding_lg (18dp).
        val xml = settingsLayout.readText()
        // The hero LinearLayout has android:padding="@dimen/kn_card_padding_lg".
        assertWithMessage("hero must use @dimen/kn_card_padding_lg internal padding (Req 2.3)")
            .that(xml).contains("@dimen/kn_card_padding_lg")
    }

    @Test
    fun settingsLayout_heroHasStatusChipWithA11yLabel() {
        // Req 2.6 / 2.8 / NFR 2.5: chip_autofill_status references the
        // existing badge string and the composite a11y label
        // (settings_autofill_badge_a11y is set in Activity bind code, not
        // in XML, but the chip View ID must exist).
        val xml = settingsLayout.readText()
        assertWithMessage("hero must declare @+id/chip_autofill_status (Req 2.6)")
            .that(xml).contains("@+id/chip_autofill_status")
        assertWithMessage("status chip must reference settings_autofill_badge_enabled " +
            "as initial text (Req 2.8)")
            .that(xml).contains("@string/settings_autofill_badge_enabled")
    }

    @Test
    fun settingsLayout_heroHasTitleAndAccessibilityHeading() {
        // Req 2.5 / 2.9 / 2.14 / NFR 2.3: hero title TextView with
        // Text.KeyNest.TitleM + accessibilityHeading=true.
        val xml = settingsLayout.readText()
        val startIdx = xml.indexOf("@+id/text_autofill_hero_title")
        assertWithMessage("@+id/text_autofill_hero_title must appear (Req 2.5)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("/>", startIdx)
        val block = xml.substring(startIdx, endIdx + 2)
        assertWithMessage("hero title must use Text.KeyNest.TitleM (Req 2.9)")
            .that(block).contains("@style/Text.KeyNest.TitleM")
        assertWithMessage("hero title must declare accessibilityHeading=true (Req 2.14 / NFR 2.3)")
            .that(block).contains("android:accessibilityHeading=\"true\"")
        assertWithMessage("hero title must reference settings_autofill_hero_title (Req 2.5)")
            .that(block).contains("@string/settings_autofill_hero_title")
    }

    @Test
    fun settingsLayout_heroDescriptionUsesCaptionStyleAndKnTextAutofillStatusId() {
        // Req 2.5 / 2.10 / 12.4: the description TextView keeps the
        // existing text_autofill_status ID and uses Text.KeyNest.Caption.
        val xml = settingsLayout.readText()
        val startIdx = xml.indexOf("@+id/text_autofill_status")
        assertWithMessage("@+id/text_autofill_status must appear (Req 12.4 / 2.5)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("/>", startIdx)
        val block = xml.substring(startIdx, endIdx + 2)
        val usesCaption = block.contains("@style/Text.KeyNest.Caption") ||
            block.contains("@style/Text.KeyNest.BodyS")
        assertWithMessage("hero description must use Text.KeyNest.Caption or " +
            "Text.KeyNest.BodyS (Req 2.10)")
            .that(usesCaption).isTrue()
    }

    @Test
    fun settingsLayout_heroCtaIsBtnOpenAutofillSettingsWith48dpTouch() {
        // Req 2.11 / 2.13 / NFR 2.1: in-hero CTA keeps the
        // btn_open_autofill_settings ID and reserves 48dp tap target.
        val xml = settingsLayout.readText()
        val startIdx = xml.indexOf("@+id/btn_open_autofill_settings")
        assertWithMessage("@+id/btn_open_autofill_settings must appear (Req 2.11)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("/>", startIdx)
        val block = xml.substring(startIdx, endIdx + 2)
        assertWithMessage("hero CTA must keep 48dp minHeight (NFR 2.1)")
            .that(block).contains("48dp")
    }

    // ---- Req 3.x: SettingGroup container -----------------------------------

    @Test
    fun groupBgDrawable_usesKnSurfaceFillAndKnBorderStrokeAndKnRMd() {
        // Req 3.1 / 3.2 / 3.3.
        val xml = groupBg.readText()
        assertWithMessage("SettingGroup bg must fill with @color/kn_surface (Req 3.1)")
            .that(xml).contains("@color/kn_surface")
        assertWithMessage("SettingGroup bg must outline with @color/kn_border (Req 3.3)")
            .that(xml).contains("@color/kn_border")
        assertWithMessage("SettingGroup bg must use @dimen/kn_r_md corners (Req 3.2)")
            .that(xml).contains("@dimen/kn_r_md")
    }

    @Test
    fun settingsLayout_eyebrowHeadingsUseEyebrowStyleAndAccessibilityHeading() {
        // Req 3.4 / 3.5 / 3.6 / 3.9. Use a forward-window text slice instead
        // of a complex regex because Kotlin's `Regex` with `[^>]*?` + `\+`
        // is order-of-magnitude slower / brittle for multi-line XML blocks.
        val xml = settingsLayout.readText()
        val eyebrowIds = listOf(
            "@+id/eyebrow_security",
            "@+id/eyebrow_vault",
            "@+id/eyebrow_about",
            "@+id/eyebrow_danger",
        )
        eyebrowIds.forEach { id ->
            assertWithMessage("settings_activity.xml must declare $id (Req 3.4)")
                .that(xml).contains(id)
            val idIdx = xml.indexOf(id)
            assertWithMessage("$id must appear in settings_activity.xml (Req 3.4)")
                .that(idIdx).isAtLeast(0)
            // Find the closing `/>` after the id index.
            val endIdx = xml.indexOf("/>", idIdx)
            assertWithMessage("$id Eyebrow TextView must terminate with /> (Req 3.4)")
                .that(endIdx).isAtLeast(idIdx)
            val text = xml.substring(idIdx, endIdx + 2)
            assertWithMessage("$id must use Text.KeyNest.Eyebrow style (Req 3.5)")
                .that(text).contains("@style/Text.KeyNest.Eyebrow")
            assertWithMessage("$id must declare accessibilityHeading=true (Req 3.9 / NFR 2.3)")
                .that(text).contains("android:accessibilityHeading=\"true\"")
            assertWithMessage("$id text color must be @color/kn_text_3 (Req 3.6)")
                .that(text).contains("@color/kn_text_3")
        }
    }

    @Test
    fun settingsLayout_groupContainersReferenceGroupBgDrawables() {
        // Req 3.1 / 3.2 / 3.3 / 8.3: security / vault / about groups use
        // kn_settings_group_bg, danger group uses kn_settings_group_bg_danger.
        val xml = settingsLayout.readText()
        val standardCount = Regex("@drawable/kn_settings_group_bg(?!_)").findAll(xml).count()
        assertWithMessage("3 standard SettingGroup containers must reference " +
            "@drawable/kn_settings_group_bg (Req 3.1)")
            .that(standardCount).isAtLeast(3)
        assertWithMessage("Danger zone group must reference " +
            "@drawable/kn_settings_group_bg_danger (Req 8.3)")
            .that(xml).contains("@drawable/kn_settings_group_bg_danger")
    }

    // ---- Req 4.x: SettingRow primitives ------------------------------------

    @Test
    fun rowIconTileBgDrawable_usesKnSurface2AndKnRSmCorners() {
        // Req 4.2 / 4.3.
        val xml = rowIconTileBg.readText()
        assertWithMessage("row icon tile bg must fill with @color/kn_surface_2 (Req 4.3)")
            .that(xml).contains("@color/kn_surface_2")
        assertWithMessage("row icon tile bg must use @dimen/kn_r_sm corners (Req 4.2)")
            .that(xml).contains("@dimen/kn_r_sm")
    }

    @Test
    fun rowDividerDrawable_usesKnBorderColor() {
        // Req 4.17.
        val xml = rowDivider.readText()
        assertWithMessage("row divider must fill with @color/kn_border (Req 4.17)")
            .that(xml).contains("@color/kn_border")
    }

    @Test
    fun settingsLayout_rowsApplyIconTileSmAndDividerAndShowDividersMiddle() {
        // Req 4.1 / 4.17: icon tile uses kn_icon_tile_sm (32dp); the
        // standard SettingGroup LinearLayouts declare divider middle.
        val xml = settingsLayout.readText()
        assertWithMessage("SettingRow icon tile size must be @dimen/kn_icon_tile_sm (Req 4.1)")
            .that(xml).contains("@dimen/kn_icon_tile_sm")
        assertWithMessage("group LinearLayouts must reference " +
            "@drawable/kn_settings_row_divider (Req 4.17)")
            .that(xml).contains("@drawable/kn_settings_row_divider")
        assertWithMessage("group LinearLayouts must use showDividers=middle (Req 4.17 -- " +
            "no trailing divider on last row)")
            .that(xml).contains("android:showDividers=\"middle\"")
    }

    @Test
    fun settingsLayout_rowsReserveMinTouchSize48dp() {
        // Req 4.16 / NFR 2.1: each SettingRow keeps 48dp minimum tap target.
        val xml = settingsLayout.readText()
        // The 48dp value must appear multiple times (once per tappable row).
        // We assert >=4 occurrences (security / OSS / danger rows + others).
        val occurrences = Regex("48dp").findAll(xml).count()
        assertWithMessage("settings_activity.xml must reserve 48dp tap targets on multiple " +
            "rows (Req 4.16 / NFR 2.1)")
            .that(occurrences).isAtLeast(4)
    }

    @Test
    fun settingsLayout_chevronImageReferencesIcChevronRight24() {
        // Req 4.10 / 4.12: SettingRow に chevron が出現する.
        val xml = settingsLayout.readText()
        assertWithMessage("at least one SettingRow must reference " +
            "@drawable/ic_chevron_right_24 (Req 4.10 / 4.12)")
            .that(xml).contains("@drawable/ic_chevron_right_24")
    }

    // ---- Req 5.x: Security group --------------------------------------------

    @Test
    fun settingsLayout_lockStatusRowUsesFingerprintIconAndChevron() {
        // Req 5.6 / 5.7. Slice from btn_open_security_settings to the next
        // group (eyebrow_vault) so the chevron + icon both fall inside.
        val xml = settingsLayout.readText()
        val startIdx = xml.indexOf("@+id/btn_open_security_settings")
        assertWithMessage("@+id/btn_open_security_settings must appear (Req 5.9)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("@+id/eyebrow_vault", startIdx)
        assertWithMessage("security row block must precede the vault eyebrow")
            .that(endIdx).isAtLeast(startIdx)
        val text = xml.substring(startIdx, endIdx)
        assertWithMessage("security row leading icon must be ic_fingerprint_24 (Req 5.6)")
            .that(text).contains("@drawable/ic_fingerprint_24")
        assertWithMessage("security row right side must show chevron (Req 5.7)")
            .that(text).contains("@drawable/ic_chevron_right_24")
        assertWithMessage("security row sub text must use text_lock_status id (Req 5.10)")
            .that(text).contains("@+id/text_lock_status")
    }

    @Test
    fun settingsLayout_lockStatusRowLabelReferencesNewStringKey() {
        // Req 5.4 + NFR 3.1 / 3.4.
        val xml = settingsLayout.readText()
        assertWithMessage("lock status row label must reference settings_lock_status_label " +
            "(Req 5.4)")
            .that(xml).contains("@string/settings_lock_status_label")
    }

    // ---- Req 6.x: Vault group ----------------------------------------------

    @Test
    fun settingsLayout_vaultGroupHasThreeRowsWithExistingValueIds() {
        // Req 6.3 / 6.4 / 6.6 / 6.8 / 12.4.
        val xml = settingsLayout.readText()
        assertWithMessage("Vault group must declare @+id/group_vault")
            .that(xml).contains("@+id/group_vault")
        // The 3 right-value IDs.
        listOf(
            "@+id/text_vault_count",
            "@+id/text_vault_latest_updated",
            "@+id/text_vault_storage",
        ).forEach { id ->
            assertWithMessage("Vault group must wire $id (Req 6.4 / 6.6 / 6.8 / 12.4)")
                .that(xml).contains(id)
        }
    }

    @Test
    fun settingsLayout_vaultRowsUseExpectedIcons() {
        // Req 6.5 / 6.7 / 6.9: key / clock / storage icons.
        val xml = settingsLayout.readText()
        assertWithMessage("count row must use ic_key_24 (Req 6.5)")
            .that(xml).contains("@drawable/ic_key_24")
        assertWithMessage("latest_updated row must use ic_clock_24 (Req 6.7)")
            .that(xml).contains("@drawable/ic_clock_24")
        assertWithMessage("storage row must use ic_database_24 (Req 6.9)")
            .that(xml).contains("@drawable/ic_database_24")
    }

    // ---- Req 7.x: About group -----------------------------------------------

    @Test
    fun settingsLayout_aboutGroupKeepsTextAppVersionIdAndUsesKnMark() {
        // Req 7.5 / 7.6 / 7.7.
        val xml = settingsLayout.readText()
        assertWithMessage("about group must wire @+id/text_app_version (Req 7.6 / 12.4)")
            .that(xml).contains("@+id/text_app_version")
        assertWithMessage("KeyNest about row must use ic_keynest_mark_24 (Req 7.7)")
            .that(xml).contains("@drawable/ic_keynest_mark_24")
        assertWithMessage("KeyNest row label must reference settings_about_keynest_label " +
            "(Req 7.4)")
            .that(xml).contains("@string/settings_about_keynest_label")
    }

    @Test
    fun settingsLayout_aboutGroupOssRowHasChevronAndDocumentIcon() {
        // Req 7.9 / 7.10 / 7.11 / 7.13. Slice from btn_oss_licenses to the
        // next privacy row label so the chevron + icon are inside the slice.
        val xml = settingsLayout.readText()
        val startIdx = xml.indexOf("@+id/btn_oss_licenses")
        assertWithMessage("@+id/btn_oss_licenses must appear (Req 7.13)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("settings_about_privacy_label", startIdx)
        assertWithMessage("OSS row block must precede the privacy row label")
            .that(endIdx).isAtLeast(startIdx)
        val text = xml.substring(startIdx, endIdx)
        assertWithMessage("OSS row leading icon must be ic_description_24 (Req 7.10)")
            .that(text).contains("@drawable/ic_description_24")
        assertWithMessage("OSS row right side must show chevron (Req 7.11)")
            .that(text).contains("@drawable/ic_chevron_right_24")
        assertWithMessage("OSS row label must reference settings_about_oss_licenses_label (Req 7.9)")
            .that(text).contains("@string/settings_about_oss_licenses_label")
    }

    @Test
    fun settingsLayout_aboutGroupPrivacyRowHasShieldIcon() {
        // Req 7.14 / 7.15.
        val xml = settingsLayout.readText()
        assertWithMessage("about group must reference ic_shield_outline_24 (Req 7.15)")
            .that(xml).contains("@drawable/ic_shield_outline_24")
        assertWithMessage("privacy row label must reference settings_about_privacy_label (Req 7.14)")
            .that(xml).contains("@string/settings_about_privacy_label")
        assertWithMessage("privacy row sub must reference settings_about_privacy_sub (Req 7.14)")
            .that(xml).contains("@string/settings_about_privacy_sub")
    }

    // ---- Req 8.x: Danger zone group ----------------------------------------

    @Test
    fun groupBgDangerDrawable_usesKnDangerSoftAndKnRMd() {
        // Req 8.3 / 8.4.
        val xml = groupBgDanger.readText()
        assertWithMessage("danger group bg must fill with @color/kn_danger_soft (Req 8.3)")
            .that(xml).contains("@color/kn_danger_soft")
        assertWithMessage("danger group bg must use @dimen/kn_r_md corners (Req 8.4)")
            .that(xml).contains("@dimen/kn_r_md")
    }

    @Test
    fun settingsLayout_dangerRowUsesWarningIconAndKnDangerTint() {
        // Req 8.6 / 8.9. Slice from btn_open_danger_zone to end of file
        // since danger zone is the last group.
        val xml = settingsLayout.readText()
        val startIdx = xml.indexOf("@+id/btn_open_danger_zone")
        assertWithMessage("@+id/btn_open_danger_zone must appear (Req 8.11)")
            .that(startIdx).isAtLeast(0)
        val text = xml.substring(startIdx)
        assertWithMessage("danger row leading icon must be ic_warning_24 (Req 8.9)")
            .that(text).contains("@drawable/ic_warning_24")
        assertWithMessage("danger row tint / label color must reference @color/kn_danger " +
            "(Req 8.6 / Req 4.13)")
            .that(text).contains("@color/kn_danger")
        assertWithMessage("danger row label must reference settings_danger_delete_all_label " +
            "(Req 8.7)")
            .that(text).contains("@string/settings_danger_delete_all_label")
        assertWithMessage("danger row sub must reference settings_danger_delete_all_sub (Req 8.8)")
            .that(text).contains("@string/settings_danger_delete_all_sub")
    }

    // ---- Req 9.x: DangerZoneActivity ---------------------------------------

    @Test
    fun dangerCardBgDrawable_usesKnDangerSoftAndKnRMd() {
        // Req 9.1 / 9.2.
        val xml = dangerCardBg.readText()
        assertWithMessage("DangerZoneActivity card bg must fill with " +
            "@color/kn_danger_soft (Req 9.1)")
            .that(xml).contains("@color/kn_danger_soft")
        assertWithMessage("DangerZoneActivity card bg must use @dimen/kn_r_md (Req 9.2)")
            .that(xml).contains("@dimen/kn_r_md")
    }

    @Test
    fun dangerZoneLayout_routesThroughKnSettingsDangerCardBgAndKnDangerToken() {
        // Req 9.1 / 9.3: kn_danger_soft container background + kn_danger /
        // kn_text headings + ?attr/colorErrorContainer 撤去.
        val xml = dangerZoneLayout.readText()
        assertWithMessage("danger_zone_activity.xml must reference " +
            "@drawable/kn_settings_danger_card_bg (Req 9.1)")
            .that(xml).contains("@drawable/kn_settings_danger_card_bg")
        assertWithMessage("danger_zone_activity.xml must NOT keep ?attr/colorErrorContainer " +
            "(Req 9.1 / 9.3 -- replaced by kn_danger_soft semantic token)")
            .that(xml).doesNotContain("?attr/colorErrorContainer")
        assertWithMessage("danger_zone_activity.xml must NOT keep ?attr/colorOnErrorContainer " +
            "(Req 9.3)")
            .that(xml).doesNotContain("?attr/colorOnErrorContainer")
    }

    @Test
    fun dangerZoneLayout_btnClearUsesDestructiveStyle() {
        // Req 9.4: btn_clear -> @style/Widget.KeyNest.Button.Destructive.
        val xml = dangerZoneLayout.readText()
        val startIdx = xml.indexOf("@+id/btn_clear")
        assertWithMessage("@+id/btn_clear must appear (Req 9.4)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("/>", startIdx)
        val block = xml.substring(startIdx, endIdx + 2)
        assertWithMessage("btn_clear must use @style/Widget.KeyNest.Button.Destructive (Req 9.4)")
            .that(block).contains("@style/Widget.KeyNest.Button.Destructive")
    }

    @Test
    fun dangerZoneLayout_btnRetryUsesTextButtonStyle() {
        // Req 9.5: btn_retry -> @style/Widget.KeyNest.Button.Text + kn_danger.
        val xml = dangerZoneLayout.readText()
        val startIdx = xml.indexOf("@+id/btn_retry")
        assertWithMessage("@+id/btn_retry must appear (Req 9.5)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("/>", startIdx)
        val block = xml.substring(startIdx, endIdx + 2)
        assertWithMessage("btn_retry must use @style/Widget.KeyNest.Button.Text (Req 9.5)")
            .that(block).contains("@style/Widget.KeyNest.Button.Text")
        assertWithMessage("btn_retry text color must reference @color/kn_danger or kn_text_2 " +
            "(Req 9.5)")
            .that(block.contains("@color/kn_danger") || block.contains("@color/kn_text_2"))
            .isTrue()
    }

    // ---- Req 10.x: OssLicensesActivity item --------------------------------

    @Test
    fun ossLicensesItem_cardUsesKnCardStyle() {
        // Req 10.1: Widget.KeyNest.Card.
        val xml = ossItemLayout.readText()
        assertWithMessage("oss_licenses_item.xml must use @style/Widget.KeyNest.Card (Req 10.1)")
            .that(xml).contains("@style/Widget.KeyNest.Card")
    }

    @Test
    fun ossLicensesItem_namesAndBodiesUseKnTextAppearances() {
        // Req 10.2 / 10.3.
        val xml = ossItemLayout.readText()
        val nameStart = xml.indexOf("@+id/text_name")
        assertWithMessage("@+id/text_name must appear (Req 10.2)")
            .that(nameStart).isAtLeast(0)
        val nameEnd = xml.indexOf("/>", nameStart)
        val nameText = xml.substring(nameStart, nameEnd + 2)
        assertWithMessage("text_name TextAppearance must be Text.KeyNest.TitleS or " +
            "Text.KeyNest.Body (Req 10.2)")
            .that(nameText.contains("@style/Text.KeyNest.TitleS") ||
                nameText.contains("@style/Text.KeyNest.Body"))
            .isTrue()
        val licenseStart = xml.indexOf("@+id/text_license")
        assertWithMessage("@+id/text_license must appear (Req 10.3)")
            .that(licenseStart).isAtLeast(0)
        val licenseEnd = xml.indexOf("/>", licenseStart)
        val licenseText = xml.substring(licenseStart, licenseEnd + 2)
        assertWithMessage("text_license TextAppearance must be Text.KeyNest.BodyS or " +
            "Text.KeyNest.Caption (Req 10.3)")
            .that(licenseText.contains("@style/Text.KeyNest.BodyS") ||
                licenseText.contains("@style/Text.KeyNest.Caption"))
            .isTrue()
        assertWithMessage("text_license textColor must reference @color/kn_text_2 (Req 10.3)")
            .that(licenseText).contains("@color/kn_text_2")
    }

    @Test
    fun ossLicensesItem_btnUrlUsesKnTextButtonStyleAndPrimaryColor() {
        // Req 10.4.
        val xml = ossItemLayout.readText()
        val startIdx = xml.indexOf("@+id/btn_url")
        assertWithMessage("@+id/btn_url must appear (Req 10.4)")
            .that(startIdx).isAtLeast(0)
        val endIdx = xml.indexOf("/>", startIdx)
        val text = xml.substring(startIdx, endIdx + 2)
        assertWithMessage("btn_url must use @style/Widget.KeyNest.Button.Text (Req 10.4)")
            .that(text).contains("@style/Widget.KeyNest.Button.Text")
        assertWithMessage("btn_url textColor must reference @color/kn_primary (Req 10.4)")
            .that(text).contains("@color/kn_primary")
    }

    // ---- Req 11.x: dark mode token integrity --------------------------------

    @Test
    fun settingsLayout_doesNotHardcodeHexColorsInAttributes() {
        // Req 11.x: layout must reference kn_* semantic tokens / theme attrs,
        // not raw #RRGGBB. Drawables themselves may use translucent white
        // overlays for the hero CTA / chip but the layout must not.
        val xml = settingsLayout.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("settings_activity.xml must not hardcode hex colors " +
            "(use @color/kn_* per mapping.md §0 / Req 11.x)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    @Test
    fun dangerZoneLayout_doesNotHardcodeHexColorsInAttributes() {
        val xml = dangerZoneLayout.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("danger_zone_activity.xml must not hardcode hex colors (Req 11.x)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    @Test
    fun ossLicensesItem_doesNotHardcodeHexColorsInAttributes() {
        val xml = ossItemLayout.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("oss_licenses_item.xml must not hardcode hex colors (Req 11.x)")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }

    // ---- Req 12.x: existing string keys preserved --------------------------

    @Test
    fun stringsResource_preservesAllExistingSettingsStringKeys() {
        // Req 12.6: the 28 existing string keys must remain in en (the
        // canonical declaration table). ja translations are added as
        // separate scope (see Open Questions in requirements.md).
        val en = stringsEn.readText()
        val keys = listOf(
            "settings_title",
            "settings_back_a11y",
            "settings_section_autofill_title",
            "settings_autofill_badge_enabled",
            "settings_autofill_badge_not_enabled",
            "settings_autofill_badge_a11y",
            "settings_autofill_open_settings_action",
            "settings_section_security_title",
            "settings_lock_status_biometric_and_device",
            "settings_lock_status_device_only",
            "settings_lock_status_none",
            "settings_lock_status_update_required",
            "settings_lock_open_security_settings_action",
            "settings_section_vault_title",
            "settings_vault_count_label",
            "settings_vault_count_format",
            "settings_vault_latest_updated_label",
            "settings_vault_latest_updated_empty",
            "settings_vault_storage_label",
            "settings_section_about_title",
            "settings_about_version_label",
            "settings_about_version_format",
            "settings_about_oss_licenses_label",
            "settings_section_danger_title",
            "settings_danger_open_action",
            "settings_intent_unavailable",
        )
        keys.forEach { key ->
            assertWithMessage("values/strings.xml must preserve $key (Req 12.6)")
                .that(en).contains("name=\"$key\"")
        }
    }

    // ---- NFR 3.x: new keys in both locales ---------------------------------

    @Test
    fun stringsResource_containsNewSettingsKeysInBothLocales() {
        // NFR 3.1 / 3.4: Issue #33 で追加した文字列キーが en + ja 双方に
        // 存在することを確認する.
        val newKeys = listOf(
            "settings_autofill_hero_title",
            "settings_autofill_hero_description_enabled",
            "settings_autofill_hero_description_not_enabled",
            "settings_autofill_hero_cta",
            "settings_lock_status_label",
            "settings_lock_status_a11y_label",
            "settings_about_keynest_label",
            "settings_about_privacy_label",
            "settings_about_privacy_sub",
            "settings_about_oss_licenses_a11y_label",
            "settings_danger_delete_all_label",
            "settings_danger_delete_all_sub",
            "settings_danger_delete_all_a11y_label",
        )
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        newKeys.forEach { key ->
            assertWithMessage("values/strings.xml must declare $key (NFR 3.1)")
                .that(en).contains("name=\"$key\"")
            assertWithMessage("values-ja/strings.xml must declare $key (NFR 3.1 / 3.4)")
                .that(ja).contains("name=\"$key\"")
        }
    }

    // ---- Sanity check: drawables sound -------------------------------------

    @Test
    fun heroChipEnabledDrawable_usesPillCorners() {
        // Req 2.6: pill-shape (rounded) chip.
        val xml = heroChipEnabled.readText()
        assertWithMessage("hero chip (enabled) must use @dimen/kn_r_pill corners (Req 2.6)")
            .that(xml).contains("@dimen/kn_r_pill")
    }

    @Test
    fun heroChipNotEnabledDrawable_usesPillCorners() {
        // Req 2.7: pill-shape chip on warning_soft hero.
        val xml = heroChipNotEnabled.readText()
        assertWithMessage("hero chip (not_enabled) must use @dimen/kn_r_pill corners (Req 2.7)")
            .that(xml).contains("@dimen/kn_r_pill")
    }

    @Test
    fun heroCtaEnabledDrawable_hasStrokeForGlassEffect() {
        // Req 2.12: 1dp white-translucent stroke (JSX border: 1px solid
        // rgba(255,255,255,.30)).
        val xml = heroCtaEnabled.readText()
        assertWithMessage("hero CTA (enabled) must declare a <stroke> element (Req 2.12)")
            .that(xml).contains("<stroke")
    }
}
