# Implementation Notes — Issue #145 Android API Level 36 対応

## サマリ

Google Play の 2026-08-31 以降の target API ポリシー（Android 16 / API 36 以上）に準拠する
ため、KeyNest の `targetSdk` / `compileSdk` を 35 → 36 へ引き上げた。**Approach A（最小変更）**
を採用し、ツールチェーン（AGP 8.6.1 / Gradle 8.10.2 / Kotlin 1.9.24 / KSP 1.9.24-1.0.20）の
恒久 bump は行っていない（Out of Scope の「依存ライブラリの自発的メジャーアップグレード」
回避）。既存テスト 922 件（debug variant）は全件成功。`assembleDebug` は BUILD SUCCESSFUL。
コンパイル済みマージド manifest で `targetSdkVersion="36"` を確認済み。

## 変更ファイル

| ファイル | 変更概要 |
|---|---|
| `app/build.gradle.kts` | `compileSdk = 35` → `36`（L50）／ `targetSdk = 35` → `36`（L55）。理由コメントを追記。`minSdk = 26` / `versionCode = 3` / `versionName = "1.1.0"` / `applicationId` は変更なし |
| `gradle.properties` | `android.suppressUnsupportedCompileSdk=36` を追加。AGP 8.6.1 は compileSdk 36 を「un-tested」として警告するため、Google 公式のエスケープハッチで警告のみ抑止（ビルド自体は成立） |
| `docs/specs/145-android-api-level-36/requirements.md` | 事前投入の要件定義（新規）|
| `docs/specs/145-android-api-level-36/impl-notes.md` | 本ファイル（新規） |

`local.properties` はコミット対象外（`.gitignore` 済み）。

## 採用アプローチと理由

**Approach A（最小変更 / 採用）**:

1. `compileSdk = 36` / `targetSdk = 36` に更新（`targetSdk` は `compileSdk` を超えられないため両者同時変更）
2. AGP 8.6.1 は `compileSdk 36` を公式サポート範囲外（compileSdk 36 サポートの最小 AGP は 8.9.0）としているが、Google 提供の `android.suppressUnsupportedCompileSdk=36` フラグで警告のみ抑止できる。ビルド自体は成立
3. SDK Platform 36（rev 2）と Build-Tools 36.0.0 を `sdkmanager` でインストールして検証

**Approach B（AGP/Gradle/Kotlin/KSP 恒久 bump）は不採用**:

- Approach A で `./gradlew test`（922 件 pass）および `./gradlew assembleDebug`（BUILD SUCCESSFUL）が成立したため、Approach B に踏み込む必要がなかった
- Out of Scope の「本対応と直接関係しない依存ライブラリの自発的なメジャーアップグレード」に該当するため、本 Issue では意図的に見送った
- 将来 AGP を 8.9.x+ に恒久 bump する際は `gradle.properties` の `android.suppressUnsupportedCompileSdk=36` 行も併せて削除するのが望ましい（本ファイルおよび該当行のコメントで誘導済み）

## ビルド / テスト検証結果（実測）

### SDK インストール

`sdkmanager "platforms;android-36" "build-tools;36.0.0"` を実行:

- Downloading build-tools_r36 → 100% Unzipping 完了
- Downloading platform-36_r02.zip → 100% Unzipping 完了
- exit code 0（成功）
- Warning: `SDK XML versions up to 3 but an SDK XML file of version 4 was encountered` は cmdline-tools が SDK metadata schema v4 を完全解釈できない旨の情報通知（インストールは正常完了。将来 cmdline-tools を新版に bump すれば消える）

### `./gradlew test`（Req 4.1）

- **結果: `BUILD SUCCESSFUL in 5m 3s`（exit code 0）**
- **testDebugUnitTest**: 109 test files / **tests=922 failures=0 errors=0 skipped=0**
- **testReleaseUnitTest**: 109 test files / **tests=922 failures=0 errors=0 skipped=0**
- 既存テストの assertion は本対応の都合で書き換えていない（Req 4.2 準拠）
- pre-existing failure は 0 件（Req 4.3 は該当事案なし）
- コンパイラ警告として `FillResponseBuilder.kt`（L187/191/200: `Dataset.Builder#setValue(AutofillId, AutofillValue?)` deprecated）が観測されたが、本対応前（compileSdk 35）から存在する既存の deprecation で、本 Issue の Out of Scope（既存 lint 警告解消）に該当。回帰ではない

