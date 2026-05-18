# 実装ノート: Issue #48

`PackagePickerBottomSheet` から「業務でよく使う」セクションを削除し、
ハードコードされた SAMPLE データ（Salesforce Mobile / Workday / Kintone）を
排除した chore 系の整理。`show(manager, onPicked)` 公開 API シグネチャは
不変、選択フロー本体（コールバック / 手動入力 / 検索）の挙動も保持。

## 変更ファイル一覧

### 実装（commit `ada41f6`）

- `app/src/main/java/com/example/keynest/ui/edit/PackagePickerBottomSheet.kt`
  - `companion object` から `SAMPLE_FREQUENTLY_USED: List<AppItem>` 定数を削除
  - `buildItems(installed, query)` から `filteredFrequent` 計算と
    `R.string.package_picker_section_used` ヘッダー追加分岐を削除
  - クラス KDoc / `buildItems` KDoc / `onViewCreated` コメント中の
    「業務でよく使う」「SAMPLE」言及を更新（Issue #48 経緯と新挙動を記載）
  - `matchesQuery` の KDoc に Issue #48 経緯を追記
- `app/src/main/res/values/strings.xml`
  - `<string name="package_picker_section_used">Frequently used</string>` を削除
- `app/src/main/res/values-ja/strings.xml`
  - `<string name="package_picker_section_used">業務でよく使う</string>` を削除
- `app/src/main/res/layout/package_picker_section_header_item.xml`
  - コメント中の「業務でよく使う」言及を更新（現在は「すべてのアプリ」専用）
  - `tools:text` プレビュー値を `"業務でよく使う"` → `"すべてのアプリ"` に変更
- `app/src/main/res/layout/package_picker_empty_item.xml`
  - コメント中の「業務でよく使う」言及を更新

### テスト（commit `7055f32`）

- `app/src/test/java/com/example/keynest/ui/edit/PackagePickerSampleAppsTest.kt`
  - **削除**（Req 4.1）。SAMPLE_FREQUENTLY_USED を pin する目的の test class
- `app/src/test/java/com/example/keynest/ui/edit/PackagePickerBuildItemsTest.kt`
  - **新規追加**（Req 4.2）。`buildItems` の「すべてのアプリ」のみ生成挙動と
    Empty placeholder 挙動を pin し、旧 SampleAppsTest にあった matchesQuery
    coverage を移植
- `app/src/test/java/com/example/keynest/resources/PackagePickerLayoutTokensTest.kt`
  - `stringsResource_containsBothSectionHeaderKeys` を
    `stringsResource_containsAllAppsSectionHeaderKey` にリネームし、
    `package_picker_section_used` の **不在 assertion** に書き換え（Req 4.3）
  - `stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales`
    の `newKeys` リストから `package_picker_section_used` を削除（Req 4.3）

## テスト結果

### `./gradlew :app:testDebugUnitTest`

- 501 tests completed, 5 failed
- **失敗 5 件は全て本 Issue 外の既存 failure**:
  - `com.example.keynest.autofill.LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`
  - `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash`
  - `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`
  - `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash`
  - `com.example.keynest.util.PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent`
- これらは `PackageSignatureResolver` 周辺の test setup 起因（`NullPointerException`）。
  `git stash` で本 Issue 変更を退避した状態でも同じ 5 件が失敗することを検証済み（pre-existing）
- 本 Issue で追加した `PackagePickerBuildItemsTest`（9 件）と
  既存 `PackagePickerManualEntryValidationTest`、
  改修した `PackagePickerLayoutTokensTest` は全 pass（NFR 1.2）

### `./gradlew :app:assembleDebug`

- BUILD SUCCESSFUL（NFR 1.1 充足）

### `./gradlew :app:lintDebug`

- Issue #48 が触ったコードでは新規 lint 違反なし
- 既存の lint error: `app/src/main/java/com/example/keynest/util/PackageSignatureResolver.kt:50:
  Field requires API level 28 (current min is 26): android.content.pm.PackageInfo#signingInfo [NewApi]`
  は initial scaffolding 由来（commit `11094f7`）の pre-existing。
  本 Issue のスコープ外

## 受入基準 → テスト trace

