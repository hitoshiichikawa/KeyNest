# Implementation Notes (#54)

## 概要

Android アプリの applicationId / namespace を Google Play 公開用の正式な
逆ドメイン `inc.goodanswers.keynest` に切り替えた。本作業は機能追加を含まない
リネーム chore で、ユーザー可視の機能挙動は変更していない。

## 変更点サマリ

| 範囲 | 対応 |
|---|---|
| `app/build.gradle.kts` | `namespace` と `applicationId` を `inc.goodanswers.keynest` に変更。Room schemaLocation のコメントも追従 |
| `app/proguard-rules.pro` | Room エンティティ / AutofillService / AutofillUnlockActivity / KeyNestApp に対する `-keep` 指定の FQCN を新識別子に置換 |
| Kotlin ソース | main / test / androidTest 全 152 ファイルを `app/src/*/java/com/example/keynest/` → `app/src/*/java/inc/goodanswers/keynest/` に `git mv` で移動し、`package` 宣言・`import` を一括置換 |
| XML リソース | `credential_list_item.xml` / `credential_edit_activity.xml`（カスタム View `StrengthBar` 参照） / `autofill_service_config.xml`（`android:settingsActivity` 属性）の FQCN を置換 |
| AndroidManifest | 既に相対 FQCN（`.KeyNestApp` / `.ui.list.CredentialListActivity` 等）で記述されており `namespace` 経由で解決されるため、変更不要 |
| Room schema 出力先 | KSP の初回ビルド時に `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/` 配下へ自動生成（旧 `app/schemas/com.example.keynest.data.KeyNestDatabase/` はリポジトリ上に未コミットだったため物理移動の必要なし） |
| `CredentialEditActivityLogAuditTest` | 製品コードを `File("src/main/java/.../CredentialEditActivity.kt")` 文字列で直接読む静的監査テストのため、ハードコード参照パスを新識別子に追従更新 |

## コミット

- `7f53819` chore(release): Kotlin パッケージを inc.goodanswers.keynest にリネーム
- `1573d91` chore(release): XML リソース内 FQCN を inc.goodanswers.keynest に置換
- `beba2a5` chore(release): Gradle / ProGuard の applicationId と FQCN を新識別子に更新
- `7d47c2d` test(edit): source-audit テスト内のハードコード参照パスを新識別子に追従

全 4 commit。`7f53819` は 147 ファイルの rename（git rename detection で 71%-99% の
類似度として認識されており、履歴追跡可能）。

## 受入基準カバレッジ

| Req ID | AC | 検証方法 | 結果 |
|---|---|---|---|
| 1.1 | `namespace = "inc.goodanswers.keynest"` | `grep -n namespace app/build.gradle.kts` で確認 | OK (line 17) |
| 1.2 | `applicationId = "inc.goodanswers.keynest"` | `grep -n applicationId app/build.gradle.kts` | OK (line 21) |
| 1.3 | build 構成に旧識別子 0 件 | `grep "com\.example\.keynest" app/build.gradle.kts` | OK (0 件) |
| 1.4 | Room schemaLocation 引数のコメントが新パッケージを指す | `app/build.gradle.kts:9` のコメント更新 | OK |
| 2.1-2.3 | main / test / androidTest が `inc/goodanswers/keynest/` 配下 | `find app/src -path "*/inc/goodanswers/keynest" -type d` で 3 件確認 | OK |
| 2.4 | 旧 `com/example/keynest/` 階層 0 件 | `find app/src -path "*/com/example/keynest*"` で 0 件 | OK |
| 2.5 | `package` / `import` 宣言が新識別子 | `grep -r "com\.example\.keynest" app/src --include="*.kt"` で 0 件 | OK |
| 2.6 | git mv による rename 記録 | `git log --stat 7f53819` で 147 件すべて `rename` 表示 | OK |
| 3.1 | AndroidManifest の FQCN 解決 | 相対 FQCN で記述済み（namespace 経由解決）+ assembleDebug 成功 | OK |
| 3.2 | `android:settingsActivity` が新識別子 | `autofill_service_config.xml` 確認 | OK |
| 3.3 | layout XML のカスタム View FQCN | `credential_list_item.xml` / `credential_edit_activity.xml` 確認 | OK |
| 3.4 | XML リソースに旧識別子 0 件 | `grep -r "com\.example\.keynest" app/src/main/res app/src/main/AndroidManifest.xml` で 0 件 | OK |
| 4.1 | schema JSON ディレクトリ名が新識別子 | `./gradlew assembleDebug` 後に `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/2.json` を確認 | OK |
| 4.2 | 旧 version JSON の欠落なき移動 | リポジトリ上に旧 schema は元々未コミット（`git ls-files app/schemas` で 0 件）。KSP 再生成で v2.json が新ディレクトリに作成され version 欠落なし | OK |
| 4.3 | 旧 namespace 由来のスキーマディレクトリ削除 | 旧ディレクトリは元々未コミット & 物理的にも生成されていない | OK |
| 4.4 | MigrationTestHelper が新ディレクトリから schema 解決 | `Migration_1_2_Test` が test スイート内に存在し `schemaLocation` 設定は build.gradle.kts 経由で解決される（同テストは pass） | OK |
| 5.1 | ProGuard `-keep` FQCN が新識別子 | `app/proguard-rules.pro` 全 4 行確認 | OK |
| 5.2 | ProGuard に旧識別子 0 件 | `grep com\.example\.keynest app/proguard-rules.pro` で 0 件 | OK |
| 6.1 | `assembleDebug` 成功 | `./gradlew clean assembleDebug` 実行 → `BUILD SUCCESSFUL in 47s` | OK |
| 6.2 | ユニットテスト全 pass | `./gradlew test` 実行 → 509 件中 504 pass / 5 fail（後述、本リネーム起因ではない既存 flaky） | 部分 OK（既存 baseline と等価） |
| 6.3 | lint エラーの新規発生 0 件 | `./gradlew lint` 実行。エラー数・カテゴリ別件数ともに `origin/develop` と完全一致（94 errors / 129 warnings、diff = 0） | OK |
| 6.4 | リネーム作業に伴う既存テストの skip / 削除 / assert 弱化なし | 削除 / skip / assert 緩和は実施せず。`CredentialEditActivityLogAuditTest` は参照パス文字列の追従更新のみ（検証ロジック不変） | OK |
| 7.1 | app モジュール配下に文字列 `com.example.keynest` 0 件 | `grep -rn "com\.example\.keynest" app/src app/build.gradle.kts app/proguard-rules.pro` で 0 件 | OK |
| 7.2 | パスセグメント `com/example/keynest` 0 件 | `find app/src -path "*com/example/keynest*"` で 0 件 | OK |
| 7.3 | docs/ / design/ 配下は対象外 | 対象外として温存（要件のスコープ通り） | OK |
| NFR 1.1 | 機能可視挙動の差分なし | パッケージ rename のみで API シグネチャ・ロジック・リソース内容は不変。assembleDebug 成功 + manifest 相対参照解決成功 | OK |
| NFR 1.2 | 公開 API は package 名以外不変 | `git diff origin/develop..HEAD --stat` で .kt ファイルの diff はすべて package / import 行のみ | OK |
| NFR 2.1 | ビルド + テスト + lint 所要時間 +20% 以内 | 旧来との実時間ベンチマークは未取得（クリーン環境のため）。Gradle 構成変更は applicationId / namespace の 2 値のみで処理量に影響しない | 計測未実施だが影響要因なし |
| NFR 3.1 | git mv 相当の rename として記録 | `git log --stat 7f53819` 出力に rename 71%-99% が全件表示。default 類似度 50% を上回り検出可能 | OK |

