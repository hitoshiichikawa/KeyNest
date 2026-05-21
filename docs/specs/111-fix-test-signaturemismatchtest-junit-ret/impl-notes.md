# Implementation Notes — Issue #111

`fix(test): make SignatureMismatchTest @Test methods return Unit to satisfy JUnit 4 runner`

## 概要

`androidTest` source set 内の `SignatureMismatchTest.kt` にある 2 件の
`@Test` メソッドが expression-body `fun ... () = runBlocking { ... }`
記法で書かれており、末尾式 (Truth の `containsExactly(...)` が返す
`Ordered`、および `isEmpty()` が返す `Unit?`) の型が JUnit 4 が要求する
`void` / `Unit` と一致しないため、AndroidJUnit4 ランナーが
`SignatureMismatchTest` を `InvalidTestClassError` と判定して 1 件も
実行できない状態だった。

本変更は **テストファイル 1 つの記法** のみを直し、両 `@Test` メソッドが
`fun ... () { runBlocking { ... } }` （block body）形式で記述されるように
する。テスト本体のロジック、assertion、`runBlocking` の使用、メソッド名は
完全に維持する。

## 変更ファイル

| ファイル | 種別 | 変更内容 |
|---|---|---|
| `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt` | test (modify) | 2 @Test メソッドを expression body から block body へ変換（+23 / -19） |
| `docs/specs/111-fix-test-signaturemismatchtest-junit-ret/impl-notes.md` | docs (new) | 本ファイル |

製品コード (`app/src/main/...`) / JVM unit test (`app/src/test/...`) /
他の androidTest ファイル / ビルド設定 / CI workflow には一切手を入れていない
(NFR 1.1〜1.4)。

## 選択した記法と理由

requirements.md「実装ヒント」に記載された 3 候補のうち、**block body
構文** (`fun ... () { runBlocking { ... } }`) を採用した。

選定理由は requirements.md **Requirement 3.1** (「`@Test` メソッドの戻り値型が
`Unit` であることが Kotlin コンパイル時または code review 時に判別しやすい
記法」) を最も強く担保する形だから:

- 関数本体が statement ブロック (`{ ... }`) の場合、Kotlin の言語仕様により
  戻り値型は `Unit` で固定される。`runBlocking { ... }` の戻り値型 (Truth の
  `Ordered` や `Unit?`) がメソッドシグネチャに影響しないため、将来 assertion
  ライブラリを変更しても **root cause が再混入し得ない構造的保証** がある
  (Req 3.1, 3.2)。
- 一方 `: Unit = runBlocking { ... }` (候補 2) は型注釈の付け忘れ /
  リファクタ時の取り違えで容易に元の壊れた記法に戻れてしまう。明示的な
  `Unit` 末尾式 (候補 3) も「規約に依存する」点で同様の弱さがある。
- block body は他のテスト (`PackageSignatureResolverTest.kt` 等 JVM 側の
  慣例) で広く用いられている形でもあり、code review 時の「これは Unit を
  返す関数だ」という認知も最も自然。

なお `runBlocking` のスコープ内のテスト本体ロジックは完全に保持しており、
唯一の差分は「`= runBlocking { ... }` → `{ runBlocking { ... } }`」のみ
(プラスインデント 1 段)。assertion / fixture / メソッド名は無変更。

## Requirement との対応表

