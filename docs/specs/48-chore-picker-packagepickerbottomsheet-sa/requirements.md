# Requirements Document

## Introduction

`PackagePickerBottomSheet` の「業務でよく使う」セクションは Issue #32 で design mock 整合のために実装され、Salesforce Mobile / Workday / Kintone の 3 件をハードコードした SAMPLE データを常時表示している。当該セクションには以下の構造的問題がある:

- 端末に該当アプリが未インストールでも常時表示される
- ユーザー固有の業務環境を反映していない
- 推奨判定アルゴリズム（端末利用履歴・業務アプリ辞書・signature ベース判定など）は Android API 制約下で実用的に成立しない

このため、セクション機構そのものを削除し、`PackagePickerBottomSheet` は「すべてのアプリ」セクションのみで構成する状態に整理する。これは UI 上のハードコード sample を排除する chore 系の変更であり、選択フロー本体（コールバック / 手動入力 / 検索）の挙動は変更しない。

## Requirements

### Requirement 1: SAMPLE_FREQUENTLY_USED 定数とセクション生成ロジックの除去

**Objective:** As a メンテナ, I want ハードコードされた SAMPLE データとそれを参照するセクション生成分岐がコード上から消えていること, so that 実態を反映しない固定データに依存した UI 表示が再発しない状態にできる

#### Acceptance Criteria

1. The PackagePickerBottomSheet shall `SAMPLE_FREQUENTLY_USED` に相当する固定アプリ一覧の定数を保持しない
2. The PackagePickerBottomSheet shall リスト構築ロジック（現行の `buildItems` に相当する処理）において「業務でよく使う」セクションのヘッダー行および行アイテムを生成しない
3. The PackagePickerBottomSheet shall KDoc / コメントから「業務でよく使う」セクションの存在を前提とした記述を削除する
4. The PackagePickerBottomSheet shall `show(manager, onPicked)` 公開 API のシグネチャを変更しない

### Requirement 2: 文字列リソースの整理

**Objective:** As a メンテナ, I want 表示されなくなったセクションヘッダー用の文字列リソースが消え、残るセクションのリソースは保持されること, so that 未使用リソースが lint 警告や混乱を生まない状態にできる

#### Acceptance Criteria

1. The string resource catalog shall `package_picker_section_used` キーを英語ロケール (`values/strings.xml`) から削除する
2. The string resource catalog shall `package_picker_section_used` キーを日本語ロケール (`values-ja/strings.xml`) から削除する
3. The string resource catalog shall `package_picker_section_all` キーを英語・日本語両ロケールに保持する
4. The string resource catalog shall 他の Package Picker 関連キー（`package_picker_title` / `package_picker_subtitle` / `package_picker_search_hint` / `package_picker_manual` / `package_picker_close_a11y` / `package_picker_no_results` / `package_picker_manual_input_title` / `package_picker_manual_input_hint` / `package_picker_manual_input_invalid`）を変更しない

### Requirement 3: Package Picker 画面の表示挙動

**Objective:** As a エンドユーザー, I want アプリ選択シートを開いたときに「業務でよく使う」セクションが一切表示されず、「すべてのアプリ」のみが提示されること, so that 自分の端末状態に即した一覧から目的のアプリを選べる

#### Acceptance Criteria

1. When ユーザーが Package Picker を開いたとき, the PackagePickerBottomSheet shall 「業務でよく使う」セクションのヘッダーおよびその配下行を一切描画しない
2. When インストール済みアプリ一覧の取得が完了したとき, the PackagePickerBottomSheet shall 「すべてのアプリ」セクションのみをリストに描画する
3. While インストール済みアプリ取得が非同期で進行中である場合, the PackagePickerBottomSheet shall 既存の非同期取得挙動（取得完了後にリスト差し替え）を変更しない
4. If 検索クエリの絞り込みによって「すべてのアプリ」セクションの行数が 0 件になった場合, the PackagePickerBottomSheet shall 既存の Empty placeholder（`ListItem.Empty` 相当）を表示する
5. When ユーザーが検索バーに文字を入力したとき, the PackagePickerBottomSheet shall 既存のフィルタ仕様（label / packageName に対するケース非依存の部分一致）を変更しない
6. The PackagePickerBottomSheet shall 「手動入力」ボタンの押下挙動（手動入力ダイアログの起動・バリデーション・確定時の `onPicked` コールバック呼び出し）を変更しない
7. When ユーザーが行を押下したとき, the PackagePickerBottomSheet shall 既存の `onPicked(packageName)` コールバックを 1 回だけ呼び出し、続けて `dismiss()` を呼び出す挙動を維持する

### Requirement 4: 既存テストの整理

