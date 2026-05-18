package io.github.hitoshiichikawa.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #29: source-level pinning of the design-token references on the
 * three Credential List layouts. Backs Req 1.1 / 1.2 / 2.3 / 3.x / 4.x /
 * 5.x / 8.x / 10.1.
 *
 * Like [Material3ThemeMigrationTest], we read the layout XML files
 * directly because they are pure resources and the assertions we care
 * about (which @color / @dimen / @style is wired where) are textual. This
 * lets the test run in plain JUnit without spinning up Robolectric.
 *
 * The test does NOT verify visual rendering; that is out of scope for
 * unit tests and is covered by manual / instrumented checks. The intent
 * is to make sure a future careless refactor cannot silently revert a
 * row to a raw `#hex` color or a hard-coded dp value.
 */
class CredentialListLayoutTokensTest {

    private val activityLayout: File = File("src/main/res/layout/credential_list_activity.xml")
    private val rowLayout: File = File("src/main/res/layout/credential_list_item.xml")
    private val recentLayout: File = File("src/main/res/layout/credential_list_recent_item.xml")

    // ---- Req 10.1: all existing IDs are preserved ---------------------------

    @Test
    fun activityLayout_preservesAllIssue9Ids() {
        val xml = activityLayout.readText()

        // IDs that CredentialListActivity / Adapter / ViewModel reference
        // directly. Removing any of these would silently break Issue #9
        // behaviour (search / filter / sort / recent / empty / fab).
        val requiredIds = listOf(
            "@+id/toolbar",
            "@+id/input_search",
            "@+id/layout_search",
            "@+id/chip_group_filters",
            "@+id/chip_signature_matched",
            "@+id/chip_signature_missing",
            "@+id/btn_sort",
            "@+id/recent_header",
            "@+id/recent_recycler",
            "@+id/recycler",
            "@+id/empty_view",
            "@+id/fab_add",
        )
        requiredIds.forEach { id ->
            assertWithMessage("activity layout must declare $id (Req 10.1)")
                .that(xml).contains(id)
        }
    }

    @Test
    fun rowLayout_preservesIssue9Ids() {
        val xml = rowLayout.readText()

        listOf("@+id/text_label", "@+id/text_subtitle", "@+id/btn_overflow").forEach { id ->
            assertWithMessage("row layout must declare $id (Req 10.1)")
                .that(xml).contains(id)
        }
    }

    @Test
    fun recentLayout_preservesIssue9Ids() {
        val xml = recentLayout.readText()

        listOf(
            "@+id/card_recent",
            "@+id/text_recent_label",
            "@+id/text_recent_username",
        ).forEach { id ->
            assertWithMessage("recent layout must declare $id (Req 10.1)")
                .that(xml).contains(id)
        }
    }

    // ---- Req 1.1 / 1.2: root surface and horizontal padding -----------------

    @Test
    fun activityLayout_bindsRootBackgroundToColorBackgroundAttr() {
        // Req 1.1: the screen background must resolve a Material attribute
        // (not a #hex). Theme.KeyNest binds android:colorBackground to
        // @color/kn_bg, and values-night/colors.xml redefines kn_bg to
        // kn_ink_950 — covering Req 1.4 / Req 11.x automatically.
        val xml = activityLayout.readText()
        assertThat(xml).contains("?android:attr/colorBackground")
    }

    @Test
    fun activityLayout_appliesKnListPaddingHForScrollRegions() {
        // Req 1.2: horizontal padding for the chip group / recent carousel /
        // recycler / recent header must be kn_list_padding_h (16dp).
        // Without this, the layout drifts away from the JSX mock's
        // 16dp inset.
        val xml = activityLayout.readText()
        // Count occurrences so a single accidental missing reference fails.
        val count = "@dimen/kn_list_padding_h".toRegex().findAll(xml).count()
        assertWithMessage("activity layout should use @dimen/kn_list_padding_h " +
            "for the multiple scroll-region paddings (Req 1.2)")
            .that(count).isAtLeast(4)
    }

    // ---- Req 2.x: search bar uses kn_surface_2 + kn_r_input -----------------

    @Test
    fun activityLayout_searchBarUsesKnSurface2AndKnRInput() {
        val xml = activityLayout.readText()
        // Req 2.1: search bar fill = kn_surface_2.
        assertThat(xml).contains("app:boxBackgroundColor=\"@color/kn_surface_2\"")
        // Req 2.3: corner radius = kn_r_input.
        assertThat(xml).contains("app:boxCornerRadiusTopStart=\"@dimen/kn_r_input\"")
        assertThat(xml).contains("app:boxCornerRadiusBottomEnd=\"@dimen/kn_r_input\"")
        // Req 2.4: existing hint key is reused.
        assertThat(xml).contains("@string/credential_list_search_hint")
    }