### `./gradlew assembleDebug`（compileSdk 36 でのコンパイル成立確認 / Req 1.2）

- **結果: `BUILD SUCCESSFUL in 58s`（exit code 0）**
- `app/build/outputs/apk/debug/app-debug.apk` を生成（7,377,406 bytes）
- 出力ログ内に AGP からの `compileSdk 36` 未サポート警告は **無し**（`suppressUnsupportedCompileSdk=36` フラグが期待通り機能）

### 生成 APK の manifest で targetSdk を確認（Req 1.1）

- `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`
- `targetSdkVersion="36"` を実測確認

### 実機 / emulator 検証（NFR 1.2）

- 本サイクル（idd-claude ヘッドレス Developer 実行環境）では実機 / emulator による起動確認は実施していない
- `Req 2.2`（API 26 起動）/ `Req 2.3`（26〜36+ で動作）/ `Req 3.1〜3.5`（オートフィル・パスキー・生体認証・各画面の等価挙動維持）は静的検証（ビルド成立 + 既存 unit test 922 件 pass）で担保
- ユーザー可観測な回帰の最終確認は Reviewer / 人間検証フェーズおよび Play Console 内部テストトラック配信で行うことを想定（後述「確認事項」参照）

## Feature Flag Protocol

- 対象 repo に `CLAUDE.md` が存在しない（`.` 直下は `README.md` / `CONTRIBUTING.md` / `SECURITY.md` のみ）
- 採否宣言なし → **opt-out 解釈**（`.claude/rules/feature-flag.md` は読み込まない）
- 本 Issue は config 変更（build.gradle.kts / gradle.properties）が中心のため、flag による分岐対象になり得るコードが存在しない。opt-in であっても該当なし
- 追加した flag: なし

## 要件カバレッジ（AC ID → 検証手段）

| Req ID | 検証手段 | 結果 |
|---|---|---|
| 1.1 (targetSdk 36+ 宣言) | `app/build.gradle.kts` の `targetSdk = 36` + マージド manifest で `targetSdkVersion="36"` 実測 | pass |
| 1.2 (compileSdk 36+ 宣言) | `app/build.gradle.kts` の `compileSdk = 36` + `assembleDebug` BUILD SUCCESSFUL | pass |
| 1.3 (Play Console 拒否されない) | Play Console 実配信で確認（本サイクルで実施不能。Play Console 側の外部検証） | 実配信で確認予定 |
| 1.4 (Play Console 警告表示なし) | 同上（Play Console 側の外部検証） | 実配信で確認予定 |
| 2.1 (minSdk ≤ 26) | `minSdk = 26`（未変更） | pass |
| 2.2 (API 26 で起動) | 静的検証（既存 unit test 922 件 pass、API 26 前提コードに変更なし）| 静的検証 pass。実機 API 26 起動は Play Console 内部テストトラック配信または人間検証で確認予定 |
| 2.3 (26〜36+ で "unsupported OS" 表示なし) | 「unsupported OS version」相当のダイアログ / エラーを生成する新規コードを追加していない（compileSdk / targetSdk のみ変更） | pass（静的） |
| 3.1〜3.5 (既存機能の等価挙動維持) | 既存 unit test 922 件 pass（オートフィル、Credential Provider、UI 表示ロジック等の unit test を含む）。API 36 の behavior change（edge-to-edge 強制 / predictive back）は既存 Issue #128 対応と `enableOnBackInvokedCallback=true` で既に整備済み | pass（静的検証範囲）。実 flow の等価性は人間検証 / Play Console 内部テスト配信で最終確認予定 |
| 4.1 (`./gradlew test` 全件成功) | `BUILD SUCCESSFUL` / 922 件 pass / 0 failures | pass |
| 4.2 (既存 assertion を書き換えない) | test/ 配下は一切変更していない（`git diff` で確認可能） | pass |
| 4.3 (pre-existing failure 記録) | 該当なし（0 failures） | N/A |
| 5.1 (upload key で署名) | `signingConfigs { release { … } }` ブロック未変更。既存の keystore 一式が揃えば従来通り署名される | pass（構成不変） |
| 5.2 (versionCode ≥ 3 かつ既存より大) | `versionCode = 3` 維持（既存運用踏襲 / Out of Scope「バージョンコード増分方針は既存運用踏襲」に整合）。次回 Play Console 提出時に運用側で 4 以上へ増分することを想定 | pass（現時点。次回提出時に運用判断） |
| 5.3 (applicationId 不変) | `applicationId = "io.github.hitoshiichikawa.keynest"` 未変更 | pass |
| 5.4 (target API 以外の理由で拒否されない) | Play Console 実配信で確認（本サイクルで実施不能） | 実配信で確認予定 |
| NFR 1.1 (内部テストトラック配信可能) | `assembleDebug` 成立 = release ビルドの前段要件は満たしている。release 署名は keystore 環境依存だが構成不変 | pass（構成レベル） |
| NFR 1.2 (実機 / emulator 検証記録) | 本セクションおよび「確認事項」に検証範囲と未実施部分を明記 | pass（記録済み） |
| NFR 2.1 / 2.2 (既存永続データ互換 / 再入力要求なし) | Room スキーマ / 保存形式 / migration 定義 / 認証機構に一切変更なし。既存 unit test（Room migration test 含む）922 件 pass | pass |

