package io.github.hitoshiichikawa.keynest.resources

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Issue #31: source-level pinning of the design-token references on
 * `autofill_enable_activity.xml`, the supporting drawables, and the new
 * onboarding string keys.
 *
 * Mirrors the [CredentialListLayoutTokensTest] pattern from Issue #29:
 * we read the resource XMLs directly so the assertions stay textual and
 * don't require Robolectric for resource inflation. The intent is to
 * make sure a future careless refactor cannot silently revert any of
 * the JSX `ScreenOnboarding` visual contract points (progress dots /
 * hero / steps card / fixed bottom CTA / already-enabled state).
 */
class OnboardingLayoutTokensTest {

    private val layout: File =
        File("src/main/res/layout/autofill_enable_activity.xml")
    private val heroDrawable: File =
        File("src/main/res/drawable/ic_onboarding_hero.xml")
    private val dotActive: File =
        File("src/main/res/drawable/kn_onboarding_progress_dot_active.xml")
    private val dotInactive: File =
        File("src/main/res/drawable/kn_onboarding_progress_dot_inactive.xml")
    private val stepChip: File =
        File("src/main/res/drawable/kn_onboarding_step_chip_bg.xml")
    private val enabledBg: File =
        File("src/main/res/drawable/kn_onboarding_already_enabled_bg.xml")
    private val stringsEn: File =
        File("src/main/res/values/strings.xml")
    private val stringsJa: File =
        File("src/main/res/values-ja/strings.xml")
    private val themes: File =
        File("src/main/res/values/themes.xml")
    private val manifest: File =
        File("src/main/AndroidManifest.xml")

    // ---- Req 9.x: existing IDs and manifest preserved ----------------------

    @Test
    fun layout_preservesBtnEnableAndTextAlreadyEnabledIds() {
        // Req 9.1 boundary: the activity binds binding.btnEnable and
        // binding.textAlreadyEnabled. Removing either would silently
        // break the existing onResume / launchSettings wiring.
        val xml = layout.readText()
        listOf(
            "@+id/btn_enable",
            "@+id/text_already_enabled",
        ).forEach { id ->
            assertWithMessage("layout must declare $id (Req 9.x boundary)")
                .that(xml).contains(id)
        }
    }

    @Test
    fun manifest_preservesAutofillEnableActivityDeclaration() {
        // Req 9.3: the AndroidManifest activity entry must keep its
        // android:name / android:exported / android:label.
        val xml = manifest.readText()
        assertThat(xml).contains(".ui.enable.AutofillEnableActivity")
        assertThat(xml).contains("android:exported=\"false\"")
        assertThat(xml).contains("android:label=\"@string/autofill_enable_title\"")
    }

    @Test
    fun strings_preserveExistingAutofillEnableKeys() {
        // Req 9.4: the 5 existing keys must remain in both en and ja
        // resource tables.
        val keys = listOf(
            "autofill_enable_title",
            "autofill_enable_description",
            "autofill_enable_action",
            "autofill_enable_already_enabled",
            "autofill_enable_settings_unavailable",
        )
        val en = stringsEn.readText()
        val ja = stringsJa.readText()
        keys.forEach { key ->
            assertWithMessage("values/strings.xml must keep $key (Req 9.4)")
                .that(en).contains("name=\"$key\"")
            assertWithMessage("values-ja/strings.xml must keep $key (Req 9.4)")
                .that(ja).contains("name=\"$key\"")
        }
    }

    // ---- Req 1.x: root surface, padding, vertical spacing ------------------

    @Test
    fun layout_bindsRootBackgroundToColorBackgroundAttr() {
        // Req 1.1: the screen background must resolve a theme attribute
        // (kn_bg via Theme.KeyNest android:colorBackground binding), not
        // a raw #hex. We accept the working `?android:attr/colorBackground`
        // alias used elsewhere in the codebase (credential_list_activity).
        val xml = layout.readText()
        assertThat(xml).contains("?android:attr/colorBackground")
    }