| Req ID | 内容 | 担保方法 |
|---|---|---|
| 1.1 | API 34 emulator 上で `SignatureMismatchTest` が `InvalidTestClassError` を出さず instantiate される | block body 変換により JUnit 4 戻り値型制約を満たす。**CI 側 `connectedDebugAndroidTest (API 34)` で最終検証** |
| 1.2 | `candidateFilter_dropsMismatched_andRetainsMatched` が individual case として実行される | 同上 (CI 検証) |
| 1.3 | `candidateFilter_returnsEmpty_whenAllAreMismatched` が individual case として実行される | 同上 (CI 検証) |
| 1.4 | すべての `@Test` メソッドが `void` / `Unit` を返す | block body 構文により Kotlin コンパイル時に `Unit` 戻り値型が確定。ローカル `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL で確認 |
| 2.1 | メソッド名 `candidateFilter_dropsMismatched_andRetainsMatched` 維持 | 名前未変更 (diff 上 verbatim) |
| 2.2 | メソッド名 `candidateFilter_returnsEmpty_whenAllAreMismatched` 維持 | 名前未変更 (diff 上 verbatim) |
| 2.3 | matching の候補のみ残り、mismatch / null signature が落ちる検証を維持 | `assertThat(candidates.map { it.username }).containsExactly("alice", "bob")` のロジック / fixture (alice, imposter, ghost, bob) を完全保持 |
| 2.4 | 全候補が mismatch / null signature の場合に結果が空になる検証を維持 | `assertThat(candidates).isEmpty()` と fixture (imposter, ghost) を完全保持 |
| 2.5 | `ResolveAutofillCandidatesUseCase` / `PackageSignatureResolver` / `CredentialRepository` の参照関係を変えない | import / stub 構造 / use case コンストラクタ呼び出しすべて無変更 |
| 3.1 | 戻り値型 `Unit` が Kotlin コンパイル時または code review 時に判別しやすい記法 | block body は Kotlin 言語仕様により戻り値が `Unit` 固定。上記「選択した記法と理由」参照 |
| 3.2 | coroutine スコープの非同期処理を JUnit 4 制約を満たしたまま呼ぶ | `runBlocking { ... }` を block body 内の statement として呼ぶ形に変更 |
| NFR 1.1 | 製品コード変更なし | `git diff` で `app/src/main` 配下無変更 |
| NFR 1.2 | JVM unit test 変更なし | `git diff` で `app/src/test` 配下無変更 |
| NFR 1.3 | `SignatureMismatchTest` 以外の androidTest 変更なし | `git diff` で他 androidTest 無変更 |
| NFR 1.4 | ビルド設定変更なし | `git diff` で `build.gradle.kts` / `libs.versions.toml` / ProGuard / manifest 無変更 |
| NFR 2.1 | CI の JUnit XML で `SignatureMismatchTest` の 2 件が `passed` として記録 | **CI 側 `connectedDebugAndroidTest (API 34)` で最終検証** |
| NFR 2.2 | `./gradlew :app:compileDebugAndroidTestKotlin` が `BUILD SUCCESSFUL` で終了 | ローカル実行で確認済み (下記「実行コマンドと結果」§1) |

## 実行コマンドと結果

| Step | コマンド | 結果 | 所要時間 |
|---|---|---|---|
| 1 | `./gradlew :app:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** | 2m 10s |
| 2 | `./gradlew :app:testDebugUnitTest` | BUILD FAILED **(無関係な既存失敗)** — 詳細は下記 | 2m 17s |
| 3 | `./gradlew :app:connectedDebugAndroidTest (API 34)` | **CI 側で検証** (emulator 必要のためローカル skip) | — |

実行環境: `JAVA_HOME=/home/hitoshi/sdks/jdk-17`,
`ANDROID_HOME=/home/hitoshi/sdks/android-sdk` を export。

### Step 2 の失敗内容と本変更との切り分け

`testDebugUnitTest` で 680 件中 1 件失敗:

```
AppInfoProviderTest > get_returnsVersionNameFromBuildGradle FAILED
    com.google.common.truth.ComparisonFailureWithFacts at AppInfoProviderTest.kt:30
```

これは **本変更前の HEAD (`60a300f`) でも同じ 1 件が失敗する** ことを
`git stash` 状態で再実行して確認済み。`AppInfoProviderTest` は
`app/src/test/java/...` 配下の JVM unit test で、本 Issue の修正対象である
`app/src/androidTest/java/.../SignatureMismatchTest.kt` とは source set も
ファイルも別。

したがって本変更は unit test source set に対し **regression を生じていない**
（既存の独立した failing test が残っているのみ）。この既存失敗は本 Issue の
スコープ外であり、別 Issue で扱うべき問題と判断する。

### Step 3 は CI のみで検証

`connectedDebugAndroidTest` は emulator もしくは実機を要するため、本ローカル
環境では実行せず PR 連動の CI job (`connectedDebugAndroidTest (API 34)`) で
最終検証する。requirement 1.1〜1.3 / NFR 2.1 はこの CI 経由で確認される。

## 設計判断と deviations

- **deviation なし**: requirements.md / 実装ヒントの想定範囲内で完結する
  1 ファイル / 4 行差分（実質）の semantic fix。
- **ambiguity なし**: 「block body / explicit return type / trailing Unit の
  どれを採るか」のみが Developer 判断に委ねられており、Req 3.1 の趣旨
  (「再発しにくい記法」) から block body を選択した。

## Reviewer への申し送り

1. **CI 確認**: ローカルでは `compileDebugAndroidTestKotlin` (Req 1.4 / NFR
   2.2) までしか検証していない。PR の `connectedDebugAndroidTest (API 34)`
   job で `SignatureMismatchTest` 2 件が `passed` として記録されることを
   最終確認してほしい (Req 1.1〜1.3 / NFR 2.1)。
2. **`AppInfoProviderTest` の既存失敗**: 本変更と独立した既存 failing test
   が JVM unit test 側に 1 件ある。本 Issue のスコープ外。必要なら別 Issue
   として切り出すことを推奨。
3. **block body 採用理由**: Req 3.1 の「再発しにくい記法」観点で
   expression body (`: Unit = runBlocking { ... }`) より block body を優先
   した。Reviewer が好みを持つ場合は議論可能だが、AC は両者とも満たす。

## Push 状況

- ローカル commit 作成のみ。`git push` は実施していない。PR 作成も実施しない。
  本ステージは Reviewer ゲート前のため、後段（PM / orchestrator）に委ねる。

## 関連

- Issue: #111
- 前段: Issue #98 (compile error 系) / Issue #106 (mockk-android runtime 系)
- Background: PR #97 (Issue #94) で導入された `connectedDebugAndroidTest (API
  34)` job の latent debt の最後のピース