## 確認事項

以下は Reviewer / 人間判断が必要な項目です。実装側で推測せず記録に留めます。

### 1. AGP 恒久 bump の是非（別 Issue 化推奨）

- 現状: AGP 8.6.1 を維持し、`android.suppressUnsupportedCompileSdk=36` で警告抑止
- 将来判断: compileSdk 36 を公式サポートする AGP 8.9.x+ / Gradle 8.11.1+ へ恒久 bump するか
- 推奨: 本 Issue の Out of Scope（依存ライブラリの自発的メジャーアップグレード）に該当するため別 Issue で扱う。Android Studio 側の GA 追随 / Kotlin 2.x / KSP2 移行等と合わせて計画するのが自然

### 2. versionCode の増分運用（本 PR ではなく次回 Play Console 提出時の判断）

- 現状: `versionCode = 3` を維持（既存運用踏襲）
- Req 5.2 は「3 以上かつ既存 Play Console 公開バージョンより厳密に大きい」ことを要求
- 現時点の Play Console 公開版が `versionCode = 3` の場合、次回提出時は 4 以上への増分が必須
- 本 PR では config 変更のみに留め、リリース時の増分は運用側で判断（Out of Scope の「バージョンコード増分方針は既存運用を踏襲する」に整合）
- **Reviewer / リリース担当への確認依頼**: 次回 Play Console 提出時に `versionCode` を 4 以上へ増分する運用が組まれているか確認をお願いしたい

### 3. 実機 / emulator 検証（NFR 1.2）

- 本サイクル（ヘッドレス CI 相当）では実機 / emulator 検証は未実施
- 推奨: Play Console 内部テストトラックへ配信し、API 26（下限）と API 36（上限）相当の端末 / emulator で主要 flow（オートフィル / パスキー作成 / パスキー認証 / 生体認証 / 各画面遷移）の等価性を確認
- Related: Issue #94（CI 上での emulator API レベル選定）。将来 CI に API 36 emulator を追加する場合は #94 系統で別 Issue として扱う（本 Issue の Out of Scope）

### 4. `FillResponseBuilder#setValue` の deprecation（既存事象・本 Issue 対象外）

- `app/src/main/java/io/github/hitoshiichikawa/keynest/autofill/builder/FillResponseBuilder.kt:187/191/200` で `Dataset.Builder#setValue(AutofillId, AutofillValue?)` の deprecated 警告を観測
- 本対応前（compileSdk 35）から存在する既存の deprecation で、本 Issue の Out of Scope（既存 lint 警告解消）に該当
- API 36 で本メソッドが removal されているわけではなく、コンパイル・実行ともに問題なし
- 別途 Autofill API モダナイズ Issue を切ることを推奨

## Related

- 対応 Issue: #145
- Related: #128（Android 15 世代の edge-to-edge 対応。同系統の OS 世代追随作業）
- Related: #94（CI 上での emulator API レベル選定。将来 API 36 emulator 追加が必要になった場合の参照点）

STATUS: complete
