# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-50-impl-chore-manifest-onbackinvokedcallback-war
- HEAD commit: 45ce4943a85906caea7d528157b256b647a22740
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out（標準 3 カテゴリ判定のみ。flag 観点の細目は適用しない）

## Verified Requirements

- 1.1 — `app/src/main/AndroidManifest.xml:25` に `android:enableOnBackInvokedCallback="true"` を追加。`app/src/test/java/com/example/keynest/manifest/OnBackInvokedCallbackEnabledTest.kt` の `applicationManifest_optsIntoOnBackInvokedCallback` が DOM パースで属性値 `"true"` を assert（impl-notes によれば PASS）
- 1.2 — diff (`git diff develop..HEAD -- app/src/main/AndroidManifest.xml`) は `<application>` 直下に 1 行追加のみ。既存 9 属性（`android:name` / `allowBackup` / `fullBackupContent` / `dataExtractionRules` / `icon` / `roundIcon` / `label` / `supportsRtl` / `theme`）は値・並び順とも未変更
- 1.3 — diff 上 `<queries>` / `<activity>` 4 件 / `<service>` 1 件 / `<intent-filter>` / `<meta-data>` ブロックの変更行は 0 件
- 2.1 — `CredentialEditActivity` 関連のコード変更なし。OS 既定の戻る経路（`finish()`）を利用するため挙動は等価維持される
- 2.2 — `PackagePickerBottomSheet` 関連のコード変更なし。BottomSheet 既定の dismiss 経路を利用するため挙動等価維持
- 2.3 — Settings / Danger Zone / OSS / Autofill 有効化 / Autofill ロック解除 / クレデンシャル一覧 のいずれも diff にコード変更なし。挙動等価維持
- 2.4 — `onBackPressed` / `BackPressedCallback` / `backPressedDispatcher` / `onBackInvoked` を `app/` 配下で Grep（kt / java 全体）した結果 0 件。移行対象が存在しないため N/A
- 2.5 — `impl-notes.md` の「onBackPressed override 調査結果」節に Grep パターン・結果・対象範囲（`app/src/main`, `app/src/test`, `app/src/androidTest`）が明記され、独立検証でも 0 件を再確認した
- 3.1 — Manifest 属性 `enableOnBackInvokedCallback="true"` が merged manifest に出力される時点で OS の warning 出力条件を満たさない設計のため、自動テスト（1.1 のテスト）で実質的に裏付けられる
- 3.2 — `impl-notes.md` の「Requirement 3.1 の検証方法」節に Android 13+ 端末/エミュレータでの手動検証手順（`adb logcat -c && adb logcat | grep -i "OnBackInvokedCallback"`、主要画面遷移）と代替検証（merged manifest 目視確認、line 46）が記録されている
- NFR 1.1 — `android:enableOnBackInvokedCallback` は API 33 で導入された属性で API 32 以下では OS が無視する仕様（impl-notes に lint Warning も同旨と記録）。`minSdk=26` の本アプリでは旧 OS 側で属性無視され従来挙動を維持
- NFR 1.2 — `./gradlew :app:testDebugUnitTest` フル実行で 507 件中 502 件 PASS。失敗 5 件はベース commit `f9596df` を `git stash` で確認した結果同一 5 件が失敗する既存債務であり、本 PR で新たに壊れた既存テストは 0 件（impl-notes に該当テスト名列挙）
- NFR 2.1 — アプリコードへの変更 0 件（manifest 1 属性追加のみ）のため、新規 Logcat 出力（INFO/WARN/ERROR）は発生し得ない

## Findings

なし

## Summary

Issue #50 は manifest への 1 属性追加と新規テスト 1 件（DOM パースで属性値を assert）の最小差分実装で、Requirement 1.1〜3.2 / NFR 1.1〜2.1 をすべてカバーしている。`onBackPressed` 系の override は独立 Grep でも 0 件を再確認し、Requirement 2.4 / 2.5 の前提（移行対象なし）が成立している。Feature Flag Protocol は opt-out 宣言のため flag 観点の細目チェックは不要。差分は要件定義の Scope セクションが明示する対象ファイル（`AndroidManifest.xml` と `app/src/test/...` の新規 test）に閉じており、boundary 逸脱なし。

RESULT: approve