    @Test
    fun layout_appliesKnScreenPaddingHForContent() {
        // Req 1.2: horizontal padding = kn_screen_padding_h (20dp).
        // The scroll content uses paddingStart / paddingEnd; the bottom
        // action area uses layout_marginStart / End to the same dimen.
        val xml = layout.readText()
        val count = "@dimen/kn_screen_padding_h".toRegex().findAll(xml).count()
        assertWithMessage("layout should use @dimen/kn_screen_padding_h " +
            "for content + action area side insets (Req 1.2 / Req 6.2)")
            .that(count).isAtLeast(4)
    }

    @Test
    fun layout_usesKnSpaceTokensForVerticalRhythm() {
        // Req 1.3: vertical gaps come from @dimen/kn_space_* tokens.
        // We sample the four required gaps from the JSX:
        //   * 12dp before headline (kn_space_3)
        //   * 24dp before steps card (kn_space_6)
        //   * 24dp under steps card + bottom margin (kn_space_6)
        //   * 32dp under progress dots (kn_space_8)
        val xml = layout.readText()
        listOf(
            "@dimen/kn_space_3",
            "@dimen/kn_space_4",
            "@dimen/kn_space_6",
            "@dimen/kn_space_8",
        ).forEach { token ->
            assertWithMessage("layout should reference $token for vertical rhythm (Req 1.3)")
                .that(xml).contains(token)
        }
    }

    // ---- Req 2.x: progress dot row ----------------------------------------

    @Test
    fun layout_progressDotRowDeclaresThreeSegmentsWithCorrectWeights() {
        // Req 2.1: 3 segments, 4dp tall.
        // Req 2.2: current step (= 2 per JSX `step = 2` default) has 2x
        //          flex; siblings = 1x.
        // Req 2.3: past / current use the active drawable (kn_primary).
        // Req 2.4: future uses the inactive drawable (kn_border_strong).
        // Req 2.5: 6dp gap between segments.
        val xml = layout.readText()
        assertThat(xml).contains("@+id/progress_dot_row")
        // segment 1 = active, weight 1
        assertThat(xml).contains("@+id/progress_dot_1")
        // segment 2 = active, weight 2
        assertThat(xml).contains("@+id/progress_dot_2")
        // segment 3 = inactive, weight 1
        assertThat(xml).contains("@+id/progress_dot_3")
        // active drawable appears twice (segments 1 and 2).
        val activeCount =
            "@drawable/kn_onboarding_progress_dot_active".toRegex().findAll(xml).count()
        assertWithMessage("two segments should use the active drawable (Req 2.3)")
            .that(activeCount).isEqualTo(2)
        // inactive drawable appears once (segment 3).
        val inactiveCount =
            "@drawable/kn_onboarding_progress_dot_inactive".toRegex().findAll(xml).count()
        assertWithMessage("one segment should use the inactive drawable (Req 2.4)")
            .that(inactiveCount).isEqualTo(1)
        // 6dp gap appears twice between segments. (Inline because
        // @dimen/kn_space_* does not include a 6dp token.)
        val gapCount = "android:layout_width=\"6dp\"".toRegex().findAll(xml).count()
        assertWithMessage("two 6dp gap spacers should sit between the 3 segments (Req 2.5)")
            .that(gapCount).isAtLeast(2)
        // 4dp tall row.
        val rowHeightRegex = Regex(
            pattern = "<LinearLayout[^>]*?@\\+id/progress_dot_row[\\s\\S]*?>",
        )
        val match = rowHeightRegex.find(xml)
        assertWithMessage("progress_dot_row declaration must exist")
            .that(match).isNotNull()
        val block = match!!.value
        assertWithMessage("progress_dot_row must be 4dp tall (Req 2.1)")
            .that(block).contains("android:layout_height=\"4dp\"")
        // weights 1 / 2 / 1 across the three segments.
        val weight1Count = "android:layout_weight=\"1\"".toRegex().findAll(xml).count()
        val weight2Count = "android:layout_weight=\"2\"".toRegex().findAll(xml).count()
        assertWithMessage("two segments should have weight=1 (Req 2.2)")
            .that(weight1Count).isAtLeast(2)
        assertWithMessage("the current step should have weight=2 (Req 2.2)")
            .that(weight2Count).isAtLeast(1)
    }

