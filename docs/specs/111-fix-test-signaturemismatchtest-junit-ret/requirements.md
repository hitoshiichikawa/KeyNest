# Requirements Document

## Introduction

PR #97 (Issue #94) で導入された API 34 emulator 上の `connectedDebugAndroidTest`
job において、`SignatureMismatchTest` が `initializationError` で恒常的に
失敗している。Issue #98 (compile error 系) と Issue #106 (mockk-android runtime
系) を経て残った最後の debt で、JUnit 4 の仕様 (`@Test` メソッドの戻り値型は
`void`/`Unit` でなければならない) に反した記法が原因。テストランナーが
クラス全体を `Invalid test class` と判定するため、内部の `@Test` メソッドが
1 件も実行されない状態になっている。

本要件は、テスト意図 (signature mismatch / null signature の candidate
フィルタリング検証) と既存のテストメソッド名を保ったまま、`SignatureMismatchTest`
の `@Test` メソッドが API 34 emulator 上で正常に実行されるようにすることを
目的とする。

## Requirements

### Requirement 1: SignatureMismatchTest が JUnit 4 のテストクラスとして受理される

**Objective:** As a CI operator, I want `SignatureMismatchTest` を JUnit 4
ランナーが有効なテストクラスとして受理すること, so that PR #97 で導入された
`connectedDebugAndroidTest (API 34)` job が green になり、androidTest source set
の latent debt が解消される

#### Acceptance Criteria

1. When `connectedDebugAndroidTest` を API 34 emulator 上で実行したとき, the test runner shall `SignatureMismatchTest` を `InvalidTestClassError` を出さずに instantiate する
2. When `SignatureMismatchTest` が instantiate されたとき, the test runner shall `candidateFilter_dropsMismatched_andRetainsMatched` を individual test case として実行する
3. When `SignatureMismatchTest` が instantiate されたとき, the test runner shall `candidateFilter_returnsEmpty_whenAllAreMismatched` を individual test case として実行する
4. The `SignatureMismatchTest` 内のすべての `@Test` メソッド shall JUnit 4 の戻り値型制約 (`void` / `Unit` を返す) を満たす

### Requirement 2: 既存のテスト意図とメソッド名の保持

**Objective:** As a maintainer, I want テスト修正後も signature mismatch /
null signature フィルタリングの検証範囲が同等であること, so that
`ResolveAutofillCandidatesUseCase` のセキュリティ要件 (Req 4.2 / 4.3 / 4.4)
の回帰検知能力が損なわれない

#### Acceptance Criteria

1. The `SignatureMismatchTest` shall `candidateFilter_dropsMismatched_andRetainsMatched` のテストメソッド名を維持する
2. The `SignatureMismatchTest` shall `candidateFilter_returnsEmpty_whenAllAreMismatched` のテストメソッド名を維持する
3. When `candidateFilter_dropsMismatched_andRetainsMatched` が実行されたとき, the test shall signature が matching の候補のみが残り、mismatch および null signature の候補が落ちることを検証する
4. When `candidateFilter_returnsEmpty_whenAllAreMismatched` が実行されたとき, the test shall すべての候補が mismatch または null signature の場合に結果が空になることを検証する
5. The test fixture shall 既存の `ResolveAutofillCandidatesUseCase` / `PackageSignatureResolver` / `CredentialRepository` の参照関係を変えずに動作する

### Requirement 3: 同種の再発を抑止する記法の採用

**Objective:** As a maintainer, I want 同じ root cause (`fun ... () = runBlocking { ... }` で
末尾式が `Unit` 以外を返すパターン) が将来再混入しにくい記法を取り入れること,
so that 同種の latent debt が今後の androidTest 追加時に静かに混入し続けない

#### Acceptance Criteria

1. The fix shall `@Test` メソッドの戻り値型が `Unit` であることが Kotlin コンパイル時または code review 時に判別しやすい記法を採用する
2. While `SignatureMismatchTest` の `@Test` メソッドが coroutine スコープで非同期処理を呼ぶ必要があるとき, the test shall JUnit 4 の戻り値型制約を満たしたまま coroutine 呼び出しを行う方法で記述される

## Non-Functional Requirements

### NFR 1: 影響範囲の局所性

1. The fix shall 製品コード (`app/src/main/...`) を変更しない
2. The fix shall JVM unit test (`app/src/test/...`) を変更しない
3. The fix shall `SignatureMismatchTest` 以外の androidTest ファイルを変更しない
4. The fix shall `app/build.gradle.kts` / `gradle/libs.versions.toml` などビルド設定ファイルを変更しない

### NFR 2: 検証可能性

1. When CI が `connectedDebugAndroidTest (API 34)` job を実行したとき, the JUnit XML report shall `SignatureMismatchTest` の 2 件のテストケースが `passed` として記録されることを示す
2. When 開発者が `./gradlew :app:compileDebugAndroidTestKotlin` をローカル実行したとき, the build shall `BUILD SUCCESSFUL` で終了する

## Out of Scope

Issue 本文「スコープ外」の転記:

- 他の androidTest 全体の見直し（観測された 1 件以外）
- JUnit 5 / Robolectric 等への移行
- Truth assertion ライブラリの変更

加えて本要件で禁止する変更:

- `SignatureMismatchTest` のテストメソッド名の rename
- テストが検証している意図 (signature mismatch / null signature の candidate フィルタリング、Req 4.2 / 4.3 / 4.4) の変更
- 製品コード (`PackageSignatureResolver`, `ResolveAutofillCandidatesUseCase`, `CredentialRepository` 等) への変更
- JVM unit test (`app/src/test/...`) への ripple
- ビルド設定 (`build.gradle.kts`, `libs.versions.toml`, ProGuard 等) の変更
- CI workflow ファイルの変更

## Open Questions

なし

## 実装ヒント（非規範: 受入基準には含まれない参考情報）

JUnit 4 の戻り値型制約を満たす書き方は複数あり、Developer の判断に委ねる。
以下は参考の選択肢で、いずれを採るかは AC の対象外とする:

- block body 構文 (`fun ... () { runBlocking { ... } }`) に書き換える
- expression body を維持しつつ戻り値型を明示する (`fun ... (): Unit = runBlocking { ... }`)
- `runBlocking` ブロック内で assertion 後に明示的に `Unit` を末尾式とする

いずれの選択肢でも Requirement 1〜3 の AC を満たせる前提で、Developer は
「同じパターン (`fun ... () = runBlocking { ... }` で末尾式が Unit 以外) が
将来再発しにくい」観点で選択することが推奨される。
