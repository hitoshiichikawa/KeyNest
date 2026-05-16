# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T06:52:47Z -->

## Reviewed Scope

- Branch: claude/issue-48-impl-chore-picker-packagepickerbottomsheet-sa
- HEAD commit: c9b26701654567a0e21ed30ae02a855e8844dab0
- Compared to: develop..HEAD

差分構成:

- 実装: `PackagePickerBottomSheet.kt`（`SAMPLE_FREQUENTLY_USED` 定数および
  `buildItems` の frequently セクション分岐を削除、KDoc / コメント更新）
- リソース: `values/strings.xml` / `values-ja/strings.xml` から
  `package_picker_section_used` を削除
- レイアウト xml: `package_picker_section_header_item.xml` / `package_picker_empty_item.xml`
  のコメントおよび `tools:text` プレビュー値を更新
- テスト: `PackagePickerSampleAppsTest.kt` を削除、`PackagePickerBuildItemsTest.kt`
  を新規追加、`PackagePickerLayoutTokensTest.kt` を更新
- spec: `requirements.md` / `impl-notes.md` を追加

`tasks.md` / `design.md` は本 Issue では作成されていない（chore で要件単独で
実装方針が一意に決まる、と impl-notes.md に明記）。そのため `_Boundary:_`
アノテーションは存在しないが、requirements.md がスコープを Package Picker 周辺に
明示しており、差分はその implicit な境界に収まっている。

## Verified Requirements

- 1.1 — `SAMPLE_FREQUENTLY_USED` 定数は `PackagePickerBottomSheet` から削除。
  grep で `val SAMPLE_FREQUENTLY_USED` / `SAMPLE_FREQUENTLY_USED =` の定義行が
  存在しないことを確認。`PackagePickerBuildItemsTest` は当該定数を参照しない
  ことで暗黙保証
- 1.2 — `buildItems` から `filteredFrequent` 計算と
  `R.string.package_picker_section_used` 分岐を削除。
  `PackagePickerBuildItemsTest.buildItems_withInstalledApps_producesOnlyAllAppsHeaderAndRows`
  が `headerCount == 1` を assert
- 1.3 — クラス KDoc / `buildItems` KDoc / `companion object` コメントから
  「業務でよく使う」セクション存在前提の記述が削除されている。残存する
  「業務でよく使う」言及は全て「Issue #48 で削除した」旨を説明する経緯コメント
  のみ（grep で確認）
- 1.4 — `show(manager: FragmentManager, onPicked: (String) -> Unit)` 行が
  `PackagePickerBottomSheet.kt:331` に存在。差分でも変更されていない
- 2.1 — `values/strings.xml` から `<string name="package_picker_section_used">`
  が削除されている。`PackagePickerLayoutTokensTest.stringsResource_containsAllAppsSectionHeaderKey`
  の `doesNotContain("name=\"package_picker_section_used\"")` で反証
- 2.2 — `values-ja/strings.xml` から同キーが削除されている。同上テストの
  ja 側 assertion で反証
- 2.3 — 両ロケールで `package_picker_section_all` が残存することを同テストで
  assert
- 2.4 — `stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales`
  で `package_picker_subtitle` / `package_picker_search_hint` /
  `package_picker_section_all` / `package_picker_manual` /
  `package_picker_close_a11y` / `package_picker_no_results` /
  `package_picker_manual_input_*` 系の保持を assert（リストから
  `package_picker_section_used` のみ除外）
- 3.1 — `PackagePickerBuildItemsTest.buildItems_withInstalledApps_producesOnlyAllAppsHeaderAndRows`
  が「Header は 1 個のみ、`R.string.package_picker_section_all` であること」を assert
- 3.2 — 同上テストが All apps セクション 1 個（header + rows）のみ生成を確認
- 3.3 — `onViewCreated` の `lifecycleScope.launch { withContext(Dispatchers.IO) ... }`
  構造は差分で変更されていない。`buildItems_withEmptyInstalledAndBlankQuery_producesNoItems`
  が「installed 未解決時に空 payload」を pin
