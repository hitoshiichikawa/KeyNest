# Review Notes for #106 (Round 1)

## Summary

Issue #106 は API 34 emulator 上の `FillRequestLatencyTest` / `SignatureMismatchTest`
の runtime failure（`mockk<PackageSignatureResolver>()` 由来）を解消することが目的。
Developer は方針 (B)（anonymous-object stub への置換）を採用し、製品コード 1
ファイル（`open class` + `open fun` 化）と androidTest 2 ファイルを変更している。
レビュー対象は `git diff develop..HEAD` の 3 コードファイル（impl-notes.md /
requirements.md は対象外）。tasks.md と design.md は本 spec に存在しないため、
Boundary は requirements.md「変更候補ファイル一覧」(方針 B) と「対象外」セクションを
基準に判定する。

## AC Coverage

- **AC-1** (`FillRequestLatencyTest` が API 34 emulator で `ExceptionInInitializerError` を出さずに完了): covered
  - `mockk<PackageSignatureResolver>()` を `object : PackageSignatureResolver(...)` に置換し、
    mockk-android のクラス生成経路を完全に排除している
    （`FillRequestLatencyTest.kt:65-69`）。`io.mockk.*` import も削除済み（同 7-8 行削除）。
  - 最終 verification は CI emulator 上で行う必要があり（impl-notes §4）、
    ローカルで `compileDebugAndroidTestKotlin` が pass している点まで本レビューで確認。
- **AC-2** (`SignatureMismatchTest` runner instantiation 成功 + 2 ケース実行): covered
  - 2 箇所の `mockk<PackageSignatureResolver> { ... }` を共通 helper
    `stubResolver(hash)` 経由の anonymous-object に置換（`SignatureMismatchTest.kt:44, 55, 63-69`）。
    `io.mockk.*` import 削除済み。テストメソッド名
    (`candidateFilter_dropsMismatched_andRetainsMatched`,
    `candidateFilter_returnsEmpty_whenAllAreMismatched`) は維持。
- **AC-3** (latency assertion 値 300/600 維持): covered
  - `FillRequestLatencyTest.kt:99-100` で `isLessThan(300.0)` / `isLessThan(600.0)` が
    変更されていないことを diff で確認。
- **AC-4** (テスト意図保持: signature mismatch filtering / fill latency NFR): covered
  - `SignatureMismatchTest.stubResolver` は `if (packageName == "com.example.target") hash else null`
    と分岐し、既存 mockk stub（`every { resolveSha256("com.example.target") } returns matching`）
    と等価な動作。filter 検証の意図は維持。
  - `FillRequestLatencyTest` の anonymous-object stub は `resolveSha256` が常に
    `matchingHash` を返す（既存 mockk stub と同じ単一 packageName 想定）。
    setup（100 件投入 / backfill / 5 warm-up / 50 計測ループ）も `Before` と
    `Test` 内のロジック含めて維持されている。

## Missing Tests

なし。本 Issue は既存テストを runtime で動かす方向の修正であり、新規 AC を導入していない。
AC-1 / AC-2 は対象テスト自身が AC の実行体（pass 自体が判定）、AC-3 / AC-4 は
assertion 値・テスト意図の維持という性質上、追加テスト不要。

## Boundary Check

requirements.md「変更候補ファイル一覧」(方針 B 列) との突合せ:

- `app/src/androidTest/.../perf/FillRequestLatencyTest.kt`: 変更（許容範囲内、想定通り）
- `app/src/androidTest/.../security/SignatureMismatchTest.kt`: 変更（許容範囲内、想定通り）
- `gradle/libs.versions.toml`: 不変（OK、方針 B は dependency 変更なしを想定）
- `app/build.gradle.kts` / `app/proguard-rules.pro` / `androidTest/AndroidManifest.xml`: 不変（OK）
- `app/src/main/.../util/PackageSignatureResolver.kt`: 変更（`open class` 化 + `open fun` 化）
  - requirements.md 方針 B の「製品コードに `open` 修飾子追加」がスコープ判断事項
    として明示されている（requirements.md §確認事項 2、§169-176 リスク欄）。
    impl-notes §1, §5.2 で `open` 化のみ採用（interface 抽出は不採用）と判断
    根拠を記載済み。**「製品コード全方針で不変」とは方針 A 列の脚注のみで、
    方針 B 採択時は最小 `open` 化が想定範囲内**。よって boundary 逸脱なし。
- `app/src/test/...` (unit test): 変更なし（OK、requirements.md「既存 unit test 全方針で不変」を満たす）
- `testImplementation(libs.mockk)` ripple: なし（unit test 側に変更なし、OK）
- CI workflow ファイル: 変更なし（OK）

「対象外」セクションの禁止事項チェック:
- latency assertion 値変更: なし（OK）
- テストメソッド名 rename: なし（OK）
- 既存 setup ロジック改変: なし（OK、`@Before populateCredentials` / backfill /
  warm-up / 50 計測ループは未変更）
- テスト意図変更: なし（OK、上記 AC-4 参照）

## Findings

### AC 未カバー

なし。

### Missing test

なし。

### Boundary 逸脱

なし。製品コード `PackageSignatureResolver.kt` の `open` 化は方針 B 採択時に
requirements.md が想定する最小スコープ内で、impl-notes に判断根拠が記載されている。
DI 配線・interface 抽出には及んでいない。

## Decision Rationale

AC-1〜AC-4 全てが diff 上でカバーされている。Boundary 逸脱なし。製品コードへの
`open` 修飾子追加は requirements.md 方針 B のリスク欄および確認事項で想定済みの
変更で、最小スコープに収まっている。AC-1 / AC-2 の最終 verification は CI emulator
に依存するが、本レビューの判定対象（AC 未カバー / missing test / boundary 逸脱）
としては問題なし。

RESULT: approve
