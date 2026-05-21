# Requirements Document

## Issue 概要

PR #97 (Issue #94) で API 34 emulator job が CI に追加された結果、
`connectedDebugAndroidTest (API 34)` で以下 2 件の androidTest が **runtime** に
失敗することが判明した。compile は通過しており、`androidTest` source set の
クラスロード / インスタンス化フェーズでこける（Issue #98 / PR #105 の「stub
override 欠落 → compile error」とは別系統）。

### 観測された 2 件の runtime failure

1. **`FillRequestLatencyTest.resolveCandidates_medianLatency_within300ms_for100Credentials`**
   - 症状: `java.lang.ExceptionInInitializerError`
   - 発生位置: `FillRequestLatencyTest.kt:111` の `invokeSuspend` (※当該テストは
     現状 102 行までだが、コルーチン bytecode 上の合成行番号と思われる)
   - 失敗フェーズ: テスト本体実行中（クラス初期化子のいずれかが throw）

2. **`SignatureMismatchTest.initializationError`**
   - 症状: `java.lang.RuntimeException: Failed to instantiate test runner class
     androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner`
   - 失敗フェーズ: AndroidJUnit4 ランナーがテストクラスを構築する直前

### 共通点

- 両テストとも `io.mockk.mockk<PackageSignatureResolver>()` を使用
  （`FillRequestLatencyTest.kt:67`, `SignatureMismatchTest.kt:45 / 58`）
- 両テストとも `androidTestImplementation(libs.mockk.android)`（mockk 1.13.12）
  経由でロードされる
- 両テストとも本 CI 追加（PR #97）まで Android 14 emulator 上で走った実績がない
- 製品コード（`PackageSignatureResolver` 等）は未変更で、Issue #98 / PR #105 の
  スコープ（stub override 欠落）とは独立

CI run: <https://github.com/hitoshiichikawa/KeyNest/actions/runs/26222618603>

備考: CI ログのうち `--log-failed` で取得できる範囲には Failure 1/2 の Caused-by
チェイン（Byte Buddy / objenesis / hidden API 等の根因情報）が含まれず、Issue
本文に転記済みの一段目スタックトレースが現時点で得られる最大解像度。詳細根因
（mockk-android 由来か、API 34 の hidden API restriction 由来か）の特定は
Developer の実装フェーズで再現確認する想定。

## スコープ

### 変更候補ファイル一覧（採用方針により変化する）

| 対象 | 方針 (A) bump | 方針 (B) stub 置換 | 方針 (C) runner 構成 |
|---|---|---|---|
| `gradle/libs.versions.toml` (`mockk = "1.13.12"` 行) | **変更**（バージョン更新） | 不変 | 不変 |
| `app/src/androidTest/.../perf/FillRequestLatencyTest.kt` | 不変 | **変更**（`mockk<...>()` → anonymous-object stub） | 不変 |
| `app/src/androidTest/.../security/SignatureMismatchTest.kt` | 不変 | **変更**（同上、2 箇所） | 不変 |
| `app/build.gradle.kts` (`testInstrumentationRunner` / `packaging` / dependency 追加) | 不変または微調整 | 不変 | **変更**（ランナー差し替え / mockk-agent 追加等） |
| `app/proguard-rules.pro` | 不変 | 不変 | **変更の可能性**（mockk 関連 keep / Byte Buddy 関連） |
| `app/src/androidTest/AndroidManifest.xml`（存在しなければ新規） | 不変 | 不変 | **変更の可能性**（`tools:targetApi` / hidden-API workaround flag） |
| 製品コード (`app/src/main/...`) | **全方針で不変** | 同左 | 同左 |
| 既存 unit test (`app/src/test/...`) | **全方針で不変** | 同左 | 同左 |

### 共通制約

- `androidTestImplementation(libs.mockk.android)` の依存自体は外さない
- `testImplementation(libs.mockk)` には ripple させない（Issue 本文「unit test 側
  への ripple は最小にしてほしい」）

## 対象外（非スコープ）

Issue 本文「スコープ外」の転記:

- 性能 NFR (NFR 2.1 / 2.2) の数値見直し
- 他の androidTest 全体の見直し（観測された 2 件以外）
- mockk から別 mocking ライブラリへの全面移行