- 3.4 — `buildItems_filterMatchesNothing_emitsEmptyPlaceholder` が `ListItem.Empty`
  単独 emission を assert
- 3.5 — `matchesQuery_returnsTrueOnLabelSubstringMatchCaseInsensitive` /
  `matchesQuery_returnsTrueOnPackageNameSubstringMatch` /
  `matchesQuery_returnsFalseWhenNeitherFieldContainsQuery` /
  `matchesQuery_returnsTrueOnEmptyOrBlankQuery` の 4 件で従来仕様を継承（旧
  SampleAppsTest から移植）
- 3.6 — `PackagePickerManualEntryValidationTest` は本 PR で touch されていない
  （diff にファイルが現れない）ことを確認。手動入力ボタンの起動・バリデーション・
  `onPicked` コールバック挙動は不変
- 3.7 — `onRowPicked` 実装は差分で変更されていない（`onPicked(packageName)` →
  `dismiss()` の順序が保持される）。`PackagePickerManualEntryValidationTest`
  が同パスのクラス構造を pin
- 4.1 — `PackagePickerSampleAppsTest.kt` がファイルごと削除されている（diff の
  `deleted file mode 100644` / `-93` 行で確認）
- 4.2 — 既存 codebase に buildItems を直接検証するテストは存在しなかったため、
  spec の意図に沿って `PackagePickerBuildItemsTest.kt` を新規作成し「All apps
  セクションのみ生成」前提を pin。impl-notes.md の Trace 表で明記。spec 文言は
  「既存テストの更新」だが、対応する既存テストが無い場合の前向き解釈として妥当
- 4.3 — `PackagePickerLayoutTokensTest.kt` から `package_picker_section_used` の
  存在 assertion が削除され、新たに `doesNotContain` 反証が追加されている。
  `stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales` の
  `newKeys` リストからも当該キーが除去
- 4.4 — `PackagePickerManualEntryValidationTest` は差分に現れない（無変更）
- 4.5 — impl-notes.md に「`PackagePickerBuildItemsTest` の 9 件、改修した
  `PackagePickerLayoutTokensTest`、既存 `PackagePickerManualEntryValidationTest`
  は全 pass」と記載。残る 5 件失敗は `PackageSignatureResolver` / `LockedFillResponseSecurityTest`
  系で、Developer が `git stash` 検証により本 Issue 着手前から失敗していたことを
  確認済み（NFR 1.2 が要求する「変更しなかった既存テスト」の範囲外）
- NFR 1.1 — impl-notes.md に `BUILD SUCCESSFUL` 記載
- NFR 1.2 — pre-existing 5 件失敗は本 Issue 起因ではないことを stash 検証済み
- NFR 2.1 — `com.example.keynest.ui.edit.PackagePickerBottomSheet` クラス名不変
- NFR 2.2 — `show(manager, onPicked)` シグネチャ不変（上記 1.4 と重複確認）
- NFR 2.3 — `CredentialEditActivity.kt` は本 PR で変更されていない（`git diff`
  で当該ファイルが現れない）
- NFR 3.1 — en / ja 両ロケールで `package_picker_section_used` が同時削除され、
  `stringsResource_containsAllAppsSectionHeaderKey` が双方の `doesNotContain` で
  反証

## Findings

なし

## Summary

Issue #48 のすべての numeric Requirement / NFR について、差分・新規テスト・
impl-notes のテスト実行結果（Developer 報告）の三点で観測可能なカバレッジを
確認した。`SAMPLE_FREQUENTLY_USED` 定数および「業務でよく使う」セクション関連
分岐は実装・リソース・テストすべてのレイヤから一貫して削除されている。
`show()` シグネチャ / `CredentialEditActivity` 呼び出し元 / 手動入力フロー /
非同期取得構造はいずれも未変更で、`PackagePickerManualEntryValidationTest` も
無修正のまま保持されている。boundary 逸脱・AC 未カバー・missing test のいずれ
にも該当しない。

RESULT: approve