**Objective:** As a メンテナ, I want 削除対象セクションを前提とした既存テストが残らず、残るテストは新しい挙動と整合していること, so that テストスイートが現行仕様を正しく検証している状態にできる

#### Acceptance Criteria

1. The test suite shall `PackagePickerSampleAppsTest` を完全に削除する
2. The test suite shall `buildItems` を検証する既存テストから「業務でよく使う」セクションの header / row 生成に関する assertion を削除し、「『すべてのアプリ』セクションのみが生成される」前提に更新する
3. The test suite shall リソース層テスト（`PackagePickerLayoutTokensTest` 相当）から `package_picker_section_used` キーの存在 assertion を削除する
4. The test suite shall `PackagePickerManualEntryValidationTest` に対する assertion 変更を行わず、既存どおり成功させる
5. When 変更後の単体テストスイートを実行したとき, the Android test runner shall 全テストを成功で終了させる

## Non-Functional Requirements

### NFR 1: ビルドと既存テストの非退行

1. When `./gradlew :app:assembleDebug` を本変更後に実行したとき, the Android build pipeline shall 当該タスクを成功（exit code 0）で終了する
2. When `./gradlew :app:testDebugUnitTest` を本変更後に実行したとき, the Android test runner shall 本 Issue で変更しなかった既存テストを全て成功させる

### NFR 2: API および呼び出し元の不変

1. The PackagePickerBottomSheet shall 既存クラス名 `com.example.keynest.ui.edit.PackagePickerBottomSheet` を維持する
2. The PackagePickerBottomSheet shall `companion object` の `show(manager: FragmentManager, onPicked: (String) -> Unit)` シグネチャを維持する
3. When `CredentialEditActivity` の既存呼び出し元から `PackagePickerBottomSheet.show()` が呼び出されたとき, the PackagePickerBottomSheet shall 従来どおりシートを表示し、選択結果を呼び出し元の入力フィールドに反映できる

### NFR 3: ローカライズの整合

1. The string resource catalog shall 削除する `package_picker_section_used` キーを英語・日本語の両ロケールから同時に除去する（片側ロケールへの残存を許容しない）

## Out of Scope

- 推奨判定アルゴリズム（端末利用履歴・業務アプリ辞書・signature 判定など）の実装 — 永久に Out of Scope と宣言する
- 「すべてのアプリ」セクションが単一になる状況でのセクションヘッダー（`package_picker_section_all`）削除可否の判断 — 本 Issue では現状維持。要否は別 Issue で扱う（確認事項 1）
- インストール済みアプリ取得中のローディング表示（プレースホルダー / スピナー / スケルトン UI の追加）— 本 Issue では既存の非同期挙動をそのまま維持する（確認事項 2）
- Autofill detection log に基づく推奨機能（将来想定）の追加 — 本 Issue ではセクション機構ごと削除する（確認事項 3）
- Issue #46 で対応中のアイコン表示 hotfix
- `PackagePickerBottomSheet` の視覚仕様（drag handle / タイトル / 検索バー / 行レイアウト / 手動入力行のスタイル）の変更 — Issue #32 で完了済みであり、本 Issue は表示挙動の整理に限定する
- `PackagePickerManualEntryValidationTest` の仕様変更
- `loadInstalledApps()` の挙動変更（`PackageManager.getInstalledApplications(0)` / `Dispatchers.IO` / `label.lowercase()` 昇順ソート）

## Open Questions

なし（Issue 本文の「確認事項」3 件は本要件の Out of Scope セクションに整理済み。本 Issue のスコープ内で人間判断を要する曖昧点は残っていない）

## 確認事項（Issue 本文より引き継ぎ）

以下は Issue 本文の「確認事項」セクションを引き継いだもので、本 Issue では現状判断を要件に確定済み。レビュワーが別 Issue 化の要否を判断する材料として残す。

1. **セクションヘッダーの扱い**: 「すべてのアプリ」セクションが 1 つだけになる場合、ヘッダー表示（`package_picker_section_all`）が冗長になりうる。本 Issue では現状維持（Requirement 2.3 / Out of Scope）。削除可否は別 Issue として起票するかをレビュワーが判断する
2. **ローディング中の表示**: インストール済みアプリ読み込み中は空リストとなり、「業務でよく使う」セクション消滅により短時間ながら何も表示されない時間が発生しうる。本 Issue では既存の非同期挙動を維持（Requirement 3.3 / Out of Scope）。ローディング UI 追加の要否は別 Issue として扱う
3. **将来の機能拡張コスト**: 将来的に Autofill detection log ベースの推奨機能を実装する場合、本 Issue でセクション機構ごと削除する判断によって再導入コストが上がる可能性がある。本 Issue では「永久に Out of Scope」とする方針を採用する（Out of Scope 第 1 項）。方針の継続可否はレビュワーが判断する