加えて本要件で禁止する変更:

- **`testImplementation(libs.mockk)` を unit test 側から外す変更は禁止**
  （`app/src/test/...` 配下の既存 unit test がコンパイル不能になる）
- **テストの意図変更禁止**:
  - `SignatureMismatchTest` の意図（signature mismatch / null signature の
    candidate フィルタリング検証 — Req 4.2 / 4.3 / 4.4）を変えない
  - `FillRequestLatencyTest` の意図（onFillRequest 配下 resolve パスの
    latency NFR 検証 — NFR 2.1 / 2.2）を変えない
- **`FillRequestLatencyTest` の latency assertion 値変更禁止**
  （`isLessThan(300.0)` / `isLessThan(600.0)` の数値は維持）
- **テストメソッド名の rename 禁止**:
  - `resolveCandidates_medianLatency_within300ms_for100Credentials`
  - `candidateFilter_dropsMismatched_andRetainsMatched`
  - `candidateFilter_returnsEmpty_whenAllAreMismatched`
- 既存 setup ロジック（`ServiceLocator.initialize` / 100 件投入 /
  backfill / warm-up / 50 計測ループ等）の改変禁止
- CI workflow ファイル変更（runtime failure が解消すれば既存 workflow が
  そのまま走る）

## 受入基準 (EARS)

Issue 本文 AC の転記:

- **AC-1**: When `connectedDebugAndroidTest` を API 34 emulator
  (`reactivecircus/android-emulator-runner@v2`) 上で実行したとき, the
  `FillRequestLatencyTest` shall `ExceptionInInitializerError` を出さずに
  完了する。
- **AC-2**: When `connectedDebugAndroidTest` を API 34 emulator 上で実行した
  とき, the `SignatureMismatchTest` shall test runner instantiation に成功し、
  内部の 2 テストケース
  (`candidateFilter_dropsMismatched_andRetainsMatched`,
  `candidateFilter_returnsEmpty_whenAllAreMismatched`) が実行される。
- **AC-3**: The `FillRequestLatencyTest` の latency assertion
  (median <= 300ms, p95 <= 600ms) shall 本 Issue では変更しない。
- **AC-4**: If 修正のために test fixture や mock 戦略を変える場合, the change
  shall 既存のテスト意図 (signature mismatch filtering / fill latency NFR の
  検証) を保持すること。

### ローカル / CI での検証コマンド

| コマンド | 検証対象 | 実行環境 |
|---|---|---|
| `./gradlew :app:compileDebugAndroidTestKotlin` | 方針 (B) で書き換えた anonymous-object stub のコンパイル通過 | ローカル / CI 双方 |
| `./gradlew :app:testDebugUnitTest` | JVM unit test 退行（特に `PackageSignatureResolverTest` 等）が出ていないこと | ローカル / CI 双方 |
| `./gradlew :app:connectedDebugAndroidTest` | AC-1 / AC-2 の最終確認（emulator 必須） | **CI 専用**（GitHub Actions `connectedDebugAndroidTest (API 34)` job） |

ローカル開発機に Android emulator がない場合、AC-1 / AC-2 の最終 verification
は CI run でのみ確認可能。Developer はローカルで上 2 つを pass させてから
push し、CI の `connectedDebugAndroidTest (API 34)` が緑になることを以て完了
判定する。

## 実装方針の選択肢と trade-off

Issue 本文「修正案として『mockk バージョンの bump』『mock 戦略を anonymous-object
stub に変更』『テストランナー構成の見直し』など複数の方向がありうる。
implementer の判断に委ねる」の前提で、3 案を整理する。

### (A) `mockk-android` を新版に bump

**概要**: `gradle/libs.versions.toml` の `mockk = "1.13.12"` を新版
（例: 1.13.13 以降、または 1.14.x 系）に上げる。`mockk` と `mockk-android` は
同じ `mockk` version ref を共有しているため、unit test 側の `mockk` も同時に
上がる点に注意（unit test 側でも問題が出ないか要回帰確認）。