    @Test
    fun progressDotDrawables_useKnPrimaryAndKnBorderStrong() {
        // Req 2.3 / 2.4: the active drawable fills with kn_primary, the
        // inactive drawable fills with kn_border_strong.
        assertThat(dotActive.readText()).contains("@color/kn_primary")
        assertThat(dotInactive.readText()).contains("@color/kn_border_strong")
        // Req 2.1: both use the same kn_space_1 (4dp) corner radius.
        assertThat(dotActive.readText()).contains("@dimen/kn_space_1")
        assertThat(dotInactive.readText()).contains("@dimen/kn_space_1")
    }

    @Test
    fun layout_progressDotRowHas32dpBottomMarginToHero() {
        // Req 2.6: 32dp (= @dimen/kn_space_8) gap between the progress
        // dot row and the hero illustration.
        val xml = layout.readText()
        val rowBlock = Regex(
            pattern = "<LinearLayout[^>]*?@\\+id/progress_dot_row[\\s\\S]*?</LinearLayout>",
        ).find(xml)
        assertWithMessage("progress_dot_row block must be present in layout")
            .that(rowBlock).isNotNull()
        assertWithMessage("progress dots must have @dimen/kn_space_8 bottom margin (Req 2.6)")
            .that(rowBlock!!.value).contains("android:layout_marginBottom=\"@dimen/kn_space_8\"")
    }

    // ---- Req 3.x: hero illustration ----------------------------------------

    @Test
    fun layout_heroImageReferences220x180dpVectorDrawable() {
        // Req 3.1 / 3.2: ic_onboarding_hero at 220dp x 180dp, centered.
        val xml = layout.readText()
        assertThat(xml).contains("@drawable/ic_onboarding_hero")
        val heroBlock = Regex(
            pattern = "<ImageView[^>]*?@\\+id/image_hero[\\s\\S]*?/>",
        ).find(xml)
        assertWithMessage("image_hero declaration must exist")
            .that(heroBlock).isNotNull()
        val block = heroBlock!!.value
        assertWithMessage("hero image must be 220dp wide (Req 3.2)")
            .that(block).contains("android:layout_width=\"220dp\"")
        assertWithMessage("hero image must be 180dp tall (Req 3.2)")
            .that(block).contains("android:layout_height=\"180dp\"")
        // center_horizontal gravity in the LinearLayout flow.
        assertWithMessage("hero image must be centered horizontally (Req 3.2)")
            .that(block).contains("android:layout_gravity=\"center_horizontal\"")
        // NFR 2.2: decorative -> importantForAccessibility=no.
        assertWithMessage("hero image must be marked decorative (NFR 2.2)")
            .that(block).contains("android:importantForAccessibility=\"no\"")
    }

    @Test
    fun heroVectorDrawable_isSized220x180WithKnTokenFills() {
        // Req 3.1 / 3.3: ic_onboarding_hero is 220x180 viewport with
        // kn_blue_500 / kn_blue_600 / kn_success / kn_white / kn_ink_300
        // fills. We pin the literal tokens so a future refactor cannot
        // silently substitute a raw #hex.
        val xml = heroDrawable.readText()
        assertThat(xml).contains("android:viewportWidth=\"220\"")
        assertThat(xml).contains("android:viewportHeight=\"180\"")
        listOf(
            "@color/kn_blue_500",
            "@color/kn_blue_600",
            "@color/kn_success",
            "@color/kn_white",
            "@color/kn_ink_300",
        ).forEach { token ->
            assertWithMessage("hero drawable must reference $token (Req 3.3)")
                .that(xml).contains(token)
        }
    }