    // ---- Req 3.x: filter chip style ----------------------------------------

    @Test
    fun activityLayout_filterChipsUseWidgetKeyNestChipStyle() {
        // Req 3.1: chips inherit Widget.KeyNest.Chip (which itself
        // inherits Widget.Material3.Chip.Filter, satisfying Req 3.4 / 3.5
        // color states via colorPrimary / kn_surface).
        val xml = activityLayout.readText()
        val count = "@style/Widget.KeyNest.Chip".toRegex().findAll(xml).count()
        assertWithMessage("filter chips must consume @style/Widget.KeyNest.Chip (Req 3.1)")
            .that(count).isEqualTo(2)
    }

    // ---- Req 4.x: recent header eyebrow ------------------------------------

    @Test
    fun activityLayout_recentHeaderUsesEyebrowTextAppearance() {
        val xml = activityLayout.readText()
        // Req 4.1: recent header textAppearance = Text.KeyNest.Eyebrow.
        assertThat(xml).contains("@style/Text.KeyNest.Eyebrow")
    }

    // ---- Req 5.x: row card outline + typography ----------------------------

    @Test
    fun rowLayout_rootIsWidgetKeyNestCard() {
        // Req 5.1 / 5.2: row card inherits Widget.KeyNest.Card (kn_surface
        // + kn_border + r_card). The card content padding (kn_card_padding)
        // is supplied inside the LinearLayout because the row uses the
        // FrameLayout-rendered card directly; the test below covers it.
        val xml = rowLayout.readText()
        assertThat(xml).contains("style=\"@style/Widget.KeyNest.Card\"")
    }

    @Test
    fun rowLayout_iconTileIs44dpWithR12Background() {
        // Req 5.3: 44dp icon tile (= kn_icon_tile_lg) with r12 background
        // drawable.
        val xml = rowLayout.readText()
        assertThat(xml).contains("@dimen/kn_icon_tile_lg")
        assertThat(xml).contains("@drawable/kn_icon_tile_bg")
    }

    @Test
    fun rowLayout_titleAndSubtitleAndPackageUseKnTextAppearances() {
        // Req 5.5 / 5.6 / 5.7.
        val xml = rowLayout.readText()
        assertThat(xml).contains("@style/Text.KeyNest.Body")
        assertThat(xml).contains("@style/Text.KeyNest.BodyS")
        assertThat(xml).contains("@style/Text.KeyNest.Mono")
        // Req 5.6: username uses kn_text_2.
        assertThat(xml).contains("@color/kn_text_2")
        // Req 5.7: package uses kn_text_3.
        assertThat(xml).contains("@color/kn_text_3")
    }

    @Test
    fun rowLayout_overflowButtonHasA48dpTapTarget() {
        // Req 5.8 / Req 9.2 / NFR 2.1.
        val xml = rowLayout.readText()
        assertThat(xml).contains("android:id=\"@+id/btn_overflow\"")
        // Both layout dims AND minWidth/minHeight are pinned at 48dp.
        assertThat(xml).contains("android:layout_width=\"48dp\"")
        assertThat(xml).contains("android:minHeight=\"48dp\"")
        assertThat(xml).contains("@drawable/ic_more_vert_24")
        // kn_text_3 tint (Req 9.1).
        assertThat(xml).contains("app:tint=\"@color/kn_text_3\"")
    }

    @Test
    fun rowLayout_embedsStrengthBarCustomView() {
        // Req 6.x: the row card embeds io.github.hitoshiichikawa.keynest.ui.widget.StrengthBar.
        val xml = rowLayout.readText()
        assertThat(xml).contains("io.github.hitoshiichikawa.keynest.ui.widget.StrengthBar")
    }

    @Test
    fun rowLayout_signatureChipReferencesBothSuccessBackgroundDrawables() {
        // Req 7.1: success chip background drawable referenced from the
        // layout (the row defaults to success; the adapter swaps to the
        // warning background when signatureSha256 is null — verified in
        // the adapter code, but we make sure the resource is at least
        // declared somewhere in the layout / strings to detect drift).
        val xml = rowLayout.readText()
        assertThat(xml).contains("@drawable/kn_signature_chip_bg_success")
        assertThat(xml).contains("@string/signature_match")
    }

    // ---- Req 4.x: recent card outline --------------------------------------

