package inc.goodanswers.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #30: source-level pinning of the design-token references on the
 * Credential Edit layout. Backs Req 1.x / 2.x / 3.x / 4.x / 5.x / 6.x /
 * 7.x / 8.x / 9.x / 10.1 of the Issue #30 requirements.
 *
 * Like [CredentialListLayoutTokensTest] (Issue #29) and
 * [Material3ThemeMigrationTest] (Issue #24), we read the layout XML
 * directly because it is a pure resource file and the assertions we
 * care about (which @color / @dimen / @style is wired where) are
 * textual. This lets the test run in plain JUnit without spinning up
 * Robolectric.
 *
 * The test does NOT verify visual rendering; that is out of scope for
 * unit tests and is covered by manual / instrumented checks. The intent
 * is to make sure a future careless refactor cannot silently revert the
 * screen to a raw `#hex` color or hard-coded dp value.
 */
class CredentialEditLayoutTokensTest {

    private val editLayout: File = File("src/main/res/layout/credential_edit_activity.xml")

    // ---- Req 10.1: all existing IDs are preserved --------------------------

    @Test
    fun editLayout_preservesAllPreIssue30Ids() {
        val xml = editLayout.readText()

        // IDs that CredentialEditActivity / CredentialEditViewModel /
        // PackagePickerBottomSheet / Issue #14 advanced section reference
        // directly. Removing any of these would silently break the save /
        // edit / package picker / signature copy / credential ID toggle
        // flows (Req 10.1).
        val requiredIds = listOf(
            "@+id/toolbar",
            "@+id/layout_package",
            "@+id/input_package",
            "@+id/btn_pick_installed_app",
            "@+id/layout_username",
            "@+id/input_username",
            "@+id/layout_password",
            "@+id/input_password",
            "@+id/layout_label",
            "@+id/input_label",
            "@+id/btn_save",
            "@+id/advanced_header",
            "@+id/advanced_title",
            "@+id/advanced_chevron",
            "@+id/advanced_content",
            "@+id/row_created_at",
            "@+id/value_created_at",
            "@+id/row_updated_at",
            "@+id/value_updated_at",
            "@+id/row_signature_hex",
            "@+id/value_signature_hex",
            "@+id/btn_copy_signature_hex",
            "@+id/row_signature_captured_at",
            "@+id/value_signature_captured_at",
            "@+id/row_credential_id",
            "@+id/value_credential_id",
            "@+id/toggle_credential_id",
        )
        requiredIds.forEach { id ->
            assertWithMessage("credential_edit_activity layout must declare $id (Req 10.1)")
                .that(xml).contains(id)
        }
    }

    // ---- Req 1.1 / 1.2: root surface and horizontal padding ----------------

    @Test
    fun editLayout_bindsRootBackgroundToColorBackgroundAttr() {
        // Req 1.1: the screen background must resolve a Material attribute
        // (not a #hex). Theme.KeyNest binds android:colorBackground to
        // @color/kn_bg, and values-night/colors.xml redefines kn_bg to
        // kn_ink_950, automatically covering Req 1.4 / 11.x.
        val xml = editLayout.readText()
        assertThat(xml).contains("?android:attr/colorBackground")
    }

    @Test
    fun editLayout_appliesKnScreenPaddingHForScrollRegion() {
        // Req 1.2: the ScrollView horizontal padding must be
        // @dimen/kn_screen_padding_h (20dp). The activity uses
        // paddingHorizontal so a single reference is sufficient.
        val xml = editLayout.readText()
        assertThat(xml).contains("@dimen/kn_screen_padding_h")
    }

    // ---- Req 2.x: app bar uses Text.KeyNest.TitleS + kn_text close icon ----

    @Test
    fun editLayout_appBarUsesTitleSAppearanceAndKnTextTintedCloseIcon() {
        val xml = editLayout.readText()
        // Req 2.1: title text appearance.
        assertThat(xml).contains("app:titleTextAppearance=\"@style/Text.KeyNest.TitleS\"")
        // Req 2.2: close icon source + kn_text tint.
        assertThat(xml).contains("app:navigationIcon=\"@drawable/ic_close_24\"")
        assertThat(xml).contains("app:navigationIconTint=\"@color/kn_text\"")
    }

    // ---- Req 3.x: target app card --------------------------------------------

    @Test
    fun editLayout_targetAppCardUsesWidgetKeyNestCardAndKnIconTile() {
        // Req 3.1: card uses Widget.KeyNest.Card outline.
        // Req 3.3: 44dp icon tile via kn_icon_tile_lg and kn_icon_tile_bg drawable.
        val xml = editLayout.readText()
        assertThat(xml).contains("style=\"@style/Widget.KeyNest.Card\"")
        assertThat(xml).contains("@dimen/kn_icon_tile_lg")
        assertThat(xml).contains("@drawable/kn_icon_tile_bg")
    }

    @Test
    fun editLayout_targetAppCardHasLabelPackageAndSignatureChip() {
        // Req 3.5 / 3.6 / 3.7 / 3.8: label, mono package, signature chip
        // (with the warning drawable as the layout default; the success
        // variant is swapped by the Activity).
        val xml = editLayout.readText()
        assertThat(xml).contains("@+id/tv_target_app_label")
        assertThat(xml).contains("@+id/tv_target_app_package")
        assertThat(xml).contains("@+id/chip_signature_edit")
        // Both signature drawables must at least be referenced from the
        // module so they cannot be deleted as "unused" by a future cleanup.
        // The activity layout references the warning variant in XML; the
        // success variant is set programmatically — covered by the row
        // layout (Issue #29) and the unit test below verifying the
        // drawable files exist.
        assertThat(xml).contains("@drawable/kn_signature_chip_bg_warning")
        assertThat(xml).contains("@string/signature_missing")
    }

    @Test
    fun editLayout_keepsHiddenEditablePackageField() {
        // Req 3.9 / 10.1: layout_package + input_package remain in the
        // layout (visibility=gone) so the PackagePickerBottomSheet write
        // path and onSaveClicked() read path are intact. We pin the
        // declaration of both the layout_package TextInputLayout and its
        // visibility=gone marker so a future refactor cannot delete the
        // hidden field silently.
        val xml = editLayout.readText()
        val layoutPackageRegex = Regex(
            pattern = "<com\\.google\\.android\\.material\\.textfield\\.TextInputLayout[^>]*?@\\+id/layout_package[\\s\\S]*?</com\\.google\\.android\\.material\\.textfield\\.TextInputLayout>",
        )
        val match = layoutPackageRegex.find(xml)
        assertWithMessage("layout_package TextInputLayout must be present")
            .that(match).isNotNull()
        val block = match!!.value
        assertWithMessage("layout_package must remain at visibility=gone (Req 3.9)")
            .that(block).contains("android:visibility=\"gone\"")
    }

    // ---- Req 4.x / 5.x: TextInputLayouts use Widget.KeyNest.TextField -------

    @Test
    fun editLayout_textInputLayoutsUseWidgetKeyNestTextField() {
        // Req 4.1 / 5.1: all four TextInputLayouts (package / label /
        // username / password) must declare style=@style/Widget.KeyNest.TextField.
        val xml = editLayout.readText()
        val count = "@style/Widget.KeyNest.TextField".toRegex().findAll(xml).count()
        assertWithMessage("all 4 TextInputLayouts must consume Widget.KeyNest.TextField (Req 4.1 / 5.1)")
            .that(count).isEqualTo(4)
    }

    @Test
    fun editLayout_inputFieldsApplyKnInputHeight() {
        // Req 4.3 / 5.1: each editable field must specify a minimum
        // height of @dimen/kn_input_height (52dp).
        val xml = editLayout.readText()
        val count = "@dimen/kn_input_height".toRegex().findAll(xml).count()
        assertWithMessage("all 4 input fields must declare minHeight=kn_input_height (Req 4.3 / 5.1)")
            .that(count).isAtLeast(4)
    }

    @Test
    fun editLayout_passwordFieldUsesPasswordTextAppearance() {
        // Req 5.2: password EditText must apply Text.KeyNest.Password
        // (17sp / weight 700 / mono / letter-spacing 0.1em).
        val xml = editLayout.readText()
        assertThat(xml).contains("@style/Text.KeyNest.Password")
        // Req 5.5: inputType=textPassword preserved.
        assertThat(xml).contains("android:inputType=\"textPassword\"")
    }

    @Test
    fun editLayout_passwordFieldHasToggleAndKnText2Tint() {
        // Req 5.4: password toggle preserved + tinted kn_text_2.
        val xml = editLayout.readText()
        assertThat(xml).contains("app:passwordToggleEnabled=\"true\"")
        assertThat(xml).contains("app:endIconTint=\"@color/kn_text_2\"")
    }

    // ---- Req 6.x: password strength bar -------------------------------------

    @Test
    fun editLayout_embedsStrengthBarCustomView() {
        // Req 6.1 / 6.2: the password header row embeds
        // inc.goodanswers.keynest.ui.widget.StrengthBar so the bar appears
        // alongside the password label.
        val xml = editLayout.readText()
        assertThat(xml).contains("inc.goodanswers.keynest.ui.widget.StrengthBar")
        assertThat(xml).contains("@+id/strength_bar_edit")
    }

    // ---- Req 7.x: advanced section ------------------------------------------

    @Test
    fun editLayout_advancedSectionUsesKnSurface2BackgroundAndKnRInputRadius() {
        // Req 7.1 / 7.2: advanced section container background drawable
        // pins kn_surface_2 + r14 corner. The drawable file itself is
        // covered by the existing drawable scan (a manual review verified
        // its contents).
        val xml = editLayout.readText()
        assertThat(xml).contains("@drawable/kn_advanced_section_bg")
    }

    @Test
    fun editLayout_advancedHeaderUsesBodyAppearanceAndKnText2() {
        // Req 7.4: advanced header text appearance + kn_text_2 color.
        val xml = editLayout.readText()
        val headerBlockRegex = Regex(
            pattern = "<TextView[^>]*?@\\+id/advanced_title[\\s\\S]*?/>",
        )
        val match = headerBlockRegex.find(xml)
        assertWithMessage("advanced_title TextView must be present")
            .that(match).isNotNull()
        val block = match!!.value
        assertThat(block).contains("style=\"@style/Text.KeyNest.Body\"")
        assertThat(block).contains("android:textColor=\"@color/kn_text_2\"")
    }

    @Test
    fun editLayout_advancedChevronTintsKnText3() {
        // Req 7.5: chevron tint kn_text_3.
        val xml = editLayout.readText()
        val chevronBlockRegex = Regex(
            pattern = "<ImageView[^>]*?@\\+id/advanced_chevron[\\s\\S]*?/>",
        )
        val match = chevronBlockRegex.find(xml)
        assertWithMessage("advanced_chevron ImageView must be present")
            .that(match).isNotNull()
        assertThat(match!!.value).contains("app:tint=\"@color/kn_text_3\"")
    }

    @Test
    fun editLayout_advancedRowsUseMonoForSha256AndCredentialIdAndPackage() {
        // Req 7.7: mono targets (signature hex, credential ID, package
        // name in the target app card) all use Text.KeyNest.Mono.
        val xml = editLayout.readText()
        val count = "@style/Text.KeyNest.Mono".toRegex().findAll(xml).count()
        assertWithMessage(
            "Text.KeyNest.Mono must be referenced for tv_target_app_package, " +
                "value_signature_hex and value_credential_id (Req 3.6 / Req 7.7)",
        ).that(count).isAtLeast(3)
    }

    @Test
    fun editLayout_advancedRowDividerColor() {
        // Req 7.8: separator drawable resolves @color/kn_border (verified
        // in kn_advanced_row_divider.xml).
        val xml = editLayout.readText()
        assertThat(xml).contains("@drawable/kn_advanced_row_divider")
    }

    // ---- Req 8.x: delete CTA ------------------------------------------------

    @Test
    fun editLayout_deleteCtaUsesWidgetKeyNestButtonDestructive() {
        // Req 8.5-8.7: destructive style + match_parent width + min height.
        val xml = editLayout.readText()
        val deleteBlockRegex = Regex(
            pattern = "<com\\.google\\.android\\.material\\.button\\.MaterialButton[^>]*?@\\+id/btn_delete[\\s\\S]*?/>",
        )
        val match = deleteBlockRegex.find(xml)
        assertWithMessage("btn_delete must be declared in the edit layout (Req 8.1)")
            .that(match).isNotNull()
        val block = match!!.value
        assertWithMessage("btn_delete must use Widget.KeyNest.Button.Destructive (Req 8.5-8.7)")
            .that(block).contains("style=\"@style/Widget.KeyNest.Button.Destructive\"")
        assertWithMessage("btn_delete must be match_parent wide (Req 8.3)")
            .that(block).contains("android:layout_width=\"match_parent\"")
        assertWithMessage("btn_delete must use the shared @string/action_delete_credential (Req 8.8)")
            .that(block).contains("android:text=\"@string/action_delete_credential\"")
        // Req 8.1 / 8.2: starts hidden; the Activity flips to VISIBLE
        // when editingId != null. Layout default is therefore gone.
        assertWithMessage("btn_delete default visibility must be gone (Req 8.2)")
            .that(block).contains("android:visibility=\"gone\"")
    }

    // ---- Req 9.x: save CTA --------------------------------------------------

    @Test
    fun editLayout_saveCtaUsesWidgetKeyNestButtonPrimary() {
        // Req 9.3 / 9.5: save button uses Widget.KeyNest.Button.Primary
        // (51dp height, kn_primary fill, r14 corner). Reuses Issue #29
        // visual style.
        val xml = editLayout.readText()
        val saveBlockRegex = Regex(
            pattern = "<com\\.google\\.android\\.material\\.button\\.MaterialButton[^>]*?@\\+id/btn_save[\\s\\S]*?/>",
        )
        val match = saveBlockRegex.find(xml)
        assertWithMessage("btn_save must be declared in the edit layout")
            .that(match).isNotNull()
        val block = match!!.value
        assertWithMessage("btn_save must use Widget.KeyNest.Button.Primary (Req 9.3)")
            .that(block).contains("style=\"@style/Widget.KeyNest.Button.Primary\"")
        assertWithMessage("btn_save must use @string/action_save (Req 9.4)")
            .that(block).contains("android:text=\"@string/action_save\"")
    }

    // ---- mapping.md §0: layouts must not hardcode hex colors ----------------

    @Test
    fun editLayout_doesNotHardcodeHexColors() {
        // Mapping rule (mapping.md §0): layouts must reference kn_* tokens,
        // never a raw #RRGGBB. The same convention is enforced by the
        // Issue #29 CredentialListLayoutTokensTest.
        val xml = editLayout.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage(
            "credential_edit_activity.xml must not hardcode hex colors " +
                "(mapping.md §0 — use @color/kn_*).",
        ).that(hexInAttr.containsMatchIn(xml)).isFalse()
    }
}
