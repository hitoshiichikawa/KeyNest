# Review Notes: Issue #111

- Round: 1
- Reviewer: claude (general-purpose subagent)
- HEAD: 5ad63957e5d77ea9fba3ad6db69011a20ee6cd0b
- Base: develop

## Summary

PR #97 (Issue #94) で導入された API 34 emulator 上の `connectedDebugAndroidTest`
job における `SignatureMismatchTest` の `initializationError` (JUnit 4 戻り値型制約
違反) を解消する fix。`develop..HEAD` の差分は以下 3 ファイルのみ（合計
+274 / -19）:

- `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt`
  (+23 / -19): 2 件の `@Test` メソッドを expression body
  `fun ... () = runBlocking { ... }` から block body
  `fun ... () { runBlocking { ... } }` 形式へ変換
- `docs/specs/111-fix-test-signaturemismatchtest-junit-ret/requirements.md` (new)
- `docs/specs/111-fix-test-signaturemismatchtest-junit-ret/impl-notes.md` (new)

製品コード (`app/src/main/...`)、JVM unit test (`app/src/test/...`)、他の
androidTest ファイル、ビルド設定 (`build.gradle.kts`, `libs.versions.toml` 等)、
CI workflow への変更は無し（NFR 1.1〜1.4 を `git diff --stat` で確認）。

Developer は requirements.md「実装ヒント」3 候補のうち **block body 構文**を
選択。Req 3.1（戻り値型 `Unit` が判別しやすい記法）担保のため、Kotlin 言語
仕様で戻り値型が `Unit` 固定になる block body を採用した旨が impl-notes.md
「選択した記法と理由」に記載されている。

ローカル検証は `compileDebugAndroidTestKotlin` で BUILD SUCCESSFUL を確認済。
`connectedDebugAndroidTest (API 34)` 自体は emulator 必須のため CI 側で最終
検証する旨が明記されている（Req 1.1〜1.3 / NFR 2.1）。

## AC カバレッジ