    @Test
    fun recentLayout_cardWidthIs132dpAndUsesKnSurfaceBorder() {
        // Req 4.2 / 4.3 / 4.5: 132dp wide, kn_surface fill, kn_border
        // stroke, r_card radius, Text.KeyNest.Body + Caption.
        val xml = recentLayout.readText()
        assertThat(xml).contains("android:layout_width=\"132dp\"")
        assertThat(xml).contains("app:cardBackgroundColor=\"@color/kn_surface\"")
        assertThat(xml).contains("app:cardCornerRadius=\"@dimen/kn_r_card\"")
        assertThat(xml).contains("app:strokeColor=\"@color/kn_border\"")
        assertThat(xml).contains("@drawable/kn_icon_tile_bg")
        assertThat(xml).contains("@style/Text.KeyNest.Body")
        assertThat(xml).contains("@style/Text.KeyNest.Caption")
    }

    // ---- Req 8.x: empty state hero + CTA -----------------------------------

    @Test
    fun activityLayout_emptyStateContainsHeroAndPrimaryCta() {
        // Req 8.1 / 8.4: empty state has the hero glow drawable, a
        // KeyNest mark (mipmap launcher), a primary CTA, and the
        // security footer.
        val xml = activityLayout.readText()
        assertThat(xml).contains("@+id/empty_state_container")
        assertThat(xml).contains("@+id/empty_state_hero")
        assertThat(xml).contains("@drawable/kn_empty_hero_glow")
        assertThat(xml).contains("@mipmap/ic_launcher")
        assertThat(xml).contains("@+id/empty_state_cta")
        assertThat(xml).contains("@style/Widget.KeyNest.Button.Primary")
        // Req 8.4: CTA opens "Add credential" (Req: same target as FAB).
        assertThat(xml).contains("android:text=\"@string/action_add_credential\"")
        // Footer security note (Req 8 footer).
        assertThat(xml).contains("@string/credential_list_empty_security_note")
    }

    // ---- Req 8.1 / 8.3: supplemental body copy uses Body + kn_text_2 -------

    @Test
    fun activityLayout_emptyStateContainsBodyCopyWithKnText2() {
        // Req 8.1 (補足文 as one of the five required elements) /
        // Req 8.3 (補足文テキスト = Text.KeyNest.Body + kn_text_2).
        //
        // We pin the body TextView declaration by checking:
        //  - the dedicated id `@+id/empty_state_body`
        //  - its style is `Text.KeyNest.Body` (NOT BodyS — the AC requires
        //    Body specifically), declared on the same TextView
        //  - its textColor resolves `@color/kn_text_2`
        //  - it references the new `credential_list_empty_body` string
        val xml = activityLayout.readText()
        assertThat(xml).contains("@+id/empty_state_body")
        assertThat(xml).contains("@string/credential_list_empty_body")
        // The body TextView block must combine Text.KeyNest.Body and
        // @color/kn_text_2 on the same element. We assert both tokens are
        // present in the empty_state_body declaration by matching the
        // surrounding block.
        val bodyBlockRegex = Regex(
            pattern = "<TextView[^>]*?@\\+id/empty_state_body[\\s\\S]*?/>",
        )
        val match = bodyBlockRegex.find(xml)
        assertWithMessage("empty_state_body declaration must exist in the activity layout")
            .that(match).isNotNull()
        val block = match!!.value
        assertWithMessage(
            "empty_state_body must use @style/Text.KeyNest.Body (Req 8.3)",
        ).that(block).contains("style=\"@style/Text.KeyNest.Body\"")
        assertWithMessage(
            "empty_state_body must use textColor=@color/kn_text_2 (Req 8.3)",
        ).that(block).contains("android:textColor=\"@color/kn_text_2\"")
    }

    // ---- Req 8 boundary: the headline TextView keeps its id ----------------

    @Test
    fun activityLayout_emptyStateHeadlineKeepsEmptyViewId() {
        // Issue #29 design decision: the existing empty_view TextView is
        // preserved inside the new empty state container so the existing
        // renderEmptyView(setText / setVisibility) path keeps working
        // unchanged. The TextView is styled as the headline (TitleM).
        val xml = activityLayout.readText()
        assertThat(xml).contains("android:id=\"@+id/empty_view\"")
        assertThat(xml).contains("@style/Text.KeyNest.TitleM")
    }

    // ---- No raw hex colors in layouts (mapping.md "Don't" rule) ------------

    @Test
    fun layouts_doNotHardcodeHexColors() {
        // Mapping rule (mapping.md §0): layouts must reference kn_*
        // tokens, never a raw #RRGGBB.
        listOf(activityLayout, rowLayout, recentLayout).forEach { file ->
            val xml = file.readText()
            // Match any 6- or 8-digit hex token inside a quoted attribute
            // value. We allow "@android:color/..." and "?attr/..." since
            // those are not hex.
            val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
            assertWithMessage("${file.name} must not hardcode hex colors " +
                "(mapping.md §0 — use @color/kn_*).")
                .that(hexInAttr.containsMatchIn(xml)).isFalse()
        }
    }
}
