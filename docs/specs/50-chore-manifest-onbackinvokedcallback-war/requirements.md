# Requirements Document

## Introduction

Android 13 (API 33+) で導入された predictive back gesture を KeyNest アプリ上で有効化する。
具体的には `AndroidManifest.xml` の `<application>` 要素に `android:enableOnBackInvokedCallback="true"`
を宣言し、アプリ起動時に logcat に出力される `OnBackInvokedCallback is not enabled` warning を
解消する。これにより、Android 14 以降の OS ベース戻る操作 UX（端末左右端からのスワイプ時に
直前画面のプレビューが表示される機能）にアプリが正式対応していることを宣言できる状態にする。

本 Issue は manifest 属性の追加と、既存 Activity が `onBackPressed()` を override している場合の
新パターンへの移行に範囲を限定し、predictive back gesture のカスタムアニメーション
（`OnBackAnimationCallback` 等）は対象外とする。

なお、本 Issue 起票時点でリポジトリを Grep した結果、`onBackPressed` を override している
Kotlin / Java ファイルは検出されていない（`onBackPressed` / `BackPressedCallback` /
`backPressedDispatcher` / `onBackInvoked` のいずれの語も既存コード中に存在しない）。
そのため Requirement 2.3 は実質的に「該当 0 件であることの確認」となる見込みだが、
本要件としては「もし将来 override が混入していた場合の移行義務」を明文化しておく。

## Scope

### 対象ファイル

| 区分 | 対象 |
| --- | --- |
| Manifest | `app/src/main/AndroidManifest.xml` |
| Kotlin | `onBackPressed()` を override している Activity（現時点で該当 0 件、Developer が再確認する） |

### 対象画面（戻る操作の挙動を維持する範囲）

1. クレデンシャル一覧 (`CredentialListActivity`)
2. クレデンシャル編集 (`CredentialEditActivity`)
3. Package Picker BottomSheet (`PackagePickerBottomSheet`)
4. Autofill 有効化 (`AutofillEnableActivity`)
5. 設定 (`SettingsActivity`)
6. Danger Zone (`DangerZoneActivity`)
7. OSS ライセンス (`OssLicensesActivity`)
8. Autofill ロック解除 (`AutofillUnlockActivity`)

## Requirements

### Requirement 1: AndroidManifest への属性追加

**Objective:** As a KeyNest 運用者, I want AndroidManifest.xml の `<application>` 要素に `android:enableOnBackInvokedCallback="true"` が宣言されていること, so that Android 13 以降の OS から predictive back gesture を正式有効化された状態のアプリとして扱われ、関連 warning が出ない状態にできる

#### Acceptance Criteria

1.1. The AndroidManifest.xml shall `<application>` 要素に `android:enableOnBackInvokedCallback="true"` 属性を含む

1.2. The AndroidManifest.xml shall 既存の `<application>` 属性（`android:name` / `android:allowBackup` / `android:fullBackupContent` / `android:dataExtractionRules` / `android:icon` / `android:roundIcon` / `android:label` / `android:supportsRtl` / `android:theme`）の値を変更しない

1.3. The AndroidManifest.xml shall 既存の `<queries>` ブロックおよび `<activity>` / `<service>` 要素の宣言内容（名前・属性値・`<intent-filter>` / `<meta-data>` 構成）を変更しない

### Requirement 2: 既存の戻る操作 UX の維持

**Objective:** As a KeyNest エンドユーザー, I want 各画面で戻る操作を行ったときの遷移挙動が、本 Issue の対応前と等価であること, so that predictive back gesture 有効化に伴って既存 UX が壊れることなく、引き続き同じ操作で同じ遷移結果を得られる

#### Acceptance Criteria

2.1. When ユーザーがクレデンシャル編集画面 (`CredentialEditActivity`) で戻る操作（OS の戻るジェスチャまたは戻るボタン押下）を行ったとき, the KeyNest App shall 当該画面を終了し、呼び出し元のクレデンシャル一覧画面に遷移させる挙動を、本 Issue 対応前と等価に維持する

2.2. When ユーザーが Package Picker BottomSheet (`PackagePickerBottomSheet`) を表示中に戻る操作を行ったとき, the KeyNest App shall 当該 BottomSheet を dismiss する挙動を、本 Issue 対応前と等価に維持する

2.3. When ユーザーが Settings / Danger Zone / OSS ライセンス / Autofill 有効化 / Autofill ロック解除 / クレデンシャル一覧 のいずれかの画面で戻る操作を行ったとき, the KeyNest App shall 各画面の戻る挙動（親 Activity への遷移またはアプリ離脱）を、本 Issue 対応前と等価に維持する

2.4. If 既存または新規に追加された Activity / Fragment / DialogFragment が `Activity.onBackPressed()` を override している場合, the KeyNest App shall 当該 override を `OnBackPressedDispatcher.addCallback(...)` を用いた `OnBackPressedCallback` パターンへ移行する

2.5. The KeyNest App shall Requirement 2.4 の移行対象が本 Issue 着手時点で 0 件であることを Developer が `Grep`（`onBackPressed`）で確認し、その事実を `impl-notes.md` に記録する

### Requirement 3: warning の解消確認

**Objective:** As a KeyNest 開発者・QA, I want アプリ起動後の logcat に `OnBackInvokedCallback is not enabled` warning が出力されないこと, so that 本 Issue で対応した manifest 属性が実機で正しく機能していることを Logcat から検証できる

#### Acceptance Criteria

3.1. When 本対応後のアプリを Android 13 (API 33) 以上の端末/エミュレータで起動し logcat を取得したとき, the logcat shall `OnBackInvokedCallback is not enabled for the application` または同等の文言を含む warning 行を含まない

3.2. The KeyNest App shall Requirement 3.1 を満たすことを実機 / エミュレータでの確認手順とともに PR 本文または `impl-notes.md` に記録する

## Non-Functional Requirements

### NFR 1: 既存挙動との後方互換

1.1. The KeyNest App shall Android 12L 以下（API 32 以下）の端末上で、本対応前と等価な戻る操作挙動を維持する（`android:enableOnBackInvokedCallback` 属性は API 33 未満では無視されるため、旧 OS への影響が無いこと）

1.2. The KeyNest App shall 本対応前のアプリで成立していた既存テスト（unit test / androidTest 含む）を、本対応後も追加修正なしで成功させる

### NFR 2: 観測可能性

2.1. The KeyNest App shall 本対応により新規に Logcat への永続的なログ出力（INFO/WARN/ERROR レベル）を増やさない

## Out of Scope

- predictive back gesture のカスタムアニメーション対応（`OnBackAnimationCallback` の実装、`onBackProgressed` / `onBackStarted` / `onBackCancelled` ハンドリングの追加）
- Compose `BackHandler` への移行（既存 UI は View ベースのため）
- predictive back gesture の動作を視覚的に確認するための専用 UI / アニメーション差し替え
- `targetSdk` / `compileSdk` の引き上げ
- AppCompat / activity-ktx 等の依存ライブラリのバージョン変更
- Fragment ベースの戻るスタック挙動の刷新
- BottomSheet の dismiss アニメーション差し替え

## Open Questions

- なし（Issue 本文・既存実装の調査結果から、要件は確定可能。Developer 着手時に
  `onBackPressed` の Grep を再実行し、override が混入していないことを再確認すること）
