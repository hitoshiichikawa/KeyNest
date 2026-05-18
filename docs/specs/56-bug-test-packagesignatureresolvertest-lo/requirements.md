# Requirements Document

## Introduction

`develop` baseline で unit test を実行すると、`PackageSignatureResolverTest` の 4 件と
`LockedFillResponseSecurityTest` の 1 件、計 5 件が常に失敗する状態が PR #55 のレビュー過程で
確認されている。原因は Android SDK の `android.jar` が提供する stub 実装のみで Plain JVM
JUnit を実行している環境において、`android.content.pm.Signature` インスタンスは生成できる一方で
`Signature.toByteArray()` が stub のみで実体を返さず、SHA-256 計算経路や parcel 出力経路で
期待値が得られないことに起因する。

本 Issue (#56) は、上記 5 件の test failure を解消し `./gradlew test` を `BUILD SUCCESSFUL`
に戻すための test-only タスクである。製品コード（`PackageSignatureResolver.kt` /
`FillResponseBuilder.kt` 等の autofill 系コード）には一切手を入れず、また既存テストが検証して
いる security 観点（signing certificate hash 計算、locked FillResponse の plaintext 非含有）を
弱体化させずに復旧することを必須とする。

## Scope

### 対象ファイル（テスト側のみ）

| 区分 | 対象 |
| --- | --- |
| Test | `app/src/test/java/com/example/keynest/util/PackageSignatureResolverTest.kt`（4 failures） |
| Test | `app/src/test/java/com/example/keynest/autofill/LockedFillResponseSecurityTest.kt`（1 failure） |
| Build | `app/build.gradle.kts`（依存追加が必要な場合に限り `testImplementation` のみ） |

> 注: Issue 本文では `inc/goodanswers/keynest/...` と記載されているが、実態の package は
> `com.example.keynest.*` であり、上記が正しいパスである。

## Requirements

### Requirement 1: テスト失敗の解消

**Objective:** As a KeyNest 開発者, I want `develop` baseline で常に失敗している 5 件の
unit test を pass 状態に戻したい, so that `./gradlew test` が `BUILD SUCCESSFUL` で完了し
以後の PR で baseline failure による誤検知が発生しない

#### Acceptance Criteria

1. When `./gradlew test` を実行したとき, the PackageSignatureResolverTest shall 4 件すべて pass する
2. When `./gradlew test` を実行したとき, the LockedFillResponseSecurityTest shall 1 件 pass する
3. When `./gradlew test` を実行したとき, the Gradle Build shall `BUILD SUCCESSFUL` で完了し、本 Issue 起票時点の baseline failure 5 件に対する新規 failure は 0 件となる

### Requirement 2: テストの検証観点維持

**Objective:** As a KeyNest 開発者・QA, I want 既存テストの `@Test` メソッド名・検証観点・
assertion 強度を変更せずに修正したい, so that signing certificate hash 計算と locked
FillResponse の plaintext 非含有という security 観点の回帰検知能力を失わない

#### Acceptance Criteria

1. The PackageSignatureResolverTest shall 既存の `@Test` メソッド名（`resolveSha256_api28_singleSigner_returnsCanonicalHash` / `resolveSha256_api28_multipleSigners_isOrderIndependent` / `resolveSha256_api28_uninstalledPackage_returnsNull` / `resolveSha256_api26_singleSigner_returnsHash` / `resolveSha256_api26_uninstalledPackage_returnsNull` / `resolveSha256_api26_emptySignatures_returnsNull` / `resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime`）と各テストの検証観点を維持する
2. The LockedFillResponseSecurityTest shall 既存の検証観点（locked FillResponse の parcel bytes に plaintext password 等の credential material が含まれず、placeholder 文字列のみが含まれること）を維持する
3. If 既存テストの assertion を緩める・`@Ignore` でスキップする・モックを過度に強めて検証対象ロジックを実質バイパスする等のテスト弱体化が含まれる場合, the Fix shall 受け入れられない（rejected）扱いとする
4. The Fix shall snapshot を盲目的に更新する等の「テスト側を書き換えて通す」アプローチを含まない

### Requirement 3: 製品コード非変更

**Objective:** As a KeyNest 開発者, I want 本修正で autofill 系の製品コードに一切変更を
加えずにテストのみで解決したい, so that 製品挙動への副作用リスクを 0 に抑え、レビュー範囲を
test スコープに限定できる

#### Acceptance Criteria

1. The Fix shall `app/src/main/java/com/example/keynest/util/PackageSignatureResolver.kt` を変更しない
2. The Fix shall `app/src/main/java/com/example/keynest/autofill/builder/FillResponseBuilder.kt` を変更しない
3. The Fix shall `app/src/main/java/com/example/keynest/autofill/` 配下の他の autofill 関連製品コードを変更しない
4. If 製品コード変更が不可避と判断される場合, the Fix shall 実装着手前に PM / 人間レビュワーへエスカレーションする

### Requirement 4: 依存追加の最小化

**Objective:** As a KeyNest 開発者, I want 解決のための依存追加を最小限に留めたい, so that
ビルド時間増・依存サーフェスの拡大・将来のメンテコストを最低限に抑えられる

#### Acceptance Criteria

1. Where `Signature` の class-level mock 等、既存依存のみで解決可能な手段が存在する場合, the Fix shall 依存追加なしで完了する
2. If 既存依存のみで解決困難で追加 test ランタイム導入が必要な場合, the Addition shall `app/build.gradle.kts` の `testImplementation` スコープに限定する
3. The Fix shall `implementation` / `api` 等の製品コード側依存スコープに新規依存を追加しない

## Non-Functional Requirements

### NFR 1: テスト実行時間

1. The PackageSignatureResolverTest shall 1 ファイルあたりの実行時間が本修正後も Plain JVM JUnit 相当の水準（1 file あたり数秒以内）を維持する
2. The LockedFillResponseSecurityTest shall 1 ファイルあたりの実行時間が本修正後も既存の test ランタイムで許容される水準（1 file あたり数十秒以内）を維持する

### NFR 2: 再現性・決定性

1. The Fixed Tests shall 連続 10 回実行しても flaky にならず、全実行で同一 pass 結果を返す
2. The Fixed Tests shall 実行マシンのタイムゾーン・ロケール・JDK minor バージョンに依存して pass / fail が変動しない

## Out of Scope

- 製品コード（`PackageSignatureResolver.kt` / `FillResponseBuilder.kt` 等）のリファクタや署名検証ロジック変更
- `androidTest`（instrumented test）への移動・分割そのものを目的とした構造変更（test ランタイム選定の結果としての移動は許容するが、本 Issue は「失敗 5 件を pass に戻す」ことを目的とする）
- 新規 security 観点テストの追加（既存観点の維持のみが対象）
- `develop` baseline で本 Issue とは無関係に失敗している他テストの修正
- CI 設定（GitHub Actions workflow 等）の変更
- 依存ライブラリのメジャーバージョンアップ

## Open Questions

1. **test ランタイム選定の最終判断**: Issue 本文では (a) `mockk<Signature>()` で
   `toByteArray()` を直接 mock する案 / (b) Robolectric 導入 / (c) `androidTest` 移動の
   3 案が提示され、(a) が「依存追加なし・Plain JVM 維持」のため推奨されている。Architect /
   Developer フェーズで (a) を採用するかの最終判断は委ねるが、(b) / (c) を採用する場合は
   Requirement 4.2 / Requirement 2.2（既存検証観点維持）に抵触しないことの明示が必要。
2. **`LockedFillResponseSecurityTest` の現行 `@RunWith(AndroidJUnit4::class)` / Robolectric
   依存の扱い**: 当該テストは現状 `androidx.test.ext.junit.runners.AndroidJUnit4` と
   `org.robolectric.annotation.Config` を import しているため、Plain JVM 単独では既に
   動作しない構造である。Robolectric が build classpath 上で実体として利用可能か、また
   Signature 関連のシム提供状態をどう扱うかは設計フェーズで確認が必要。