    @Test
    fun layout_heroHas28dpBottomMargin() {
        // Req 3.4: 28dp = @dimen/kn_r_xl (28dp per dimens.xml). No
        // dedicated kn_space_* token exists for 28dp.
        val xml = layout.readText()
        val heroBlock = Regex(
            pattern = "<ImageView[^>]*?@\\+id/image_hero[\\s\\S]*?/>",
        ).find(xml)!!.value
        assertWithMessage("hero must have @dimen/kn_r_xl (28dp) bottom margin (Req 3.4)")
            .that(heroBlock).contains("android:layout_marginBottom=\"@dimen/kn_r_xl\"")
    }

    // ---- Req 4.x: headline + description -----------------------------------

    @Test
    fun layout_headlineUsesDisplayStyleAndKnTextColor() {
        // Req 4.1 / 4.2 / 4.3.
        val xml = layout.readText()
        val titleBlock = Regex(
            pattern = "<TextView[^>]*?@\\+id/text_title[\\s\\S]*?/>",
        ).find(xml)!!.value
        assertThat(titleBlock).contains("style=\"@style/Text.KeyNest.Display\"")
        assertThat(titleBlock).contains("android:text=\"@string/autofill_enable_title\"")
        assertThat(titleBlock).contains("android:textColor=\"@color/kn_text\"")
    }

    @Test
    fun layout_descriptionUsesBodyStyleAndKnText2Color() {
        // Req 4.4 / 4.5 / 4.6.
        val xml = layout.readText()
        val descBlock = Regex(
            pattern = "<TextView[^>]*?@\\+id/text_description[\\s\\S]*?/>",
        ).find(xml)!!.value
        assertThat(descBlock).contains("style=\"@style/Text.KeyNest.Body\"")
        assertThat(descBlock).contains("android:text=\"@string/autofill_enable_description\"")
        assertThat(descBlock).contains("android:textColor=\"@color/kn_text_2\"")
    }

    // ---- Req 5.x: steps card -----------------------------------------------

    @Test
    fun layout_stepsCardUsesWidgetKeyNestCardStyle() {
        // Req 5.1 / 5.2.
        val xml = layout.readText()
        assertThat(xml).contains("style=\"@style/Widget.KeyNest.Card\"")
        // Widget.KeyNest.Card supplies contentPadding via the style;
        // the layout overrides to kn_space_4 (16dp) explicitly because
        // the style's default kn_card_padding == 14dp, not 16dp.
        val cardBlock = Regex(
            pattern = "<com\\.google\\.android\\.material\\.card\\.MaterialCardView[^>]*?@\\+id/card_steps[\\s\\S]*?>",
        ).find(xml)!!.value
        assertWithMessage("steps card must use kn_space_4 contentPadding (Req 5.2)")
            .that(cardBlock).contains("app:contentPadding=\"@dimen/kn_space_4\"")
    }

    @Test
    fun layout_stepsCardHasThreeStepRowsAndTwoDividers() {
        // Req 5.3: 3 step rows. Each row uses Widget.KeyNest.OnboardingStepRow.
        // Req 5.6: exactly 2 dividers (between rows 1-2 and 2-3); no
        // divider after row 3.
        val xml = layout.readText()
        val rowCount =
            "Widget\\.KeyNest\\.OnboardingStepRow".toRegex().findAll(xml).count()
        assertWithMessage("layout should contain 3 step rows (Req 5.3)")
            .that(rowCount).isEqualTo(3)
        val chipCount =
            "Widget\\.KeyNest\\.OnboardingStepChip".toRegex().findAll(xml).count()
        assertWithMessage("layout should contain 3 step number chips (Req 5.3)")
            .that(chipCount).isEqualTo(3)
        // 2 dividers between the rows.
        val dividerCount =
            "android:background=\"@color/kn_border\"".toRegex().findAll(xml).count()
        assertWithMessage("layout should contain exactly 2 step dividers " +
            "(Req 5.6 -- last row has no divider)")
            .that(dividerCount).isEqualTo(2)
    }

    @Test
    fun stepChipDrawable_usesKnSurfaceTintFill() {
        // Req 5.3: step number chip background = kn_surface_tint.
        assertThat(stepChip.readText()).contains("@color/kn_surface_tint")
        assertThat(stepChip.readText()).contains("android:shape=\"oval\"")
    }