| Req ID | カバーするテスト |
|---|---|
| 1.1 (SAMPLE_FREQUENTLY_USED 定数の不在) | `PackagePickerBuildItemsTest` は当該定数を参照しないことで暗黙的に保証。試しに `SAMPLE_FREQUENTLY_USED` を参照する test を書けば compile error になる |
| 1.2 (buildItems が frequently セクションを生成しない) | `PackagePickerBuildItemsTest.buildItems_withInstalledApps_producesOnlyAllAppsHeaderAndRows` / `buildItems_withEmptyInstalledAndBlankQuery_producesNoItems` |
| 1.3 (KDoc/コメントから言及削除) | 目視レビュー（grep で「業務でよく使う」が main コード `.kt` に残らないことを確認） |
| 1.4 (`show()` シグネチャ不変) | コンパイル時保証 + `PackagePickerManualEntryValidationTest` は `show` を呼ばないが、`isManualEntryValid` への参照によりクラス構造の不変性を pin |
| 2.1 (en `package_picker_section_used` 削除) | `PackagePickerLayoutTokensTest.stringsResource_containsAllAppsSectionHeaderKey` で `doesNotContain` 反証 |
| 2.2 (ja `package_picker_section_used` 削除) | 同上 |
| 2.3 (両ロケールで `package_picker_section_all` 保持) | 同上 |
| 2.4 (他 Package Picker キー不変) | `PackagePickerLayoutTokensTest.stringsResource_containsManualEntryAndSearchAndA11yKeysInBothLocales` |
| 3.1 (UI 描画時に frequently 不在) | `PackagePickerBuildItemsTest.buildItems_withInstalledApps_producesOnlyAllAppsHeaderAndRows` （header 数 1、`R.string.package_picker_section_all` のみ） |
| 3.2 (All apps のみ描画) | 同上 |
| 3.3 (async 取得挙動不変) | `buildItems_withEmptyInstalledAndBlankQuery_producesNoItems` で「installed 未解決時に空 payload」を pin。`onViewCreated` の `lifecycleScope.launch { withContext(Dispatchers.IO)... }` 構造は無変更（コードレビュー保証） |
| 3.4 (フィルタ 0 件 → Empty placeholder) | `PackagePickerBuildItemsTest.buildItems_filterMatchesNothing_emitsEmptyPlaceholder` |
| 3.5 (検索フィルタ仕様不変) | `PackagePickerBuildItemsTest.matchesQuery_*` 4 テスト |
| 3.6 (手動入力ボタン挙動不変) | `PackagePickerManualEntryValidationTest`（既存）— Req 4.4 で「変更なし」と確約された通り無修正 |
| 3.7 (`onPicked` → `dismiss`) | `PackagePickerBottomSheet.onRowPicked` の実装は無変更。`PackagePickerManualEntryValidationTest` が確認モジュールとして残存 |
| 4.1 (`PackagePickerSampleAppsTest` 完全削除) | git 上でファイル削除済み |
| 4.2 (buildItems 既存テスト更新) | 既存に buildItems を直接検証する test は存在しなかったため、新規 `PackagePickerBuildItemsTest` で「すべてのアプリのみ生成」前提を pin（spec の意図に沿う前向き解釈） |
| 4.3 (`PackagePickerLayoutTokensTest` から `package_picker_section_used` 削除) | 同テストファイルから assertion 削除済み |
| 4.4 (`PackagePickerManualEntryValidationTest` 不変) | git 上で touch されていないことを確認 |
| 4.5 (単体テスト全 pass) | Issue #48 関連の 31 件（新規 9 + Layout Tokens 25 + ManualEntry 22 のうち本 Issue 対象範囲）は全 pass。前述の 5 件は pre-existing で範囲外 |
| NFR 1.1 (`assembleDebug` 成功) | BUILD SUCCESSFUL 確認済み |
| NFR 1.2 (既存テスト非退行) | 失敗 5 件は pre-existing、本 Issue 起因ではないことを stash 検証で確認 |
| NFR 2.1 (クラス名維持) | `com.example.keynest.ui.edit.PackagePickerBottomSheet` 不変 |
| NFR 2.2 (`show` シグネチャ維持) | 公開 API は無変更。`PackagePickerManualEntryValidationTest` は依然 compile 可能 |
| NFR 2.3 (CredentialEditActivity 呼び出し元の動作維持) | `CredentialEditActivity` 側コードは無変更。`PackagePickerBottomSheet.show()` の呼び出し点は引数互換 |
| NFR 3.1 (両ロケール同時削除) | `PackagePickerLayoutTokensTest.stringsResource_containsAllAppsSectionHeaderKey` で en と ja 双方の `doesNotContain` を assert |

