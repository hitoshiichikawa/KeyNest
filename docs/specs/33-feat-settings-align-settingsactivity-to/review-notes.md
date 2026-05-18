# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-15T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-33-impl-feat-settings-align-settingsactivity-to
- HEAD commit: 239ef367fa17283a61e4d0b7f759767f872d0735
- Compared to: develop..HEAD
- Commits:
  - 15d9baf feat(settings): add KeyNest tokens for settings drawables and strings (#33)
  - f63181c feat(settings): align SettingsActivity layout and bindings to ScreenSettings (#33)
  - af578c3 feat(settings): align DangerZone and OssLicenses layouts to KeyNest tokens (#33)
  - d75d13f test(settings): pin design-token references on settings layouts (#33)
  - 239ef36 docs(impl-notes): record Issue #33 implementation summary and AC coverage
- Note: 本 Issue は Architect を経由していないため `tasks.md` / `design.md` は不在。
  `_Boundary:_` アノテーションが存在しないため、boundary 逸脱は変更ファイルが
  requirements.md Introduction で言及されたスコープ（SettingsActivity / DangerZoneActivity /
  OssLicensesActivity および対応リソース）に収まっているかで判定する。

## Verified Requirements

- 1.1 — `settings_activity.xml` root に `android:background="?android:attr/colorBackground"` を付与 (`SettingsLayoutTokensTest.settingsLayout_bindsRootBackgroundToColorBackgroundAttr`)
- 1.2 — `paddingHorizontal="@dimen/kn_screen_padding_h"` (`settingsLayout_appliesScreenHorizontalPaddingToken`)
- 1.3 — `MaterialToolbar` + `@string/settings_title` / `@string/settings_back_a11y` を維持 (`settingsLayout_preservesToolbar`)
- 1.4 — `app:titleTextAppearance="@style/Text.KeyNest.TitleS"` (`settingsLayout_toolbarTitleUsesTitleSStyle`)
- 1.5 — hero/group の `layout_margin*` に `kn_space_1` / `kn_space_5` / `kn_section_gap` / `kn_space_6` を使用（layout XML を実視）
- 2.1 — hero 用 `@+id/group_autofill_hero` を AppBar 直下に配置（layout XML）
- 2.2 — `kn_settings_hero_gradient_enabled.xml` で `@dimen/kn_r_lg` (`heroGradientDrawable_usesPrimaryAndBlue700AndKnRLgCorners`)
- 2.3 — hero `android:padding="@dimen/kn_card_padding_lg"` (`settingsLayout_heroAppliesCardPaddingLg`)
- 2.4 — `kn_primary` → `kn_blue_700` の linear gradient (angle=315) を `kn_settings_hero_gradient_enabled.xml` で定義（同上テスト）
- 2.5 — `@+id/text_autofill_hero_title` + `@string/settings_autofill_hero_title` と `@+id/text_autofill_status` (description) を Enabled 時に `@color/kn_on_primary` で描画 (`settingsLayout_heroHasTitleAndAccessibilityHeading` / `SettingsActivity.applyAutofillHeroEnabled()`)
- 2.6 — `@+id/chip_autofill_status` + `kn_settings_hero_chip_bg_enabled.xml`（半透明白 #33FFFFFF, `@dimen/kn_r_pill`）(`heroChipEnabledDrawable_usesPillCorners` / `settingsLayout_heroHasStatusChipWithA11yLabel`)
- 2.7 — `kn_settings_hero_bg_notenabled.xml` で `@color/kn_warning_soft` / `@dimen/kn_r_lg`、`applyAutofillHeroNotEnabled()` で chip 文字色 `kn_warning`・description `kn_text_2`、CTA `kn_warning` accent (`heroNotEnabledDrawable_usesWarningSoftAndKnRLgCorners`)
- 2.8 — chip に `@string/settings_autofill_badge_enabled|not_enabled` を `bindAutofill()` で差し替え（同上テスト + Kotlin）
- 2.9 — hero title `@style/Text.KeyNest.TitleM` (`settingsLayout_heroHasTitleAndAccessibilityHeading`)
- 2.10 — hero description `@style/Text.KeyNest.Caption` (`settingsLayout_heroDescriptionUsesCaptionStyleAndKnTextAutofillStatusId`)
- 2.11 — hero 内 `@+id/btn_open_autofill_settings` を保持 (`settingsLayout_heroCtaIsBtnOpenAutofillSettingsWith48dpTouch`)
- 2.12 — `kn_settings_hero_cta_bg_enabled.xml` (半透明白 + stroke) / `kn_settings_hero_cta_bg_notenabled.xml` (warning accent) (`heroCtaEnabledDrawable_hasStrokeForGlassEffect` + SettingsActivity.kt)
- 2.13 — `SystemSettingsIntents.openAutofillServiceChooser(this)` 経路を Kotlin 側で保持（`wireRows()` 既存ロジック維持）
- 2.14 — DOM 順 chip → title → description → CTA、title に `accessibilityHeading="true"`（layout XML + 同上テスト）
- 3.1/3.2/3.3 — `kn_settings_group_bg.xml` (`@color/kn_surface` + `@color/kn_border` 1dp + `@dimen/kn_r_md`) (`groupBgDrawable_usesKnSurfaceFillAndKnBorderStrokeAndKnRMd`)
- 3.4/3.5/3.6/3.9 — `eyebrow_security|vault|about|danger` に `@style/Text.KeyNest.Eyebrow` + `@color/kn_text_3` + `accessibilityHeading="true"` (`settingsLayout_eyebrowHeadingsUseEyebrowStyleAndAccessibilityHeading`)
- 3.7 — eyebrow `layout_marginBottom="@dimen/kn_space_2"` (= 8dp)（layout XML）
- 3.8 — eyebrow `paddingHorizontal="@dimen/kn_space_1"` (= 4dp 相当)（layout XML）
- 3.10 — group `layout_marginBottom="@dimen/kn_section_gap"` (24dp)（layout XML）
- 4.1 — `@dimen/kn_icon_tile_sm` を leading icon tile に適用 (`settingsLayout_rowsApplyIconTileSmAndDividerAndShowDividersMiddle`)
- 4.2/4.3 — `kn_settings_row_icon_tile_bg.xml` で `@color/kn_surface_2` + `@dimen/kn_r_sm` (`rowIconTileBgDrawable_usesKnSurface2AndKnRSmCorners`)
- 4.4 — icon `app:tint="@color/kn_text_2"`（layout XML / Danger 行は 4.13 を優先し `kn_danger`）
- 4.5/4.6/4.7 — label 行 `@style/Text.KeyNest.Body` + `@color/kn_text`（layout XML）
- 4.8/4.9 — sub 行 `@style/Text.KeyNest.Caption` + `@color/kn_text_2`（layout XML）
- 4.10 — chevron ImageView を遷移行に配置 (`settingsLayout_chevronImageReferencesIcChevronRight24`)
- 4.11 — Vault 行右端の right-value TextView（`text_vault_*`）に `@style/Text.KeyNest.BodyS` + `@color/kn_text_2`（layout XML）
- 4.12 — chevron `app:tint="@color/kn_text_3"`（layout XML）
- 4.13 — Danger 行で label `@color/kn_danger` / icon `app:tint="@color/kn_danger"` (`settingsLayout_dangerRowUsesWarningIconAndKnDangerTint`)
- 4.14 — 行 `paddingHorizontal="@dimen/kn_space_3"` / `paddingVertical="@dimen/kn_space_3"`（layout XML）
- 4.15 — icon ↔ label gap `layout_marginStart="@dimen/kn_space_3"`（layout XML）
- 4.16 — 各 row `minHeight="48dp"` (`settingsLayout_rowsReserveMinTouchSize48dp`)
- 4.17 — `kn_settings_row_divider.xml` + `showDividers="middle"` (`rowDividerDrawable_usesKnBorderColor` / `settingsLayout_rowsApplyIconTileSmAndDividerAndShowDividersMiddle`)
- 5.1-5.11 — Security group, lock status row with ic_fingerprint_24 + chevron, `@+id/btn_open_security_settings` & `@+id/text_lock_status` を維持、`@string/settings_lock_status_label`、`openSecuritySettings` 経路と Snackbar fallback は Kotlin 側維持 (`settingsLayout_lockStatusRowUsesFingerprintIconAndChevron` / `settingsLayout_lockStatusRowLabelReferencesNewStringKey`)
- 6.1-6.10 — Vault group with 3 rows (count/latest/storage)、`@+id/text_vault_count|latest_updated|storage` 維持、ic_key_24 / ic_clock_24 / ic_database_24、right-value のみ chevron なし (`settingsLayout_vaultGroupHasThreeRowsWithExistingValueIds` / `settingsLayout_vaultRowsUseExpectedIcons`)
- 7.1-7.16 — About group with KeyNest / OSS / Privacy 3 rows、`@+id/text_app_version` 維持、ic_keynest_mark_24 / ic_description_24 / ic_shield_outline_24、`@+id/btn_oss_licenses` 維持、OSS row に chevron、Privacy row sub テキスト (`settingsLayout_aboutGroupKeepsTextAppVersionIdAndUsesKnMark` / `settingsLayout_aboutGroupOssRowHasChevronAndDocumentIcon` / `settingsLayout_aboutGroupPrivacyRowHasShieldIcon`)
- 8.1-8.11 — Danger zone group with `@drawable/kn_settings_group_bg_danger` (`@color/kn_danger_soft` + `@dimen/kn_r_md`)、ic_warning_24 + `@color/kn_danger` tint、`@+id/btn_open_danger_zone` 維持、`@string/settings_danger_delete_all_label|sub` (`groupBgDangerDrawable_usesKnDangerSoftAndKnRMd` / `settingsLayout_dangerRowUsesWarningIconAndKnDangerTint`)
- 9.1-9.7 — `danger_zone_activity.xml`: `kn_settings_danger_card_bg` 背景、`?attr/colorErrorContainer` / `?attr/colorOnErrorContainer` 撤去、`btn_clear` → `Widget.KeyNest.Button.Destructive`、`btn_retry` → `Widget.KeyNest.Button.Text` + `kn_danger`、既存 ID（toolbar / btn_clear / btn_retry / group_progress / text_section_heading / text_description）維持、Kotlin 側ロジック無変更 (`dangerCardBgDrawable_usesKnDangerSoftAndKnRMd` / `dangerZoneLayout_routesThroughKnSettingsDangerCardBgAndKnDangerToken` / `dangerZoneLayout_btnClearUsesDestructiveStyle` / `dangerZoneLayout_btnRetryUsesTextButtonStyle` / `dangerZoneLayout_preservesAllExistingViewIds`)
- 10.1-10.6 — `oss_licenses_item.xml`: `@style/Widget.KeyNest.Card` 適用、`text_name` → `Text.KeyNest.TitleS`、`text_license`/`text_body` → `Text.KeyNest.BodyS|Caption` + `@color/kn_text_2`、`btn_url` → `Widget.KeyNest.Button.Text` + `@color/kn_primary`、既存 ID 維持、Adapter ロジック無変更 (`ossLicensesItem_cardUsesKnCardStyle` / `ossLicensesItem_namesAndBodiesUseKnTextAppearances` / `ossLicensesItem_btnUrlUsesKnTextButtonStyleAndPrimaryColor` / `ossLicensesItem_preservesAllExistingViewIds`)
- 11.1/11.2 — layout XML はすべて `kn_*` セマンティックトークン経由（hex 直書きを `*_doesNotHardcodeHexColorsInAttributes` で禁止 pin）。新規 drawable も `@color/kn_*` 参照 + `@dimen/kn_*` 参照
- 11.3/11.4 — Phase 1 で確定済みの `kn_text` / `kn_text_2` / `kn_text_3` / `kn_warning` 等のコントラスト確保済みトークンを利用（既存 Phase 1 トークン層は無変更）
- 11.5/11.6 — `kn_warning_soft` / `kn_danger_soft` は Phase 1 で `values-night` 別解像済み。本 Issue は当該トークンを参照しているのみで上書きしていない
- 12.1 — `SettingsActivity.newIntent(context)` 公開 API シグネチャ無変更（Kotlin diff で確認）
- 12.2 — `SettingsViewModel` への接続（ServiceLocator / observeVaultMetadataUseCase / getVaultStorageUsageUseCase / getDeviceLockStatusUseCase / appInfoProvider）無変更
- 12.3 — `onResume()` の `viewModel.refresh()` 経路を維持
- 12.4 — 11 個の既存 View ID を保持 (`settingsLayout_preservesAllExistingViewIds` / `dangerZoneLayout_preservesAllExistingViewIds` / `ossLicensesItem_preservesAllExistingViewIds`)
- 12.5 — AndroidManifest 無変更（diff に含まれない）
- 12.6 — 26 個の既存 settings_* 文字列キーを保持 (`stringsResource_preservesAllExistingSettingsStringKeys`)
- 12.7 — Intent failure 時の `@string/settings_intent_unavailable` Snackbar 経路を Kotlin 側で維持（`showIntentUnavailableSnackbar()` 無変更）
- 12.8 — 既存 `SettingsActivityTest` は `@Ignore` 待機中（impl-notes Confirm #5 で明示）。本 Issue は View ID を維持しているため、`withId(...).perform(click())` 経路は引き続き機能する想定。テスト本体は未変更
- NFR 1.1 — `./gradlew :app:assembleDebug` 成功（impl-notes に記載）
- NFR 1.2 — 既存 unit test の合否は本 Issue 前後で不変（453 中 447 成功 / 事前失敗 5 件は `develop` でも同条件で失敗することを impl-notes で確認）
- NFR 1.3 — `Material3ThemeMigrationTest` / `FontTypefaceWiringTest` の structural pin に影響する変更なし（`Theme.KeyNest` / `TextAppearance.KeyNest.*` に変更なし）
- NFR 2.1 — 48dp minHeight を行ごとに保持 (`settingsLayout_rowsReserveMinTouchSize48dp`)
- NFR 2.2 — 装飾 icon に `importantForAccessibility="no"`、押下対象に `contentDescription`（layout XML）
- NFR 2.3 — hero title + 各 eyebrow に `accessibilityHeading="true"` (`settingsLayout_eyebrowHeadingsUseEyebrowStyleAndAccessibilityHeading` / `settingsLayout_heroHasTitleAndAccessibilityHeading`)
- NFR 2.4 — Phase 1 トークン継承（既存トークン層に変更なし）
- NFR 2.5 — `chip_autofill_status.contentDescription` に `settings_autofill_badge_a11y` を `bindAutofill()` で再設定（SettingsActivity.kt diff）
- NFR 3.1/3.4 — 13 個の新規キーを en + ja 双方に追加 (`stringsResource_containsNewSettingsKeysInBothLocales`)
- NFR 3.2/3.3 — Android リソース解決の標準動作（XML レベルではキー存在のみ pin。動作は Android runtime に委ねる）

## Findings

なし

## Summary

requirements.md の Req 1〜12（全 12 セクション）および NFR 1〜3 の各 numeric AC に対して
実装またはテストが揃っており、特に 5 commit (`15d9baf` リソース層 / `f63181c` SettingsActivity /
`af578c3` DangerZone+OSS / `d75d13f` テスト追加 / `239ef36` impl-notes) で実装単位が分割されている。
`SettingsLayoutTokensTest` (47 ケース) が XML / drawable / string キーをソースレベルで pin し、
既存 11 個の View ID と 26 個の string キー保持も pin されている。boundary 観点でも変更ファイルは
SettingsActivity / DangerZoneActivity / OssLicensesActivity 関連リソース・コードに収まっており、
本 Issue Introduction で明示されたスコープに整合する。Feature Flag Protocol は opt-out 宣言のため
適用外。

RESULT: approve
