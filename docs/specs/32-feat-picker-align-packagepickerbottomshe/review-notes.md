# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-15T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-32-impl-feat-picker-align-packagepickerbottomshe
- HEAD commit: 5f3cd477380a8b6c8acfe3d11b7ccb1100c2042c
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out（flag 観点の細目は適用しない）
- tasks.md: 未生成（本 Issue は Architect 経由ではないため境界判定は requirements.md の
  "Out of Scope"（UI 表層のみ / 既存ロジック維持）と Issue 本文の DoD で行う）

## Verified Requirements

- 1.1 — `package_picker_bottom_sheet.xml` ルート背景に `@drawable/kn_picker_sheet_bg`
  を割当 / `kn_picker_sheet_bg.xml` solid=`@color/kn_surface`（Theme.KeyNest 経由で
  `?attr/colorSurface` と等価）/ test `sheetLayout_bindsRootBackgroundToColorSurfaceToken`
  および `sheetBgDrawable_usesKnSurfaceFillAndKnRSheetCorners`
- 1.2 — `kn_picker_sheet_bg.xml` 上端 `topLeftRadius/topRightRadius=@dimen/kn_r_sheet`
  / test `sheetLayout_appliesKnRSheetCornerRadius`
- 1.3 — タイトル行 `paddingStart/End=@dimen/kn_screen_padding_h`、検索バー / 手動入力行
  `layout_marginStart/End=@dimen/kn_list_padding_h` / test
  `sheetLayout_appliesHorizontalListPaddingToken`
- 1.4 — `companion object` の `show(manager, onPicked)` シグネチャ不変（差分なし）
- 2.1 / 2.2 — `<View@drag_handle>` width=`@dimen/kn_handle_w` / height=`@dimen/kn_handle_h`
  / `layout_gravity="center_horizontal"` / test `sheetLayout_dragHandleHasCorrectDimensions`
- 2.3 / 2.4 — `kn_picker_drag_handle.xml` solid=`@color/kn_ink_200` corners=`@dimen/kn_r_pill`
  / test `dragHandleDrawable_usesKnInk200AndKnRPill`
- 2.5 — `layout_marginTop="12dp" / layout_marginBottom="14dp"`（impl-notes 確認事項 1 で
  ハードコード理由明示）
- 3.1〜3.7 — `text_title` (`Text.KeyNest.TitleM` + `kn_text` + `package_picker_title`)
  / `text_subtitle` (`Text.KeyNest.BodyS` + `kn_text_2` + `package_picker_subtitle`)
  / test `sheetLayout_titleUsesTitleMStyleAndKnTextColor` / `sheetLayout_subtitleUsesBodySStyleAndKnText2Color`
- 3.8 / 3.9 — `<ImageButton@btn_close>` + `@string/package_picker_close_a11y` +
  `binding.btnClose.setOnClickListener { dismiss() }` / test
  `sheetLayout_closeButtonExistsWithA11yDescription`
- 4.1〜4.6 — 検索バー LinearLayout + `kn_picker_search_bar_bg`（solid=kn_surface_2 +
  corners=kn_r_sm）+ `ic_search_24` (tint kn_text_3) + `EditText@input_search_picker`
  (`Text.KeyNest.Body` + hint=package_picker_search_hint) / tests
  `sheetLayout_searchBarUsesPickerBackgroundDrawableAndIcon` /
  `searchBarDrawable_usesKnInk50FillAndKnRSmCorners` / `sheetLayout_searchBarHasInputSearchPickerId`
- 4.7 / 4.8 — `matchesQuery()` （case-insensitive substring + blank → true）/ tests
  `matchesQuery_returnsTrueOnLabelSubstringMatchCaseInsensitive` /
  `matchesQuery_returnsTrueOnPackageNameSubstringMatch` /
  `matchesQuery_returnsTrueOnEmptyOrBlankQuery`
- 4.9 — `buildItems()` 末尾の `ListItem.Empty`（query 非空かつ両セクション空のみ）+
  `package_picker_empty_item.xml`（`@string/package_picker_no_results`）+ 手動入力行は常時表示
  / test `matchesQuery_returnsFalseWhenNeitherFieldContainsQuery`