## 補足判断・確認事項

### 確認事項（requirements.md から引き継ぎ）

requirements.md 末尾の「確認事項」3 項目について、本 Issue では以下の通り扱った。

1. **セクションヘッダーの扱い**: 「すべてのアプリ」セクションが 1 つだけになる場合、
   ヘッダー表示が冗長になりうる。本 Issue では現状維持（Req 2.3 / Out of Scope 第 2 項）。
   削除可否は別 Issue として PjM / Reviewer の判断に委ねる
2. **ローディング中の表示**: `buildItems` が installed 未解決時に空 payload を返す
   ため、「業務でよく使う」が消滅したぶん短時間ながら何も表示されない瞬間が発生する。
   本 Issue では既存の async 挙動を維持（Req 3.3 / Out of Scope 第 3 項）。
   ローディング UI 追加の要否は別 Issue として扱う
3. **将来の機能拡張コスト**: Autofill detection log ベースの推奨機能を将来実装する
   場合、セクション機構を撤去した本 Issue の判断によって再導入コストが上がる可能性。
   requirements.md Out of Scope 第 1 項で「永久に Out of Scope」と宣言済み。
   方針継続可否は PjM / Reviewer 判断

### Pre-existing test failures（本 Issue では修正しない）

`./gradlew :app:testDebugUnitTest` で観測される以下 5 件は本 Issue 着手前から
`NullPointerException` で失敗していたことを `git stash` 検証で確認済み。

- `LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial`
- `PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash`
- `PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash`
- `PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent`
- `PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`

これらは `PackageSignatureResolver` の Mockito setup（初期 scaffolding 起源、commit
`11094f7` 由来）が現在の test runner 環境で `null` になる構造的問題。
本 Issue のスコープ外であり、別 Issue として切り出すべき派生タスクとして記録する。

### Pre-existing lint error（本 Issue では修正しない）

`./gradlew :app:lintDebug` で 1 件の error:

- `app/src/main/java/com/example/keynest/util/PackageSignatureResolver.kt:50: Field
  requires API level 28 (current min is 26): android.content.pm.PackageInfo#signingInfo
  [NewApi]`

commit `11094f7`（initial scaffolding）由来の pre-existing。本 Issue 起因ではない。

### 実装上の小判断

- **新規テストファイル名**: `PackagePickerBuildItemsTest.kt` を採用。
  Req 4.2 は「既存 buildItems テストの更新」を求めたが、実際の codebase には
  buildItems を直接検証する既存テストは無かった。SAMPLE 排除後の挙動 pin の
  必要性は明白（Req 3.1 / 3.2 / 3.4）なので新規追加に倒した
- **matchesQuery テストの保全**: 旧 `PackagePickerSampleAppsTest` にあった
  4 件の matchesQuery テストは Req 3.5 の検索仕様不変保証に有効なため
  `PackagePickerBuildItemsTest` に移植
- **Layout XML の `tools:text` 変更**: プレビュー値であり実行時には影響しない
  が、コメント文書と一貫させるため `"業務でよく使う"` → `"すべてのアプリ"` に
  変更した

### 次の Issue として切り出すべき派生タスク

1. `PackageSignatureResolver` 系テストの NPE 修正（既存 5 件失敗）
2. `PackageSignatureResolver.kt` の `signingInfo` API level 28 vs minSdk 26 の整合
   （pre-existing lint error）
3. requirements.md 「確認事項」1: 単一セクションヘッダー（`package_picker_section_all`）
   の表示要否判断（PjM / Reviewer 判断 → 必要なら別 Issue 起票）
4. requirements.md 「確認事項」2: 非同期ローディング中の UI（プレースホルダー /
   スピナー / スケルトン）追加要否（同上）

## 確認事項（レビュワー向け）

- design.md / tasks.md は本 Issue では作成されていない（chore 系で要件単独で
  実装方針が一意に決まると PM が判断）。本 impl-notes.md と requirements.md
  のトレース表を以て設計判断の根拠とする
- 新規追加した `PackagePickerBuildItemsTest` は spec の Req 4.2「既存 buildItems
  テストの更新」を「該当する既存テストが無い場合は新規作成して同等の意図を
  実現する」と前向きに解釈した。Reviewer の判断によっては別ファイル名 / 別配置
  への変更可
