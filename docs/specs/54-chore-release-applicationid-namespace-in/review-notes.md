# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-54-impl-chore-release-applicationid-namespace-in
- HEAD commit: b531f6a3ce0883b0866773df80dffa109f5ff86f
- Compared to: develop..HEAD

本 Issue は Architect フェーズを経由しない chore リネーム作業のため `tasks.md` /
`design.md` は存在しない。判定は `requirements.md` の AC ID と差分・既存コードの
突き合わせのみで実施した（`_Boundary:_` 制約は不在）。Feature Flag Protocol は
CLAUDE.md にて `**採否**: opt-out` が宣言されているため、opt-in 細目は適用しない。

## Verified Requirements

### Requirement 1: Gradle ビルド構成

- 1.1 — `app/build.gradle.kts` line 17 `namespace = "inc.goodanswers.keynest"`（diff で確認）
- 1.2 — `app/build.gradle.kts` line 21 `applicationId = "inc.goodanswers.keynest"`（diff で確認）
- 1.3 — `app/build.gradle.kts` 内に `com.example.keynest` の残存なし（`grep -rn` で 0 件）
- 1.4 — `app/build.gradle.kts` line 4-9 のコメントが `inc.goodanswers.keynest.data.KeyNestDatabase/<version>.json` を指すよう更新済み

### Requirement 2: Kotlin パッケージ階層

- 2.1 — `app/src/main/java/inc/goodanswers/keynest/` ディレクトリ存在
- 2.2 — `app/src/test/java/inc/goodanswers/keynest/` ディレクトリ存在
- 2.3 — `app/src/androidTest/java/inc/goodanswers/keynest/` ディレクトリ存在
- 2.4 — `find app/src -path "*com/example/keynest*"` で 0 件（旧階層完全消滅）
- 2.5 — `app/src/main/java/inc/goodanswers/keynest/KeyNestApp.kt` 等で `package inc.goodanswers.keynest` および `import inc.goodanswers.keynest.*` を確認。`grep -rn "com\.example\.keynest" app/src` で 0 件
- 2.6 — commit 7f53819 の `git show --stat -M` 出力に `{com/example => inc/goodanswers}/...` 形式の rename 表示があり、git rename detection で履歴追跡可能

### Requirement 3: XML リソース

- 3.1 — `app/src/main/AndroidManifest.xml` は `.KeyNestApp` / `.ui.list.CredentialListActivity` 等の相対 FQCN 記述で、`namespace = inc.goodanswers.keynest` 経由で解決される（assembleDebug 成功で間接確認）
- 3.2 — `app/src/main/res/xml/autofill_service_config.xml` の `android:settingsActivity="inc.goodanswers.keynest.ui.list.CredentialListActivity"` を diff で確認
- 3.3 — `credential_edit_activity.xml` / `credential_list_item.xml` の `<inc.goodanswers.keynest.ui.widget.StrengthBar ... />` をそれぞれ diff で確認（XML コメント内 FQCN も同期更新）
- 3.4 — `app/src/main/res` 配下および `AndroidManifest.xml` で `com.example.keynest` の残存なし

### Requirement 4: Room スキーマエクスポート先

- 4.1 — `app/schemas/inc.goodanswers.keynest.data.KeyNestDatabase/` が生成されており新識別子に基づくディレクトリ名を持つ
- 4.2, 4.3 — リポジトリ上の旧 schema ディレクトリは元々 `git ls-files app/schemas` で 0 件（未コミット）。物理的な旧ディレクトリも存在せず、欠落・残存の問題は vacuously 解消
- 4.4 — `app/src/test/java/inc/goodanswers/keynest/data/Migration_1_2_Test.kt` が rename 済みで存在。`schemaLocation` は build.gradle.kts 経由で解決され、impl-notes 記載のとおり pass

### Requirement 5: ProGuard 規則

- 5.1 — `app/proguard-rules.pro` の `-keep` 指定 4 行すべてが `inc.goodanswers.keynest.*` に置換済み（diff で確認）
- 5.2 — `app/proguard-rules.pro` 内に `com.example.keynest` の残存なし

### Requirement 6: ビルド・テスト・lint

- 6.1 — impl-notes に `BUILD SUCCESSFUL in 47s`（`./gradlew clean assembleDebug`）を記載
- 6.2 — 509 件中 504 pass / 5 fail。失敗 5 件（`PackageSignatureResolverTest` 4 件 + `LockedFillResponseSecurityTest` 1 件）は `origin/develop` 時点の baseline と同条件で発生する pre-existing flaky と implementer が `git worktree` で再現確認済み。本リネーム由来の新規 failure は 0 件
- 6.3 — impl-notes に `Lint found 94 errors, 129 warnings.` で `origin/develop` と diff = 0 を記載
- 6.4 — `CredentialEditActivityLogAuditTest` の File パス文字列のみを新識別子に追従更新（commit 7d47c2d の diff で確認）。検証ロジック・assert は不変。テストの skip / 削除・assert 弱化は不在

### Requirement 7: リネーム残存物の不在

- 7.1 — `grep -rn "com\.example\.keynest" app` で 0 件
- 7.2 — `find app/src -path "*com/example/keynest*"` で 0 件
- 7.3 — `docs/` / `design/` 配下は対象外として温存（要件 Out of Scope に整合）

### Non-Functional

- NFR 1.1 — リネームのみで API シグネチャ / ロジック / リソース内容は不変。assembleDebug 成功
- NFR 1.2 — `.kt` ファイル差分は package / import 行のみ（diff stat 上もパッケージ rename + import 行数のみの増減）
- NFR 2.1 — Gradle 構成変更は 2 値（namespace / applicationId）のみで処理量に影響しない。実時間ベンチマークは未取得だが影響要因なし
- NFR 3.1 — `git show --stat -M 7f53819` で全 rename が 71%-99% の類似度として検出されており、default 50% 閾値を上回って履歴追跡可能

## Findings

なし

## Summary

`inc.goodanswers.keynest` への applicationId / namespace リネームが Gradle / Kotlin
ソース / XML リソース / ProGuard / Room schema 出力先・テストパス参照のすべてで
一貫して適用されており、`com.example.keynest` の残存は 0 件。assembleDebug は成功、
lint は develop と完全一致、test は 504/509 pass で残り 5 件は pre-existing baseline
として再現確認済み（本リネーム由来の新規 failure 0）。Architect フェーズが介在しない
chore のため `tasks.md` は存在せず、`_Boundary:_` 制約も不在。3 カテゴリ
（AC 未カバー / missing test / boundary 逸脱）いずれにも該当する問題はない。

RESULT: approve