    @Test
    fun layout_stepRowsUseTextKeyNestBodyAndBodyS() {
        // Req 5.4 / 5.5. The step rows reference Body / BodyS three
        // times each. The kn_text_2 reference is checked once across
        // the file (used by the description rows).
        val xml = layout.readText()
        val bodyCount =
            "@style/Text\\.KeyNest\\.Body\"".toRegex().findAll(xml).count()
        val bodySCount =
            "@style/Text\\.KeyNest\\.BodyS".toRegex().findAll(xml).count()
        assertWithMessage("at least 4 references to Text.KeyNest.Body " +
            "(description + 3 step titles) (Req 5.4)")
            .that(bodyCount).isAtLeast(4)
        assertWithMessage("at least 3 references to Text.KeyNest.BodyS " +
            "(3 step descriptions) (Req 5.5)")
            .that(bodySCount).isAtLeast(3)
    }

    @Test
    fun stringsResource_contains6NewStepKeysAndLaterKeyInBothLocales() {
        // Req 5.8 / Req 6.7 / NFR 3.1: the 7 new keys must exist in
        // both values/strings.xml and values-ja/strings.xml.
        val newKeys = listOf(
            "autofill_enable_step1_title",
            "autofill_enable_step1_description",
            "autofill_enable_step2_title",
            "autofill_enable_step2_description",
            "autofill_enable_step3_title",
            "autofill_enable_step3_description",
            "autofill_enable_action_later",
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

    @Test
    fun stringsResource_japaneseStepCopyMatchesJsxScreenOnboarding() {
        // Req 5.7: Japanese step copy must match the JSX `steps` array.
        val ja = stringsJa.readText()
        assertThat(ja).contains("「設定を開く」をタップ")
        assertThat(ja).contains("Android の設定画面に移動します")
        assertThat(ja).contains("「KeyNest」を選択")
        assertThat(ja).contains("パスワードとアカウントの項目で")
        assertThat(ja).contains("戻ってきたら準備完了")
        assertThat(ja).contains("クレデンシャルを登録できます")
        // Subaction "later" label.
        assertThat(ja).contains(">あとで<")
    }

    // ---- Req 6.x: bottom action area ---------------------------------------

    @Test
    fun layout_bottomActionAreaIsFixedAndUses20dpSideAnd24dpBottomInset() {
        // Req 6.2: bottom action area is fixed (alignParentBottom),
        // with kn_screen_padding_h (20dp) side margins and kn_space_6
        // (24dp) bottom margin.
        val xml = layout.readText()
        val actionBlock = Regex(
            pattern = "<LinearLayout[^>]*?@\\+id/group_actions[\\s\\S]*?</LinearLayout>",
        ).find(xml)!!.value
        assertThat(actionBlock).contains("android:layout_alignParentBottom=\"true\"")
        assertThat(actionBlock).contains("android:layout_marginStart=\"@dimen/kn_screen_padding_h\"")
        assertThat(actionBlock).contains("android:layout_marginEnd=\"@dimen/kn_screen_padding_h\"")
        assertThat(actionBlock).contains("android:layout_marginBottom=\"@dimen/kn_space_6\"")
    }

    @Test
    fun layout_primaryCtaUsesButtonPrimaryStyleAndButtonHeight() {
        // Req 6.1 / 6.3.
        val xml = layout.readText()
        val ctaBlock = Regex(
            pattern = "<com\\.google\\.android\\.material\\.button\\.MaterialButton[^>]*?@\\+id/btn_enable[\\s\\S]*?/>",
        ).find(xml)!!.value
        assertThat(ctaBlock).contains("style=\"@style/Widget.KeyNest.Button.Primary\"")
        assertThat(ctaBlock).contains("android:layout_width=\"match_parent\"")
        assertThat(ctaBlock).contains("android:layout_height=\"@dimen/kn_button_height\"")
        assertThat(ctaBlock).contains("android:text=\"@string/autofill_enable_action\"")
    }

    @Test
    fun layout_laterButtonUsesTextButtonStyleAndCorrectMetrics() {
        // Req 6.1 / 6.4 / 6.5 / 6.7. The visible height is 44dp, but
        // we also pin android:minHeight=48dp so NFR 2.1 (48dp tap
        // target) is satisfied without changing the visual height.
        val xml = layout.readText()
        val laterBlock = Regex(
            pattern = "<com\\.google\\.android\\.material\\.button\\.MaterialButton[^>]*?@\\+id/btn_later[\\s\\S]*?/>",
        ).find(xml)!!.value
        assertThat(laterBlock).contains("style=\"@style/Widget.KeyNest.Button.Text\"")
        assertThat(laterBlock).contains("android:layout_width=\"match_parent\"")
        assertThat(laterBlock).contains("android:layout_height=\"44dp\"")
        assertThat(laterBlock).contains("android:minHeight=\"48dp\"")
        assertThat(laterBlock).contains("android:textColor=\"@color/kn_primary\"")
        assertThat(laterBlock).contains("android:text=\"@string/autofill_enable_action_later\"")
        // Req 6.5: 6dp top margin between primary CTA and later button.
        assertThat(laterBlock).contains("android:layout_marginTop=\"6dp\"")
    }

    // ---- Req 7.x: already-enabled state -----------------------------------

    @Test
    fun layout_alreadyEnabledGroupOverlapsActionsAreaAndStartsHidden() {
        // Req 7.1 / 7.5: the group_already_enabled container shares the
        // bottom anchor with group_actions and starts hidden
        // (visibility="gone"). The activity toggles them on resume.
        val xml = layout.readText()
        val enabledBlock = Regex(
            pattern = "<LinearLayout[^>]*?@\\+id/group_already_enabled[\\s\\S]*?</LinearLayout>",
        ).find(xml)!!.value
        assertThat(enabledBlock).contains("android:layout_alignParentBottom=\"true\"")
        assertThat(enabledBlock).contains("android:visibility=\"gone\"")
        // Req 7.2: still uses the existing string key (preserved).
        assertThat(enabledBlock).contains("@string/autofill_enable_already_enabled")
        // Req 7.3: uses a success-tinted shield icon.
        assertThat(enabledBlock).contains("@drawable/ic_shield_check_24")
        assertThat(enabledBlock).contains("app:tint=\"@color/kn_success\"")
        // Req 7.4: rounded kn_success_soft background.
        assertThat(enabledBlock).contains("@drawable/kn_onboarding_already_enabled_bg")
    }

    @Test
    fun alreadyEnabledBgDrawable_usesKnSuccessSoftAndKnRCard() {
        // Req 7.4: kn_success_soft fill + kn_r_card (18dp) radius.
        val xml = enabledBg.readText()
        assertThat(xml).contains("@color/kn_success_soft")
        assertThat(xml).contains("@dimen/kn_r_card")
    }

    @Test
    fun themesXml_declaresOnboardingHelperStyles() {
        // Req 5.x / Req 6.x: the two new Widget.KeyNest helper styles
        // exist with the right parents and key attributes.
        val xml = themes.readText()
        assertThat(xml).contains("name=\"Widget.KeyNest.OnboardingStepRow\"")
        assertThat(xml).contains("name=\"Widget.KeyNest.OnboardingStepChip\"")
        // The step chip should pull the chip background drawable.
        assertThat(xml).contains("@drawable/kn_onboarding_step_chip_bg")
    }

    // ---- Req 8.x: dark mode integrity --------------------------------------

    @Test
    fun layout_doesNotHardcodeHexColors() {
        // Req 8.1 / 8.2: layouts must reference kn_* tokens, never raw
        // #RRGGBB. The drawables themselves may use #00000000 (fully
        // transparent) for stroke-only paths, but the layout must not.
        val xml = layout.readText()
        val hexInAttr = Regex("=\"#[0-9a-fA-F]{6,8}\"")
        assertWithMessage("autofill_enable_activity.xml must not hardcode " +
            "hex colors (mapping.md §0 -- use @color/kn_*).")
            .that(hexInAttr.containsMatchIn(xml)).isFalse()
    }
}