| 観点 | 内容 |
|---|---|
| メリット | テストコード 0 行変更。意図変更リスクが最小。 |
| メリット | 同 root cause を持つ他の androidTest（将来追加分）も自動的に救われる。 |
| リスク | 上流に「Android 14 / API 34 fix」が入っているかの確認が必須（リリースノート / GitHub issues の確認）。入っていなければ無効打。 |
| リスク | mockk 1.13.12 → 1.14.x への jump になる場合、unit test 側に他の incompatible 変更が混じる可能性（DSL 細部の挙動変化など）。 |
| ripple | unit test も含めて全 mockk 利用箇所に影響 → Issue 本文「ripple 最小」の精神には反する可能性。`testImplementation(libs.mockk)` を外しはしないが、バージョンは強制的に上がる。 |
| 検証コスト | `:app:testDebugUnitTest` + `:app:connectedDebugAndroidTest` 双方の pass が必要。 |

### (B) `mockk<PackageSignatureResolver>()` を anonymous-object stub に置換

**概要**: 2 ファイル分の `mockk<PackageSignatureResolver>()` を、
`PackageSignatureResolver` の non-final な public API を override する
anonymous object に置き換える。`PackageSignatureResolver` の primary
constructor は `(pm: PackageManager, sdkVersion: Int)` を要求するため、
そのまま anonymous-object で extends すると constructor 引数を渡す必要がある
（`pm` には instrumentation context 経由のものを渡すか、ダミー stub
`PackageManager` を作る等の工夫が必要）。

実装パターンの候補:
1. `object : PackageSignatureResolver(InstrumentationRegistry....context.packageManager) { override fun resolveSha256(packageName: String) = matchingHash }`
   - 副作用: `pm` は実際には触られないが、コンストラクタには valid 値が必要
2. `PackageSignatureResolver` の primary constructor を `open` 化済みなので、
   anonymous extension は kotlin 的に可能（`class PackageSignatureResolver(...)`
   は default で final なので **本案を採るなら interface 抽出 or `open class`
   宣言が必要 → 製品コード変更が発生する**）

> **注**: 現状 `PackageSignatureResolver` は **final class**（`open` 修飾なし、
> interface 抽出もなし）。そのため anonymous-object 実装をシンプルに作るには、
> 製品側で `open class PackageSignatureResolver(...)` 化、または `interface
> PackageSignatureResolver { fun resolveSha256(...): SigningHash? }` の抽出が
> 必要。これは「製品コード変更」に該当するため、本要件のスコープ判断が必要
> （後述「確認事項」参照）。

| 観点 | 内容 |
|---|---|
| メリット | mockk-android の Byte Buddy / objenesis 経路を完全に回避でき、CI 再現性が高い。 |
| メリット | unit test 側 `testImplementation(libs.mockk)` には ripple ゼロ（Issue 本文の制約を満たす）。 |
| リスク | `PackageSignatureResolver` を `open class` 化 or interface 抽出する **製品コード変更が伴う**（final class のため anonymous-object 不可）。 |
| リスク | anonymous-object 実装の "valid 引数" 確保のため `InstrumentationRegistry` 依存が増える可能性。 |
| ripple | androidTest 2 ファイル + 製品コード 1 ファイル（open 化 or interface 抽出のみ）。unit test には ripple なし。 |
| 検証コスト | `:app:compileDebugAndroidTestKotlin` + `:app:testDebugUnitTest`（既存 `PackageSignatureResolverTest` が動くこと）+ `:app:connectedDebugAndroidTest`。 |

### (C) test runner 構成見直し（ProGuard / packaging / hidden API exempt 等）

**概要**: テストコードに触れず、ランナー側で mockk-android の Byte Buddy 経路が
動くように構成する。例:
- `am set-debug-app` 経由で hidden API restriction を回避（API 28+ の制約）
- `mockk-agent-android` を追加（mockk 1.14 系で導入された inline mock agent）
- ProGuard rules 追加（debug build では minify 無効なので効果薄）
- `packaging.resources.excludes` で META-INF 競合解消

| 観点 | 内容 |
|---|---|
| メリット | テストコードに 1 行も触らない。 |
| リスク | API 34 emulator の hidden API restriction を本当に外せるかは試行錯誤になる（root 必須のケースあり）。 |
| リスク | 効果が不確実で、CI 上で何度も emulator job を回さないと検証できない（フィードバックループが極端に遅い）。 |
| ripple | gradle 設定 / proguard / マニフェスト数行。テストコードは 0 行。 |
| 検証コスト | CI emulator 上でのみ確認可能 → ローカル iterate 不可。 |