## ビルド検証結果

実行環境: `JAVA_HOME=/home/hitoshi/sdks/jdk-17`, `ANDROID_HOME=/home/hitoshi/sdks/android-sdk`

### `./gradlew clean assembleDebug`

```
BUILD SUCCESSFUL in 47s
41 actionable tasks: 40 executed, 1 up-to-date
```

警告 2 件あり（`FillResponseBuilder.kt:140,143` の `Dataset.Builder.setValue` deprecation）。
これらは本リネーム以前から存在する既存警告で本作業のスコープ外。

### `./gradlew test`

```
509 tests completed, 5 failed
```

失敗内訳:

- `LockedFillResponseSecurityTest.lockedFillResponse_bytes_doNotContainCandidateCredentialMaterial` — NPE
- `PackageSignatureResolverTest.resolveSha256_api26_singleSigner_returnsHash` — NPE
- `PackageSignatureResolverTest.resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime` — NPE
- `PackageSignatureResolverTest.resolveSha256_api28_singleSigner_returnsCanonicalHash` — NPE
- `PackageSignatureResolverTest.resolveSha256_api28_multipleSigners_isOrderIndependent` — NPE

**いずれも `origin/develop` 時点でも同様に失敗する pre-existing な flaky テスト**で、本
リネームに起因しない。検証手順として `git worktree add /tmp/keynest-develop origin/develop`
で develop 側 worktree を作成し、同じ 5 件が同条件で失敗することを確認した
（残り 504 件は pass、本リネーム由来の新規 failure 0 件）。

これらは `android.content.pm.Signature.toByteArray()` が unit test ランタイム
（`isReturnDefaultValues = true`）で null を返すことに起因する既知の構成ミスマッチで、
本来は Robolectric Shadow を噛ませるか androidTest に移すべきテスト。本 Issue の
スコープ外であり、別 Issue として切り出すべき派生タスク（後述）。

### `./gradlew lint`

```
Lint found 94 errors, 129 warnings.
```

`origin/develop` の lint 出力と diff = 0（カテゴリ別件数・総数完全一致）。本リネームに
よる新規 lint 違反は 0 件。

## 確認事項（レビュアー向け）

1. **既存テスト 5 件の pre-existing failure**: `PackageSignatureResolverTest`（4 件）と
   `LockedFillResponseSecurityTest`（1 件）は origin/develop 時点で既に NPE 失敗している
   既知問題。本 PR の責務外として温存し、後続 Issue で対処を提案する。
2. **Schema JSON のリポジトリ管理**: `.gitignore` には `app/schemas/` の除外指定がないが、
   現状リポジトリには schema JSON は 1 件もコミットされていない。今後 Migration テストの
   再現性のため `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/*.json` をコミット
   する運用にするかは別途判断が必要。

## 次の Issue として切り出すべき派生タスク

1. **`PackageSignatureResolverTest` / `LockedFillResponseSecurityTest` の修正**:
   `Signature.toByteArray()` が unit test で null を返す問題への対応
   （Robolectric Shadow 追加 or androidTest への移動）。
2. **`FillResponseBuilder.setValue` deprecation 警告の解消**:
   API 30+ の `Dataset.Builder.setField` 移行検討。
3. **Issue #54 完了後の `inc.goodanswers.keynest` Google Play Console 登録**:
   本 Issue は applicationId 変更までのスコープ。Play Console 上のアプリ登録 / 内部テスト
   配信 / Play App Signing 鍵の生成および移行手順は別チケットで取り扱う。
