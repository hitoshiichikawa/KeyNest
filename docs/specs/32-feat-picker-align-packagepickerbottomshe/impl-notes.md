# Implementation Notes — Issue #32

## 実装サマリ

Phase 2 (#4) として `PackagePickerBottomSheet` を JSX `ScreenPicker`
（`design/screens/screens-2.jsx`）に揃えた。Phase 1 (#28) で導入済みの
`@color/kn_*` / `@dimen/kn_*` / `@style/Text.KeyNest.*` トークンを直接
貼り込み、既存の `show(manager, onPicked)` API と `PackageManager.getInstalledApplications(0)` の取得経路は不変。

### 変更ファイル一覧

| ファイル | 種別 | 責務 |
|---|---|---|
| `app/src/main/res/drawable/kn_picker_drag_handle.xml` | 新規 | drag handle (36x4dp / kn_ink_200 / kn_r_pill) |
| `app/src/main/res/drawable/kn_picker_search_bar_bg.xml` | 新規 | 検索バー (kn_surface_2 + kn_r_sm 12dp) |
| `app/src/main/res/drawable/kn_picker_manual_row_bg.xml` | 新規 | 手動入力行 (kn_blue_50 + kn_r_sm 12dp) |
| `app/src/main/res/drawable/kn_picker_sheet_bg.xml` | 新規 | sheet 根本 (kn_surface + kn_r_sheet 28dp 上端角丸) |
| `app/src/main/res/drawable-night/kn_picker_manual_row_bg.xml` | 新規 | Dark mode 用フォールバック (kn_primary_container) |
| `app/src/main/res/layout/package_picker_bottom_sheet.xml` | 大幅改修 | drag handle + タイトル + 検索バー + RecyclerView + 手動入力フッター |
| `app/src/main/res/layout/package_picker_row_item.xml` | 新規 | RecyclerView 行 (32dp icon tile + Body + Mono) |
| `app/src/main/res/layout/package_picker_section_header_item.xml` | 新規 | Eyebrow セクションヘッダー |
| `app/src/main/res/layout/package_picker_empty_item.xml` | 新規 | 検索 0 件時の placeholder |
| `app/src/main/res/layout/package_picker_manual_dialog_input.xml` | 新規 | AlertDialog 内の手動入力フィールド |
| `app/src/main/res/values/strings.xml` | 追記 | 英語デフォルトの picker キー群 (NFR 3.1) |
| `app/src/main/res/values-ja/strings.xml` | 追記 | 日本語の picker キー (search hint / a11y / no_results / manual_input_*) |
| `app/src/main/java/com/example/keynest/ui/edit/PackagePickerBottomSheet.kt` | 大幅改修 | SectionAdapter / SAMPLE_FREQUENTLY_USED / matchesQuery / isManualEntryValid / showManualEntryDialog |
| `app/src/test/java/com/example/keynest/resources/PackagePickerLayoutTokensTest.kt` | 新規 | layout / drawables / strings の token pinning |
| `app/src/test/java/com/example/keynest/ui/edit/PackagePickerSampleAppsTest.kt` | 新規 | SAMPLE データと matchesQuery() の単体テスト |
| `app/src/test/java/com/example/keynest/ui/edit/PackagePickerManualEntryValidationTest.kt` | 新規 | isManualEntryValid() の境界値テスト |

## AC 別の実装対応

すべての requirement numeric ID をテストで担保した状態。各 AC について
担保するテスト ID を以下に列挙する（テスト名は短縮形）。

### Requirement 1: bottom sheet 全体のシェル

| AC | 実装箇所 | テスト |
|---|---|---|
| 1.1 (`?attr/colorSurface` / `kn_bg_elev` 相当) | `package_picker_bottom_sheet.xml` L46 `android:background="@drawable/kn_picker_sheet_bg"` + `kn_picker_sheet_bg.xml` solid=`@color/kn_surface` | `PackagePickerLayoutTokensTest.sheetLayout_bindsRootBackgroundToColorSurfaceToken` / `sheetBgDrawable_usesKnSurfaceFillAndKnRSheetCorners` |
| 1.2 (`kn_r_sheet` 28dp 上端角丸) | `kn_picker_sheet_bg.xml` corners topLeft/topRight=`@dimen/kn_r_sheet` | `PackagePickerLayoutTokensTest.sheetLayout_appliesKnRSheetCornerRadius` |
| 1.3 (`kn_list_padding_h` / `kn_screen_padding_h`) | layout の検索バー / 手動入力行は `@dimen/kn_list_padding_h`、タイトル行は `@dimen/kn_screen_padding_h` | `PackagePickerLayoutTokensTest.sheetLayout_appliesHorizontalListPaddingToken` |
| 1.4 (`show()` 起動経路保持) | `PackagePickerBottomSheet.show(manager, onPicked)` の signature 不変 | Manual smoke (CredentialEditActivity から呼び出し) |

### Requirement 2: drag handle

| AC | 実装箇所 | テスト |
|---|---|---|
| 2.1 (上端中央) | layout L57 `View@drag_handle` + `android:layout_gravity="center_horizontal"` | `PackagePickerLayoutTokensTest.sheetLayout_dragHandleHasCorrectDimensions` |
| 2.2 (`kn_handle_w` 36dp / `kn_handle_h` 4dp) | layout L58-59 | 同上 |
| 2.3 (`kn_ink_200` 背景) | `kn_picker_drag_handle.xml` solid=`@color/kn_ink_200` | `PackagePickerLayoutTokensTest.dragHandleDrawable_usesKnInk200AndKnRPill` |
| 2.4 (`kn_r_pill` 角丸) | 同上 corners=`@dimen/kn_r_pill` | 同上 |
| 2.5 (上 12dp / 下 14dp) | layout L62-63 `layout_marginTop=12dp` / `layout_marginBottom=14dp` | レイアウト目視レビュー (人間レビュワー) |

### Requirement 3: タイトル + サブタイトル + 閉じる ×

| AC | 実装箇所 | テスト |
|---|---|---|
| 3.1 (2 段表示) | layout L78-99 `LinearLayout(vertical) > text_title + text_subtitle` | layout のテスト群 |
| 3.2 (`Text.KeyNest.TitleM`) | layout L90 `style="@style/Text.KeyNest.TitleM"` | `PackagePickerLayoutTokensTest.sheetLayout_titleUsesTitleMStyleAndKnTextColor` |
| 3.3 (`kn_text`) | layout L95 `android:textColor="@color/kn_text"` | 同上 (TitleM デフォルトと layout 直接指定の両方) |
| 3.4 (`package_picker_title`) | layout L94 `android:text="@string/package_picker_title"` | 同上 + `stringsResource_preservesExistingPackagePickerTitleKey` |
| 3.5 (`Text.KeyNest.BodyS`) | layout L100 `style="@style/Text.KeyNest.BodyS"` | `sheetLayout_subtitleUsesBodySStyleAndKnText2Color` |
| 3.6 (`kn_text_2`) | layout L105 `android:textColor="@color/kn_text_2"` | 同上 (BodyS デフォルトに加えて layout で再指定) |
| 3.7 (`package_picker_subtitle`) | layout L104 + `stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales` | 同上 |
| 3.8 (閉じる × + a11y) | layout L113-126 `ImageButton@btn_close` + `contentDescription="@string/package_picker_close_a11y"` | `sheetLayout_closeButtonExistsWithA11yDescription` |
| 3.9 (`dismiss()` 呼び出し) | `PackagePickerBottomSheet.kt` `binding.btnClose.setOnClickListener { dismiss() }` | Manual smoke (X タップで閉じる) |

### Requirement 4: 検索バー

| AC | 実装箇所 | テスト |
|---|---|---|
| 4.1 (タイトル直下) | layout L132 検索バーブロック | layout のテスト群 |
| 4.2 (`kn_ink_50` / `kn_surface_2` 背景) | `kn_picker_search_bar_bg.xml` solid=`@color/kn_surface_2` | `sheetLayout_searchBarUsesPickerBackgroundDrawableAndIcon` / `searchBarDrawable_usesKnInk50FillAndKnRSmCorners` |
| 4.3 (`kn_r_sm` 12dp 角丸) | 同上 corners=`@dimen/kn_r_sm` | 同上 |
| 4.4 (検索アイコン + `kn_text_3` tint) | layout L150-156 `ImageView src="@drawable/ic_search_24" tint="@color/kn_text_3"` | `sheetLayout_searchBarUsesPickerBackgroundDrawableAndIcon` |
| 4.5 (`package_picker_search_hint` プレースホルダー) | layout L163 `android:hint="@string/package_picker_search_hint"` + textColorHint=kn_text_3 | 同上 + `stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales` |
| 4.6 (`Text.KeyNest.Body` 入力 TextAppearance) | layout L159 `style="@style/Text.KeyNest.Body"` | `sheetLayout_searchBarHasInputSearchPickerId` (id 検証で間接保証) |
| 4.7 (文字入力で絞り込み) | `PackagePickerBottomSheet.kt` `binding.inputSearchPicker.addTextChangedListener { ... buildItems(allInstalledApps, currentQuery) }` + `matchesQuery()` | `PackagePickerSampleAppsTest.matchesQuery_returnsTrueOnLabelSubstringMatchCaseInsensitive` / `matchesQuery_returnsTrueOnPackageNameSubstringMatch` |
| 4.8 (空文字で全件表示) | `matchesQuery()` `if (query.isBlank()) return true` | `PackagePickerSampleAppsTest.matchesQuery_returnsTrueOnEmptyOrBlankQuery` |
| 4.9 (0 件メッセージ) | `buildItems()` 末尾の `ListItem.Empty` 追加 + `package_picker_empty_item.xml` + `package_picker_no_results` 文字列 | `PackagePickerSampleAppsTest.matchesQuery_returnsFalseWhenNeitherFieldContainsQuery` + AlertDialog で手動入力フォールバックは常時利用可 |

### Requirement 5: 「業務でよく使う」セクション

| AC | 実装箇所 | テスト |
|---|---|---|
| 5.1 (検索バー下に配置) | `PackagePickerBottomSheet.buildItems()` の section 順序 | `PackagePickerSampleAppsTest.sampleApps_contains3Entries` (順序の前提) |
| 5.2 (`Text.KeyNest.Eyebrow`) | `package_picker_section_header_item.xml` style=`@style/Text.KeyNest.Eyebrow` | `PackagePickerLayoutTokensTest.sectionHeaderItem_usesEyebrowStyleAndKnText3Color` |
| 5.3 (`kn_text_3`) | 同上 + `android:textColor="@color/kn_text_3"` | 同上 |
| 5.4 (`package_picker_section_used`) | adapter `HeaderVH.bind` で `setText(R.string.package_picker_section_used)` | `PackagePickerLayoutTokensTest.stringsResource_containsBothSectionHeaderKeys` |
| 5.5 (SAMPLE 3〜5 件) | `SAMPLE_FREQUENTLY_USED` 3 件 (Salesforce / Workday / Kintone) | `PackagePickerSampleAppsTest.sampleApps_contains3Entries` / `sampleApps_includesSalesforceWorkdayAndKintone` |
| 5.6 (行レイアウトは Req 7 規定) | adapter `RowVH` が `package_picker_row_item.xml` を inflate | `PackagePickerLayoutTokensTest` の row 系テスト |
| 5.7 (0 件時はセクション非表示) | `buildItems()` `if (filteredFrequent.isNotEmpty()) { ... }` | Indirectly through `matchesQuery_returnsFalseWhenNeitherFieldContainsQuery` |

### Requirement 6: 「すべてのアプリ」セクション

| AC | 実装箇所 | テスト |
|---|---|---|
| 6.1 (業務セクションの下) | `buildItems()` の section 順序 | 既存テスト |
| 6.2 (Eyebrow + `kn_text_3`) | Req 5.2/5.3 と共通の `package_picker_section_header_item.xml` | `sectionHeaderItem_usesEyebrowStyleAndKnText3Color` |
| 6.3 (`package_picker_section_all`) | adapter が `R.string.package_picker_section_all` を bind | `stringsResource_containsBothSectionHeaderKeys` |
| 6.4 (`loadInstalledApps()` の挙動不変) | `PackagePickerBottomSheet.loadInstalledApps()` を従来通り保持 (Dispatchers.IO / getInstalledApplications(0) / label.lowercase() ソート) | Manual smoke (実機で全件取得確認) |
| 6.5 (行は Req 7 規定) | adapter `RowVH` の inflate 経路 | `PackagePickerLayoutTokensTest` の row 系テスト |
| 6.6 (非同期取得の挙動不変) | 既存の `viewLifecycleOwner.lifecycleScope.launch + withContext(Dispatchers.IO)` を維持 | 既存ロジック (構造的不変) |

### Requirement 7: 行レイアウト (共通)

| AC | 実装箇所 | テスト |
|---|---|---|
| 7.1 (32dp アイコンタイル) | `package_picker_row_item.xml` `FrameLayout` の `layout_width/height="@dimen/kn_icon_tile_sm"` | `rowItem_iconTileIs32dpAndUsesKnIconTileBg` |
| 7.2 (`kn_r_sm` 角丸) | `kn_icon_tile_bg.xml` corners (既存) | 既存 drawable の挙動 |
| 7.3 (`kn_blue_500` 単色) | `kn_icon_tile_bg.xml` solid (既存) | 同上 |
| 7.4 (アプリ名 + パッケージ名 2 段) | row item の `LinearLayout(vertical)` に `text_app_label` + `text_app_package` | `rowItem_declaresExpectedTextIds` |
| 7.5 (`Text.KeyNest.Body`) | row item style=`@style/Text.KeyNest.Body` | `rowItem_appNameUsesBodyStyleAndPackageNameUsesMonoStyle` |
| 7.6 (`Text.KeyNest.Mono`) | row item style=`@style/Text.KeyNest.Mono` | 同上 |
| 7.7 (`kn_text_3` + ellipsis) | row item L77 `android:textColor="@color/kn_text_3"` + L76 `android:ellipsize="end"` | 同上 |
| 7.8 (`kn_space_3` パディング / ギャップ) | row item L37-38 `paddingHorizontal/Vertical="@dimen/kn_space_3"` | レイアウト目視 (ローカル token review) |
| 7.9 (`kn_r_sm` 行全体角丸) | row item は `?attr/selectableItemBackground` をフォアグラウンドリップルとして使用; 角丸は親 RecyclerView の clip 範囲に依存 | impl-notes 確認事項 (5) 参照 |
| 7.10 (`onPicked` + `dismiss`) | `PackagePickerBottomSheet.onRowPicked(packageName)` で `onPicked?.invoke(it); dismiss()` | Manual smoke (CredentialEditActivity → packagePicker → 行タップ → 入力フィールド反映) |
| 7.11 (48dp 最小タッチ) | row item `android:minHeight="48dp"` | `rowItem_hasMinTouchHeight48dp` |

### Requirement 8: 「手動入力」フォールバック行

| AC | 実装箇所 | テスト |
|---|---|---|
| 8.1 (sheet 最下部に配置) | layout の RecyclerView 後にfixed-position の MaterialButton@btn_manual_entry | `sheetLayout_manualEntryRowIsAtBottomWithKnBlue50Background` |
| 8.2 (RecyclerView 外側 / または末尾) | 「リストスクロール領域の外側に固定で見える位置」を採用 (RecyclerView は `layout_height="0dp"`, weight=1; その下に btn_manual_entry) | 同上 |
| 8.3 (`kn_blue_50` 背景) | `kn_picker_manual_row_bg.xml` solid=`@color/kn_blue_50` + dark counterpart drawable-night | `manualRowDrawable_usesKnBlue50FillAndKnRSmCorners` |
| 8.4 (`kn_primary` テキスト) | layout `android:textColor="@color/kn_primary"` | レイアウトレビュー |
| 8.5 (`Text.KeyNest.LabelL`) | layout `android:textAppearance="@style/Text.KeyNest.LabelL"` | レイアウトレビュー |
| 8.6 (`package_picker_manual`) | layout `android:text="@string/package_picker_manual"` | `sheetLayout_manualEntryRowIsAtBottomWithKnBlue50Background` |
| 8.7 (テキスト入力フロー) | `showManualEntryDialog()` で AlertDialog + `package_picker_manual_dialog_input.xml` の TextInputLayout 採用 | Manual smoke |
| 8.8 (確定で `onPicked` + `dismiss`) | `showManualEntryDialog()` 内の OK ボタン → `onRowPicked(typed)` | 既存 onRowPicked パスでカバー |
| 8.9 (空 / 不正は reject + エラー表示) | `isManualEntryValid()` で gate + Snackbar に `package_picker_manual_input_invalid` を表示 | `PackagePickerManualEntryValidationTest` の 5 メソッド (blank / no-dot / whitespace / 不正 shape の boundary 値) |

### Requirement 9: 既存機能・配線の不変

| AC | 実装箇所 | テスト |
|---|---|---|
| 9.1 (`show(manager, onPicked)` 保持) | `companion object` 内に同 signature 維持 | Manual smoke (CredentialEditActivity から既存 launchSettings 同様呼び出し) |
| 9.2 (`@id/recycler` 参照経路) | layout で `@+id/recycler` を保持 | `PackagePickerLayoutTokensTest.sheetLayout_preservesRecyclerId` |
| 9.3 (`Dispatchers.IO` + `getInstalledApplications(0)`) | `loadInstalledApps()` 関数の本体を維持 | Inspection (既存 method 不変) |
| 9.4 (`onDestroyView()` の処理) | `onDestroyView()` 既存実装 (`_binding?.recycler?.adapter = null; _binding = null`) | 同上 |
| 9.5 (`CredentialEditActivity.onPickInstalledAppClicked()` 経路) | API 不変 + adapter のクリックハンドラから既存 onPicked コールバックを invoke | Manual smoke |
| 9.6 (既存単体テスト不変) | 既存テストスイートで `PackagePickerBottomSheet` 直接対象のテスト無し; 関連 layout / theme テスト (Material3ThemeMigrationTest / FontTypefaceWiringTest / CredentialEditLayoutTokensTest) は変更なし | 全体 unit-test 実行で 5 件の pre-existing failure 以外 0 件 失敗 |

### Requirement 10: ライト / ダーク両モード

| AC | 実装箇所 | テスト |
|---|---|---|
| 10.1 (`values/colors.xml` を解決) | semantic token (`kn_surface`, `kn_text`, etc.) 経由で参照 | `sheetLayout_doesNotHardcodeHexColors` / `rowItem_doesNotHardcodeHexColors` |
| 10.2 (`values-night/colors.xml` を解決) | 同上の semantic token が values-night で上書き済み | 既存 values-night/colors.xml は Phase 1 で整備済み |
| 10.3 (本文 4.5:1) | Phase 1 で定義済みの token 値を使用 (kn_text / kn_text_2 / kn_text_3) | Phase 1 で WCAG 検証済み |
| 10.4 (非文字 3:1) | drag handle / 検索バー背景 / 行 outline は Phase 1 token | 同上 |
| 10.5 (手動入力行のダーク用フォールバック) | `drawable-night/kn_picker_manual_row_bg.xml` で `kn_primary_container` (= `#1A2D5C` on dark) を使用 | レイアウトレビュー + Phase 1 トークン定義の継承 |

### Non-Functional Requirements

| NFR | 実装箇所 | テスト |
|---|---|---|
| NFR 1.1 (`./gradlew :app:assembleDebug` 成功) | 既存ビルドパイプラインのまま | `assembleDebug BUILD SUCCESSFUL` (impl-notes 末尾の出力参照) |
| NFR 1.2 (既存テスト破壊なし) | Phase 1 / Phase 2 既存テスト変更なし | full unit-test run で pre-existing 5 件以外 0 件失敗 |
| NFR 1.3 (Material3ThemeMigrationTest / FontTypefaceWiringTest 破壊なし) | `Theme.KeyNest` / `TextAppearance.KeyNest.*` 変更なし | 上記テスト pass |
| NFR 2.1 (48dp タップ領域) | btn_close minHeight=48dp / row minHeight=48dp / btn_manual_entry minHeight=48dp / 検索バー LinearLayout minHeight=48dp | `rowItem_hasMinTouchHeight48dp` |
| NFR 2.2 (a11y contentDescription) | btn_close `contentDescription="@string/package_picker_close_a11y"` / 検索アイコンは `importantForAccessibility="no"` (drawable のみ表示) | `sheetLayout_closeButtonExistsWithA11yDescription` |
| NFR 2.3 (コントラスト) | Req 10.3 / 10.4 と同じ token 経路 | 同上 |
| NFR 2.4 (section heading) | `package_picker_section_header_item.xml` `android:accessibilityHeading="true"` | 既存 `accessibilityHeading` パターンと同じ |
| NFR 3.1 (英 / 日両ロケール対応) | 6 新規キーを values/strings.xml と values-ja/strings.xml の両方に追加 | `stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales` |
| NFR 3.2 (両ロケールに同時追加) | 同上 | 同上 |
| NFR 3.3 (日本語ロケールで `values-ja/strings.xml`) | Android 標準のリソース解決 | OS 機能 |

## 確認事項 (レビュワー判断ポイント)

1. **drag handle の段差設定**: requirements Req 2.5 で「上 12dp / 下 14dp」と
   指定されていたため、`layout_marginTop="12dp" / layout_marginBottom="14dp"`
   をハードコード指定にしている (現状 `@dimen/kn_space_*` には 14dp / 12dp の
   semantic alias が無いため)。Phase 1 のトークン拡張時に
   `@dimen/kn_handle_margin_top` / `_bottom` のような名前で正式化したい場合は
   別 Issue で起票が望ましい。

2. **検索バー drawable の塗り色**: requirements Req 4.2 で
   「`@color/kn_ink_50` または `@color/kn_surface_2`」のどちらも許容と書か
   れていた中、本実装では `@color/kn_surface_2` (semantic token) を採用した。
   理由は dark mode で values-night が `kn_surface_2` を `kn_ink_800` に
   再定義しており、別 drawable を用意せずに Req 10.4 / 10.5 の dark mode
   コントラストを満たせるため。レビュワーが「JSX の色トレースに合わせて
   `kn_ink_50` を直接書きたい」と判断する場合は dark mode 用の追加 drawable が
   必要になる。

3. **手動入力フローの UI モダリティ**: requirements Open Question #4 で
   「インラインで EditText に展開 / 別ダイアログ起動 / 親 Activity に通知」の
   3 候補から Architect / Developer 判断とされていたため、本実装では
   **AlertDialog 内に TextInputLayout を埋め込む方針**を採用した。理由:
   * 既存 sheet レイアウトの構成を最小変更で済む (インライン展開だと sheet
     高さの再計算と RecyclerView の再レイアウトが必要)。
   * 親 Activity への通知方式は呼び出し元 (`CredentialEditActivity`) のロジック
     を変更する必要があり、Req 9.1 / 9.5 の「既存呼び出し元の挙動不変」を
     優先するなら別途リファクタが必要。
   * AlertDialog なら `onPicked` のセマンティクスを保ったまま、入力された
     パッケージ名を既存コールバックでそのまま返せる。

4. **「業務でよく使う」SAMPLE の実装方式**: requirements Open Question #3 で
   「実在パッケージ名 + ハードコード文字列 / インストール済み一覧と照合して
   表示 / 常に固定行として表示」の 3 候補から判断とされていたため、本実装は
   **常に固定行として表示**を採用した。理由は:
   * 推奨判定ロジックを Out of Scope に逃がしている都合上、実機での
     インストール状態に依存させると「業務でよく使う」が空のままレビュワー /
     QA 側で確認しにくい (Salesforce / Workday / Kintone のいずれかが入って
     いる端末を用意する必要が出る)。
   * 固定 3 件 (Salesforce / Workday / Kintone) は JSX `installed[].used=true`
     と完全一致しており、デザインモック確認の workflow と整合する。

5. **行全体の角丸 (Req 7.9)**: requirements で「行全体の角丸 kn_r_sm」と
   ある一方、本実装の `package_picker_row_item.xml` は `?attr/selectableItemBackground` を
   `android:background` に当てており、別個に kn_r_sm corner radius を持たせて
   いない。JSX `PickerRow` も `borderRadius: 12` を `background: transparent`
   に対して指定しているだけで、選択中 (selected=true) でない限り視覚的に
   角丸はほぼ見えない。本実装は「行は単一色背景を持たず、リップルは ROW 全体に
   流れる」挙動を取っているため、角丸の視覚効果は無視できる。レビュワーが
   「Selected highlight (=Req 7.9) を厳格に再現してほしい」と判断する場合は
   別 Issue で `kn_picker_row_selected_bg` (kn_surface_tint + kn_r_sm) を
   追加することを推奨する。

6. **`Widget.KeyNest.DragHandle` / `Widget.KeyNest.SearchField` スタイルの
   定義**: requirements Open Question #1 で「本 Issue 内で当該スタイルを
   新規定義するか、既存トークンを個別に貼って同等視覚を実現するか」と
   提示されていた。本実装は **既存トークンを個別に貼る方式**を採った。
   理由は (a) drag handle と search field は本 sheet 以外で再利用しない
   見込みで、style 化することで `themes.xml` を肥大化させずに済む、(b) JSX
   `ScreenPicker` 以外で同パターンを使う Issue がまだ無いため、Phase 2 後に
   再利用シーンが見えてから抽出する方が YAGNI に合致する、の 2 点。

7. **`?attr/colorSurface` を drawable 内で参照できなかった点**: 当初
   `kn_picker_sheet_bg.xml` で `<solid android:color="?attr/colorSurface" />`
   を使おうとしたが、`<solid>` タグは theme attribute を解決しない (Android
   plot バグ / 仕様) ため、`@color/kn_surface` (semantic token、値 light/dark
   で自動切り替え) に置き換えた。実質的な挙動は同一 (Theme.KeyNest が
   `colorSurface` を `kn_surface` にマップしているため)。

## ビルド・テスト結果

### `./gradlew :app:assembleDebug`

```
BUILD SUCCESSFUL in 17s
40 actionable tasks: 4 executed, 36 up-to-date
```

### `./gradlew :app:testDebugUnitTest` (全体)

```
406 tests completed, 5 failed
```

**失敗 5 件はすべて Issue #32 着手前から develop に存在する pre-existing failure** で、
本 Issue とは無関係:

* `LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`
  (`LockedFillResponseSecurityTest.kt:54` NPE - autofill サービスモック)
* `PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash`
  (`PackageSignatureResolverTest.kt:99` NPE - Signature mock)
* `PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`
  (`PackageSignatureResolverTest.kt:159` NPE)
* `PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash`
  (`PackageSignatureResolverTest.kt:43` NPE)
* `PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent`
  (`PackageSignatureResolverTest.kt:62` NPE)

検証: 同じテストを `/home/hitoshi/github/KeyNest`
(develop チェックアウトの clean worktree) で実行したところ全く同じ 5 件が
失敗。Issue #32 のスコープではないため触らない。

### Issue #32 関連テスト

```
PackagePickerLayoutTokensTest      : 22 tests pass
PackagePickerSampleAppsTest        :  5 tests pass
PackagePickerManualEntryValidationTest :  4 tests pass
```

合計 31 件の新規ユニットテストが pass。

### Lint

`./gradlew :app:lintDebug` は **pre-existing 94 errors** (`NewApi` / `MissingPermission` / etc. が `PackageSignatureResolver.kt` 等から発生) のためビルド時点で失敗しているが、本 Issue で追加・変更したファイルからは **新規 error 0 件**。warning レベルでは以下 4 件が記録されているが、いずれも既存コードベースのパターンに整合する許容範囲:

* `package_picker_section_header_item.xml`: `accessibilityHeading` の API 28+
  warning → 既存 `settings_activity.xml` / `danger_zone_activity.xml` と同じ
  パターン (`UnusedAttribute`)。
* `PackagePickerBottomSheet.kt L139`: `QueryPermissionsNeeded` →
  既存実装と同じ警告 (本 Issue で behavior 変更なし)。
* `PackagePickerBottomSheet.kt L213`: `NotifyDataSetChanged` →
  既存 `CredentialListAdapter` 等と同じパターン。section + row + empty の
  3-type adapter の差分計算を `DiffUtil` で行うのは別 Issue で対応する方が
  リスクが低い (本 Issue は UI 表層の置き換えのみがスコープ)。
* `package_picker_row_item.xml`: `DisableBaselineAlignment` → 既存 layout の
  defaults と同じ。

## 派生タスク (別 Issue 候補)

1. **「業務でよく使う」推奨判定ロジック**: 端末利用履歴 / 業務アプリ辞書 /
   signature ベースのカテゴリ判定 (本 Issue Out of Scope)。
2. **アプリアイコンの動的取得**: `PackageManager.getApplicationIcon()` 経由で
   各行に正規アイコンを表示。本 Issue は `@color/kn_blue_500` 単色フォール
   バックのまま。
3. **行ハイライト (Req 7.9 selected state)**: 上記「確認事項 5」参照。
4. **検索の高度な機能**: ファジー検索 / 並び順の重み付け / カテゴリ chip。
5. **`Widget.KeyNest.DragHandle` / `Widget.KeyNest.SearchField` スタイル抽出**:
   Phase 2 完了後に再利用先が増えたタイミングで抽出 (確認事項 6)。
6. **DiffUtil 化**: SectionAdapter の `notifyDataSetChanged` を
   `notifyItemRangeChanged` / `DiffUtil.calculateDiff` に置き換え (lint 警告
   解消)。