### 推奨

Developer の判断に委ねる。優先順序の参考:

1. **まず (A) を検証**（最も ripple 小・変更コスト 0）。mockk のリリース
   ノート（v1.13.13 以降、特に API 34 / Android 14 関連の Byte Buddy 更新が
   ないか）を確認し、該当 fix があれば (A) 採用。
2. (A) に該当 fix がない、または bump で別 regression が出る場合は **(B)**
   へ移行。製品コードの `PackageSignatureResolver` を `open class` 化 or
   `interface` 抽出する小規模変更で対応する。本案は「ripple 最小かつ確実」の
   観点で最も安全。
3. (C) は フィードバックループが遅く、再現性も不確実なため最終手段。

## PackageSignatureResolver の signature 確認

Developer が方針 (B) で anonymous-object stub を書く際の参照用に、
`app/src/main/java/io/github/hitoshiichikawa/keynest/util/PackageSignatureResolver.kt`
から関連箇所を抜粋する。

```kotlin
package io.github.hitoshiichikawa.keynest.util

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash

class PackageSignatureResolver(
    private val pm: PackageManager,
    private val sdkVersion: Int = Build.VERSION.SDK_INT,
) {
    fun resolveSha256(packageName: String): SigningHash?
    // ... private helpers omitted
}
```

### Stub 実装上の注意点

- **クラスは現在 `final`**（`open` 修飾なし）。anonymous-object で extends
  するには製品コード側を `open class PackageSignatureResolver(...)` に変更する
  か、`interface PackageSignatureResolver { fun resolveSha256(p: String): SigningHash? }`
  を新設し、現クラスを `PackageSignatureResolverImpl` に rename して
  interface 実装に切り替える、のいずれかが必要。
- **primary constructor は `(PackageManager, Int)` を要求**。stub では
  `pm` を実際には呼ばないが、constructor delegation で valid な
  `PackageManager` を渡す必要がある。
- 呼び出されるメソッドは `resolveSha256(packageName: String): SigningHash?`
  の 1 個のみ（既存テストでの利用箇所も `resolveSha256("com.example.target")`
  のみ）。
- 戻り値型 `SigningHash` は
  `io.github.hitoshiichikawa.keynest.domain.model.SigningHash`（`SigningHash?`
  なので `null` 返却も valid）。

### 既存テストでの mockk 利用箇所（修正対象、参考）

```kotlin
// FillRequestLatencyTest.kt:67-69
val sigResolver = mockk<PackageSignatureResolver>().also {
    every { it.resolveSha256(targetPackage) } returns matchingHash
}
```

```kotlin
// SignatureMismatchTest.kt:45-47
val sigResolver = mockk<PackageSignatureResolver> {
    every { resolveSha256("com.example.target") } returns matching
}

// SignatureMismatchTest.kt:58-60
val sigResolver = mockk<PackageSignatureResolver> {
    every { resolveSha256("com.example.target") } returns matching
}
```

いずれも **`resolveSha256(...)` を固定値 `matchingHash` / `matching` で返す**
だけのシンプルな stub。anonymous-object 化する場合は以下のような形になる:

```kotlin
val sigResolver = object : PackageSignatureResolver(
    InstrumentationRegistry.getInstrumentation().targetContext.packageManager
) {
    override fun resolveSha256(packageName: String): SigningHash? =
        if (packageName == "com.example.target") matching else null
}
```

> ※ 上記は `open class` 化を前提とした例示。Developer 判断で `interface` 抽出
> 方式を採るならよりシンプルに書ける（製品コード変更が少しだけ増える）。

## テスト方針

### 必須コマンド

| # | コマンド | 想定環境 | 必須 |
|---|---|---|---|
| 1 | `./gradlew :app:compileDebugAndroidTestKotlin` | ローカル | 必須 |
| 2 | `./gradlew :app:testDebugUnitTest` | ローカル | 必須（既存 unit test 退行確認、特に `PackageSignatureResolverTest`） |
| 3 | `./gradlew :app:connectedDebugAndroidTest` | CI (API 34 emulator) | 必須（AC-1 / AC-2 最終確認） |

### ローカル / CI 区分