| AC ID | 内容 (要約) | 判定 | 根拠 |
|---|---|---|---|
| 1.1 | API 34 emulator 上で `SignatureMismatchTest` が `InvalidTestClassError` なく instantiate される | cover | block body 化により `@Test` メソッドの戻り値型が `Unit` 固定となり、JUnit 4 の `Invalid test class` 判定の原因が除去される。最終検証は CI 上の `connectedDebugAndroidTest (API 34)` で行う前提（impl-notes.md「Reviewer への申し送り §1」） |
| 1.2 | `candidateFilter_dropsMismatched_andRetainsMatched` が individual case として実行される | cover | 1.1 と同じ理由でテストランナーがメソッドを実行可能。メソッド名・`@Test` アノテーション・本体ロジック保持を SignatureMismatchTest.kt L34-52 で確認 |
| 1.3 | `candidateFilter_returnsEmpty_whenAllAreMismatched` が individual case として実行される | cover | 同上。SignatureMismatchTest.kt L54-65 で確認 |
| 1.4 | すべての `@Test` メソッドが `void` / `Unit` を返す | cover | block body `fun X() { runBlocking { ... } }` の戻り値型は Kotlin 言語仕様により `Unit` で確定。`compileDebugAndroidTestKotlin` の BUILD SUCCESSFUL が傍証（impl-notes.md「実行コマンドと結果」§1） |
| 2.1 | メソッド名 `candidateFilter_dropsMismatched_andRetainsMatched` 維持 | cover | diff 上で関数名は変更されていない（`@@` ヘッダおよび L35 で verbatim） |
| 2.2 | メソッド名 `candidateFilter_returnsEmpty_whenAllAreMismatched` 維持 | cover | diff 上で関数名は変更されていない（L55 で verbatim） |
| 2.3 | matching の候補のみ残り、mismatch / null signature が落ちる検証を維持 | cover | fixture (`alice`/`imposter`/`ghost`/`bob`) と assertion `containsExactly("alice", "bob")` を完全保持 (L37-50) |
| 2.4 | 全候補が mismatch / null signature の場合に結果が空になる検証を維持 | cover | fixture (`imposter`/`ghost`) と `assertThat(candidates).isEmpty()` を完全保持 (L56-63) |
| 2.5 | `ResolveAutofillCandidatesUseCase` / `PackageSignatureResolver` / `CredentialRepository` の参照関係を変えない | cover | import 文・`stubResolver` / `stubRepo` / `rec` ヘルパ・use case 呼び出し (`useCase("com.example.target")`) すべて無変更 |
| 3.1 | 戻り値型 `Unit` が Kotlin compile time / code review 時に判別しやすい記法 | cover | block body は Kotlin 言語仕様により暗黙的に `Unit` 戻り値となり、`runBlocking { ... }` の内部式型に左右されない構造的保証がある。impl-notes.md「選択した記法と理由」で選定根拠が明示 |
| 3.2 | coroutine スコープの非同期処理を JUnit 4 制約を満たしたまま呼ぶ | cover | block body 内で `runBlocking { ... }` を statement として呼ぶ形に変更され、coroutine 利用と戻り値型制約の両立を達成 |
| NFR 1.1 | 製品コード (`app/src/main/...`) 無変更 | cover | `git diff --stat develop..HEAD` で `app/src/main` 配下は 0 件 |
| NFR 1.2 | JVM unit test (`app/src/test/...`) 無変更 | cover | 同上で `app/src/test` 配下は 0 件 |
| NFR 1.3 | `SignatureMismatchTest` 以外の androidTest 無変更 | cover | 同上で androidTest 変更は当該 1 ファイルのみ |
| NFR 1.4 | ビルド設定 (`build.gradle.kts` / `libs.versions.toml` 等) 無変更 | cover | 同上で該当ファイル変更なし |
| NFR 2.1 | CI `connectedDebugAndroidTest (API 34)` で 2 件が `passed` 記録 | deferred to CI | ローカル emulator 未実施。impl-notes.md でも CI 検証へ委ねる旨明記。AC 構造 (`When CI が ... を実行したとき`) も CI 実行を前提としており、ローカル未検証は requirements.md に整合 |
| NFR 2.2 | ローカル `./gradlew :app:compileDebugAndroidTestKotlin` が BUILD SUCCESSFUL | cover | impl-notes.md「実行コマンドと結果」§1 で 2m10s で BUILD SUCCESSFUL を確認済 |

## Findings

### AC 未カバー

None.

### Missing Test

None. AC 1.1〜1.3 / NFR 2.1 は CI 上の `connectedDebugAndroidTest (API 34)` job
を検証手段として要求しており、これは既存 CI ワークフロー (PR #97 で導入済) で
実行される。本 PR が新たに検証手段を追加する必要は requirements.md からは
要求されていない（NFR 1 で CI workflow 変更は禁止されているため、むしろ追加
してはならない）。AC 1.4 / NFR 2.2 はローカル `compileDebugAndroidTestKotlin`
で検証済 (impl-notes.md §実行コマンドと結果)。

### Boundary 逸脱

None. tasks.md は存在しないが、requirements.md NFR 1（製品コード / JVM unit
test / 他 androidTest / ビルド設定への変更禁止）および「Out of Scope」セクション
（メソッド名 rename 禁止 / テスト意図変更禁止 / CI workflow 変更禁止）と差分を
照合した結果、すべて遵守。差分対象ファイルは
`SignatureMismatchTest.kt` 1 件 + spec docs 2 件のみ。

## Notes

- impl-notes.md「Reviewer への申し送り §2」に記載の `AppInfoProviderTest` 既存
  failing test は、本 Issue のスコープ外であり、HEAD 以前から存在する既知の
  独立 failure である旨が `git stash` での再現確認とともに記録されている。
  本 PR の reject 事由には該当しない。
- block body と explicit return type (`: Unit = runBlocking { ... }`) の選択は
  AC 上は等価 (Req 3.1/3.2 は両者で満たせる) で、Developer の Req 3.1 重視
  judgement は妥当。スタイル観点での reject はしない（本レビューの制約）。
- 最終的な CI green 確認 (Req 1.1〜1.3 / NFR 2.1) は PR ステージで実施される
  前提のため、本 round では「AC を構造的に満たす変更が入っていること」までを
  approve 基準とする。

RESULT: approve