- 5.1 / 5.6 — `buildItems()` の section 順序 / `RowVH` が `package_picker_row_item.xml` を inflate
- 5.2 / 5.3 / 6.2 — `package_picker_section_header_item.xml` style=`Text.KeyNest.Eyebrow` +
  `textColor="@color/kn_text_3"` / test `sectionHeaderItem_usesEyebrowStyleAndKnText3Color`
- 5.4 / 6.3 — adapter `HeaderVH.bind` で `setText(R.string.package_picker_section_used / _all)`
  / test `stringsResource_containsBothSectionHeaderKeys`
- 5.5 / 5.7 — `SAMPLE_FREQUENTLY_USED` 3 件（Salesforce / Workday / Kintone）/ `buildItems()`
  内 `if (filteredFrequent.isNotEmpty())` で 0 件時はヘッダー含めて非表示 / tests
  `sampleApps_contains3Entries` / `sampleApps_includesSalesforceWorkdayAndKintone`
- 6.1 — `buildItems()` の section 順序（業務 → すべて）
- 6.4 / 6.6 — `loadInstalledApps()` 関数本体不変（`Dispatchers.IO` + `getInstalledApplications(0)`
  + `label.lowercase()` ソート維持）/ `viewLifecycleOwner.lifecycleScope.launch + withContext(Dispatchers.IO)`
  も不変
- 6.5 — `RowVH` が `package_picker_row_item.xml` を inflate
- 7.1 / 7.2 / 7.3 — `package_picker_row_item.xml` `FrameLayout` width/height=`@dimen/kn_icon_tile_sm`
  + `background="@drawable/kn_icon_tile_bg"`（既存の kn_blue_500 単色 + kn_r_sm corners）/ test
  `rowItem_iconTileIs32dpAndUsesKnIconTileBg`
- 7.4 — row item の vertical LinearLayout に `text_app_label` + `text_app_package` / test
  `rowItem_declaresExpectedTextIds`
- 7.5 / 7.6 / 7.7 — app label `Text.KeyNest.Body` + ellipsize=end / package `Text.KeyNest.Mono` +
  `kn_text_3` + ellipsize=end / test `rowItem_appNameUsesBodyStyleAndPackageNameUsesMonoStyle`
- 7.8 — row `paddingHorizontal/Vertical=@dimen/kn_space_3` + アイコン `layout_marginEnd=@dimen/kn_space_3`
- 7.9 — `?attr/selectableItemBackground`（impl-notes 確認事項 5 で kn_r_sm 行背景を採らない理由
  明示。要件文言「タップ時のリップル領域と整合させる」は selectableItemBackground で満たす）
- 7.10 — `RowVH.bind` で `binding.root.setOnClickListener { onClick(item.app.packageName) }`、
  adapter コンストラクタに渡される `::onRowPicked` が `onPicked?.invoke(packageName); dismiss()` を実行
- 7.11 — row item `android:minHeight="48dp"` / test `rowItem_hasMinTouchHeight48dp`
- 8.1 / 8.2 — `<MaterialButton@btn_manual_entry>` を RecyclerView の外側固定フッターとして配置
  （RecyclerView は `layout_height="0dp" + weight=1`）/ test
  `sheetLayout_manualEntryRowIsAtBottomWithKnBlue50Background`
- 8.3 — `kn_picker_manual_row_bg.xml` solid=`@color/kn_blue_50` corners=`@dimen/kn_r_sm` +
  drawable-night counterpart で `kn_primary_container` を割当 / test
  `manualRowDrawable_usesKnBlue50FillAndKnRSmCorners`
- 8.4 / 8.5 — `textColor="@color/kn_primary"` + `textAppearance="@style/Text.KeyNest.LabelL"`
- 8.6 — `text="@string/package_picker_manual"` / test
  `sheetLayout_manualEntryRowIsAtBottomWithKnBlue50Background`
- 8.7 / 8.8 — `showManualEntryDialog()` で AlertDialog + `package_picker_manual_dialog_input.xml`
  + OK ボタンで valid なら `onRowPicked(typed)` を呼ぶ