- **ローカル**: 上 1, 2 で BUILD SUCCESSFUL を確認。emulator が手元にあれば
  3 もローカル実行可だが、CI と同条件を保証しづらいため CI run を正とする。
- **CI**: PR 作成時に既存 `connectedDebugAndroidTest (API 34)` workflow が
  自動起動。green を以て AC-1 / AC-2 達成と判定。

### 既存テスト退行確認の手順

- `:app:testDebugUnitTest` で `PackageSignatureResolverTest` を含む既存 JVM
  unit test が全 pass することを必ず確認。方針 (A) の bump、方針 (B) の
  open 化 / interface 抽出いずれでも unit test 側に副作用が出る可能性がある。
- 方針 (B) で `open class` 化 or interface 抽出を行う場合、既存
  `PackageSignatureResolverTest`（`app/src/test/java/.../util/PackageSignatureResolverTest.kt`）
  内で `PackageSignatureResolver` を直接 instantiate しているコードがあれば、
  そのまま動くことを確認（interface 抽出方式なら instantiate 側は `Impl`
  クラス参照に書き換えが必要）。
- 製品側の `AutofillService` 等で `PackageSignatureResolver` を DI している
  箇所がある場合、open 化 / interface 抽出の影響でビルドが壊れないかを
  `./gradlew :app:assembleDebug` で軽く確認しておくと安全。

## 確認事項

Developer / Reviewer が判断に迷いそうな点を列挙する（Issue 本文に明示されて
おらず、PM が一方的に決められない事項）。

1. **方針 (A) mockk bump の最低バージョン基準**
   - mockk 1.13.13 で十分か、1.14.x まで上げるべきか。リリースノートの
     確認結果が出てから判断。1.14.x は inline mock 周りの挙動が変わるため
     unit test 側の回帰リスクが上がる点に留意。
2. **方針 (B) 採用時の製品コード変更スコープ**
   - `PackageSignatureResolver` を `open class` 化するか、`interface` 抽出
     するか。後者の方が「mockk が要求していた抽象化」を素直に表現できる
     が、製品コード変更が増える（DI 配線箇所も書き換え）。
   - そもそも「製品コードに `open` 修飾子追加」がスコープ内として許容される
     か（Issue 本文「製品コード変更を含まない」とは明記されていないが、
     最小変更原則からは要確認）。
3. **方針 (B) で anonymous-object 内に `every { } returns` 同等のロジックを
   書く際、引数 `packageName` で分岐すべきか、常に固定値を返すか**
   - 既存テストでは `mockk` の `every { resolveSha256("com.example.target") }
     returns matching` で 1 packageName のみ受け付ける形。anonymous-object
     で再現するなら `if (packageName == "com.example.target") matching else null`
     とすべきか、引数無視で常に `matching` を返すか（テスト内で他の
     packageName は呼ばれない想定）。
4. **方針 (C) を試す価値があるか**
   - CI フィードバックループが遅いため、本要件では (A) / (B) を優先し、
     (C) は最終手段と位置付けて良いか。
5. **mockk-android 1.13.12 の Android 14 上での既知 issue 有無**
   - mockk GitHub repository の issue tracker / changelog で API 34 関連の
     既知 bug があるか調査。これにより (A) / (B) の優先順位が動く。
6. **`FillRequestLatencyTest.kt:111` の合成行番号の意味**
   - 現ファイルは 102 行までだが、stack trace は `:111` を指している。
     コルーチン bytecode の合成行のためであり、実 fix 対象行は `:67-69` の
     `mockk<PackageSignatureResolver>()` 周辺と解釈して問題ないか
     （Developer に最終確認を委ねる）。

## 関連 Issue / PR

- **Blocks**: PR #97 (Issue #94) — `connectedDebugAndroidTest (API 34)` CI
  workflow 導入 PR。本 Issue が解決しないと PR #97 の CI が常に red のまま
  merge できない。
- **Related (resolved)**: Issue #98 / PR #105 — 同じく PR #97 で顕在化した
  別系統の debt（compile error / stub override 欠落）の修正。本 Issue とは
  **独立した root cause**（compile error vs runtime failure）であり、同じ
  ファイル `SignatureMismatchTest.kt` を触るが症状・修正方針は別。
