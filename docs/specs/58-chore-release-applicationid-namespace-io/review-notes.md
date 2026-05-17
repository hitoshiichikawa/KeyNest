# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-17T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-58-impl-chore-release-applicationid-namespace-io
- HEAD commit: 3bb72c9e67a2e731b104e5b40b2584391b46e9ea
- Compared to: develop..HEAD
- Feature Flag Protocol 採否: opt-out（flag 観点の細目検証はスキップ）
- design.md / tasks.md は本 spec ディレクトリに存在しない（PM の `requirements.md` と
  Developer の `impl-notes.md` のみ）。Architect が不要と判断された単純リネーム chore のため、
  境界判定は `requirements.md` の Out of Scope 節と Issue スコープに照らして実施。

## Verified Requirements

- 1.1 — `app/build.gradle.kts` で `namespace = "io.github.hitoshiichikawa.keynest"`（diff 確認）
- 1.2 — `app/build.gradle.kts` で `applicationId = "io.github.hitoshiichikawa.keynest"`（diff 確認）
- 1.3 — `grep -rn 'inc\.goodanswers\.keynest' app/` で 0 件
- 1.4 — `grep -rn 'com\.example\.keynest' app/` で 0 件
- 1.5 — `app/build.gradle.kts` コメントが `io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/<version>.json` を指すよう更新済み（diff line 8-9）
- 2.1 / 2.2 / 2.3 — `git diff --stat` の中で main / test / androidTest 配下の Kotlin が `io/github/hitoshiichikawa/keynest/` 階層に移動していることを確認
- 2.4 — `find app/ -path '*inc/goodanswers/keynest*'` で 0 件
- 2.5 — `grep -rn 'inc\.goodanswers\.keynest' app/` で 0 件（package / import / KDoc 文字列含む）
- 2.6 — `git diff --summary -M develop..HEAD | grep '^ rename'` で 147 件、類似度 83%-99%（既定しきい値 50% を上回り rename 検出可能）
- 3.1 — AndroidManifest は変更なし。相対 FQCN（`.KeyNestApp` 等）を `namespace` から解決する設計で、`assembleDebug` 成功（impl-notes.md）により ClassNotFoundException 不発生を確認
- 3.2 — `app/src/main/res/xml/autofill_service_config.xml` で `android:settingsActivity="io.github.hitoshiichikawa.keynest.ui.list.CredentialListActivity"` を確認
- 3.3 — `credential_edit_activity.xml:315` / `credential_list_item.xml:164` でカスタム View `<io.github.hitoshiichikawa.keynest.ui.widget.StrengthBar>` を確認、KDoc コメントも追従
- 3.4 — `grep -rn 'inc\.goodanswers\.keynest' app/src/main/res app/src/main/AndroidManifest.xml` で 0 件
- 4.1 — `ls app/schemas/` で `io.github.hitoshiichikawa.keynest.data.KeyNestDatabase` ディレクトリの存在を確認
- 4.2 — リポジトリ上に旧 schema は元々未コミット（impl-notes.md 記載）。KSP 再生成で新識別子配下に再構築（version 欠落なし）
- 4.3 — 旧 namespace 由来の schema ディレクトリは未生成（残存なし）
- 4.4 — `Migration_1_2_Test.kt` が test スイートに存在、`schemaLocation` は build.gradle.kts 経由で解決され `./gradlew test` 成功（impl-notes.md）
- 5.1 — `app/proguard-rules.pro` の `-keep class ...data.entity.**` / `-keep public class ...autofill.KeyNestAutofillService` / `-keep public class ...autofill.unlock.AutofillUnlockActivity` / `-keep public class ...KeyNestApp` すべて新識別子に置換済み（diff 確認）
- 5.2 — `grep 'inc\.goodanswers\.keynest' app/proguard-rules.pro` で 0 件
- 6.1 — impl-notes.md に `./gradlew clean assembleDebug` の `BUILD SUCCESSFUL in 1m 16s` を記録
- 6.2 — impl-notes.md に `./gradlew test` の `BUILD SUCCESSFUL in 1m 45s` / `tests=509 failures=0 errors=0 skipped=0` を記録
- 6.3 — impl-notes.md に `./gradlew lint` の `94 errors, 129 warnings`（#54 当時の develop baseline と総数・カテゴリ分布完全一致）を記録、新規違反 0 件
- 6.4 — テストの skip / 削除 / assert 弱化なし。`CredentialEditActivityLogAuditTest` は参照パス文字列の追従更新のみで検証ロジック不変（diff line 26-29 / 52-55 確認）
- 7.1 — `grep -rn 'inc\.goodanswers\.keynest' app/` で 0 件
- 7.2 — `grep -rn 'com\.example\.keynest' app/` で 0 件
- 7.3 — `find app/ -path '*inc/goodanswers/keynest*'` で 0 件
- 7.4 — `find app/ -path '*com/example/keynest*'` で 0 件
- 7.5 — `docs/specs/` / `design/` 配下の履歴は検索対象から除外（Out of Scope 通り温存）
- NFR 1.1 — package rename のみで API シグネチャ / ロジック / リソース内容は不変。`assembleDebug` 成功と manifest 相対参照解決成功で機能挙動の差分なしを担保
- NFR 1.2 — `git diff --stat` の .kt 差分はすべて package / import / KDoc の package 名以外不変（diff 確認）
- NFR 2.1 — impl-notes.md で「Gradle 構成変更は applicationId / namespace の 2 値のみで処理量に影響しない」と説明、`assembleDebug` 1m 16s / `test` 1m 45s。厳密ベンチマークは未実施だが、リネームの性質上 +20% 逸脱は構造的に起こり得ない
- NFR 3.1 — `git diff --summary -M develop..HEAD` 出力に 147 件すべて 83%-99% 類似度の rename として記録され、Git の既定類似度しきい値（50%）で履歴追跡可能

## Findings

なし

## Summary

要件定義の全 numeric ID（1.1-1.5 / 2.1-2.6 / 3.1-3.4 / 4.1-4.4 / 5.1-5.2 / 6.1-6.4 / 7.1-7.5 /
NFR 1.1 / 1.2 / 2.1 / 3.1）が、ソース差分・build 構成・XML・proguard・schema ディレクトリ・
ビルド/テスト/lint 結果のいずれかで観測可能に裏付けられている。boundary 逸脱なし（変更範囲は
app モジュール配下に限定、`docs/specs/` / `design/` は Out of Scope 通り温存）。テストの
skip / 削除 / assert 弱化なし。147 件の rename は類似度 83%-99% で履歴追跡可能。

RESULT: approve