- 8.9 — `isManualEntryValid()`（blank / no-dot / 空白 / 不正 shape を reject）+ Snackbar に
  `package_picker_manual_input_invalid` を表示 / tests
  `manualEntry_rejectsBlankInput` / `manualEntry_rejectsInputWithoutDot` /
  `manualEntry_rejectsInputWithWhitespace` / `manualEntry_rejectsMalformedShapes` +
  positive case `manualEntry_acceptsValidPackageNames`
- 9.1 — `companion object` の `show(manager, onPicked)` シグネチャ不変
- 9.2 — `@+id/recycler` を layout で保持 / test `sheetLayout_preservesRecyclerId`
- 9.3 — `loadInstalledApps()` 本体不変
- 9.4 — `onDestroyView()` 既存実装（`_binding?.recycler?.adapter = null; _binding = null`）不変
- 9.5 — `show()` 経路維持で `CredentialEditActivity.onPickInstalledAppClicked()` の挙動互換
- 9.6 — 既存テストへの破壊なし（impl-notes の test 結果で確認: Material3ThemeMigrationTest /
  FontTypefaceWiringTest / CredentialEditLayoutTokensTest 全て pass）
- 10.1 / 10.2 — semantic token (`kn_surface`, `kn_text`, `kn_text_2`, `kn_text_3`, `kn_surface_2`,
  `kn_primary`) のみ参照 / hex 直書きなし / tests `sheetLayout_doesNotHardcodeHexColors` /
  `rowItem_doesNotHardcodeHexColors`
- 10.3 / 10.4 — Phase 1 で WCAG AA 検証済みの token を使用（kn_text / kn_text_2 / kn_text_3 /
  kn_ink_200 / kn_surface_2）
- 10.5 — `drawable-night/kn_picker_manual_row_bg.xml` で `kn_primary_container` を割当
- NFR 1.1 — `./gradlew :app:assembleDebug` BUILD SUCCESSFUL（impl-notes 末尾参照）
- NFR 1.2 — Phase 1/2 既存テスト破壊なし。失敗 5 件は develop に既存の pre-existing failure
  （LockedFillResponseSecurityTest + 4 PackageSignatureResolverTest）で本 Issue 無関係
- NFR 1.3 — `Theme.KeyNest` / `TextAppearance.KeyNest.*` の structural pin 違反なし
- NFR 2.1 — btn_close / row item / btn_manual_entry / 検索バー LinearLayout 全て minHeight≥48dp
  / test `rowItem_hasMinTouchHeight48dp`
- NFR 2.2 — btn_close `contentDescription="@string/package_picker_close_a11y"` / 検索アイコン /
  drag handle は `importantForAccessibility="no"`（純粋な装飾）
- NFR 2.3 — Req 10.3/10.4 と同じ token 経路
- NFR 2.4 — `package_picker_section_header_item.xml` `android:accessibilityHeading="true"`
- NFR 3.1 / 3.2 — 新規 10 キー（subtitle / search_hint / section_used / section_all / manual /
  close_a11y / no_results / manual_input_title / manual_input_hint / manual_input_invalid）が
  values/ と values-ja/ の両方に追加 / test
  `stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales`
- NFR 3.3 — Android 標準のリソース解決経路に依存（特殊実装なし）

## Findings

なし

## Summary

requirements.md の全 numeric ID（Req 1.1〜10.5 / NFR 1.1〜3.3）について、対応する実装または
テストが差分または既存コード（loadInstalledApps / Phase 1 トークン）に存在することを確認。
スコープも requirements.md "Out of Scope" の「UI 表層のみ」「既存ロジック維持」に整合し、
PackagePickerBottomSheet 関連ファイル（Kotlin / picker layouts / picker drawables / picker
strings / picker tests）以外への変更なし。Feature Flag Protocol は opt-out のため flag 観点の
細目は不適用。新規ユニットテスト 31 件（PackagePickerLayoutTokensTest / SampleAppsTest /
ManualEntryValidationTest）が AC を pin しており、impl-notes に記載の 5 件 pre-existing failure
は develop に既存で本 Issue と無関係であることを clean worktree で再確認済み。

RESULT: approve
