# Requirements Document

## Issue 概要

PR #97 (Issue #94) で GitHub Actions の `connectedDebugAndroidTest (API 34)`
job が初めて回り、`androidTest` source set の Kotlin コンパイルが失敗した。
失敗箇所は
`app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt:79`
の `stubRepo` 内 anonymous `CredentialRepository` 実装で、Issue #9 / #10 で
`CredentialRepository` interface に **後方追加された 6 メソッド** が override
されていないために `Object is not abstract and does not implement abstract member`
エラーで止まる。

本番側 `data.repository.CredentialRepositoryImpl` は 6 メソッドすべて実装済み
のため本 Issue は **テストスタブの追従修正のみ** であり、製品コード / 既存
テストロジック / interface 定義への変更は含まない。

## スコープ

### 変更対象ファイル（1 ファイルのみ）

| 対象 | 変更内容 |
|---|---|
| `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt` | `stubRepo(records)` 内 `object : CredentialRepository` に欠落している 6 メソッドの最小 override を追加する |

### 追加する override 6 件のシグネチャ一覧

`app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/CredentialRepository.kt`
に定義されている interface から、Issue #9 / #10 で追加された以下 6 メソッドの
override を `stubRepo` に追加する。

| # | Issue | メソッドシグネチャ | 種別 | 推奨 stub 実装 |
|---|---|---|---|---|
| 1 | #9 | `fun observeBySort(order: CredentialSortOrder): Flow<List<Credential>>` | non-suspend / Flow | `flowOf(emptyList())` |
| 2 | #9 | `fun observeRecentlyUsed(limit: Int): Flow<List<Credential>>` | non-suspend / Flow | `flowOf(emptyList())` |
| 3 | #9 | `suspend fun markUsed(id: CredentialId, timestamp: Long)` | suspend / Unit | `error("n/a")` |
| 4 | #9 | `suspend fun duplicate(sourceId: CredentialId, timestamp: Long): Result<CredentialId>` | suspend / `Result<CredentialId>` | `error("n/a")` |
| 5 | #10 | `fun observeMetadata(): Flow<VaultMetadata>` | non-suspend / Flow | `error("n/a")` または `emptyFlow()` |
| 6 | #10 | `suspend fun clearAll()` | suspend / Unit | `error("n/a")` |

備考:
- `CredentialSortOrder` / `VaultMetadata` / `DuplicateFailure` 等の必要 import は
  `io.github.hitoshiichikawa.keynest.domain.model.*` 配下。Developer 側で import
  追加が必要。
- 既存の 6 override (`save` / `update` / `delete` / `findByPackage` /
  `findById` / `observeAll`) はそのまま維持し、書き換えない。

## 対象外（非スコープ）

- `CredentialRepository` interface 定義自体の変更（`domain/repository/CredentialRepository.kt`
  には触らない）
- 製品コード (`data/repository/CredentialRepositoryImpl.kt` 等) の変更
- 既存 `@Test` メソッド `candidateFilter_dropsMismatched_andRetainsMatched` /
  `candidateFilter_returnsEmpty_whenAllAreMismatched` のロジック / assertion /
  メソッド名の変更
- 既存 stub の振る舞い（特に `findByPackage(records)` / `findById = null` /
  `observeAll = flowOf(emptyList())`）の変更
- 新規 `@Test` ケース追加
- 他の androidTest ファイル / 製品コードのコンパイルエラー / 警告対応（本 Issue
  対象外。別 Issue で対応）
- CI workflow ファイル変更（コンパイル通過すれば既存 workflow がそのまま動く）

## 受入基準 (EARS)

> Issue #98 本文の AC をそのまま転記。テストコマンドを追記。

- When `./gradlew :app:compileDebugAndroidTestKotlin` を実行したとき, the build
  shall `SignatureMismatchTest.kt` 由来のコンパイルエラー
  (`Object is not abstract and does not implement abstract member ...`) を解消
  し、`BUILD SUCCESSFUL` で終了する。
- The `SignatureMismatchTest` shall 既存の 2 テストケース
  (`candidateFilter_dropsMismatched_andRetainsMatched`,
  `candidateFilter_returnsEmpty_whenAllAreMismatched`) が機能的に変更されない
  こと（メソッド名・assertion・テストデータ・runBlocking 構造を保持）。
- If `CredentialRepository` の override されていないメソッドがテスト内で
  呼び出された場合, the stub shall `error("n/a")` 等で **明示的に失敗** する
  （silent fail / 空値返却で挙動を曖昧にしない）。
- The fix shall `app/src/main/java/.../domain/repository/CredentialRepository.kt`
  や `data/repository/CredentialRepositoryImpl.kt` などの製品コードを一切変更
  しない。

## 実装上の指針

### 1. Stub 戻り値の方針

Issue 本文 AC「override されていないメソッドが呼ばれたら明示失敗」を起点に、
**呼び出されない想定** の 6 メソッドに対して以下のパターンで実装する:

- **suspend 系（戻り値 `Unit` / 値）**: `error("n/a")` で fail-fast にする。
  `markUsed` / `duplicate` / `clearAll` が該当。
  - 例外メッセージは既存の `save` / `update` / `delete` / `findById` で使用
    されている `"n/a"` と表記を揃える（grep 容易性）。
- **non-suspend / Flow 系**: 既存 `observeAll = flowOf(emptyList())` と同じ
  スタイルで `emptyFlow()` または `flowOf(emptyList())` を返す。Flow は **生成
  時** には side effect を起こさないため、`error(...)` を直接 return すると
  collect される前に stub 構築時点で fail する可能性があり不適切。
  - `observeBySort` / `observeRecentlyUsed`: `flowOf(emptyList())`
  - `observeMetadata`: collect された場合のみ失敗させたいなら
    `flow { error("n/a") }`、collect されない想定なら `emptyFlow()` で良い。
    呼ばれないことが前提なので最終判断は Developer に委ねるが、**silent
    fail（空 VaultMetadata 返却）は AC 違反のため避ける**。

### 2. Interface 側 KDoc の遵守

`CredentialRepository.kt` の各メソッドには Issue #9 / #10 の Requirements
リンクが KDoc に含まれている (`Issue #9 Req 3.1, 3.2, 4.1, 4.3, 5.3, 5.4` /
`Issue #10 Req 4.1, ..., 7.5, 7.8`)。本 Issue では interface 本体には触らない
ため、これらの KDoc は維持される。stub 側に KDoc を追加する義務はない。

### 3. テストロジック保護

`stubRepo(records)` の既存 6 override は **完全に同一の振る舞い** を保つ:

- `save` / `update` / `delete` / `findById` は `error("n/a")` を継続
- `findByPackage(packageName)` は `records.filter { it.packageName == packageName }`
  を継続（テスト本体の filter ロジック検証に必要）
- `findById` は `null` 返却を継続
- `observeAll` は `flowOf(emptyList())` を継続

これらに対する偶発的なリファクタ / フォーマット変更も入れない。

### 4. import 追加

新規 override の戻り値型 (`CredentialSortOrder` / `VaultMetadata`) と
`Flow` 系演算子 (`emptyFlow` を使う場合) に対する import を追加する。既存
`flowOf` import は流用可能。

## テスト方針

### 必須確認コマンド

ローカル環境では以下のみを実施し、`BUILD SUCCESSFUL` で完了することを確認する:

```
./gradlew :app:compileDebugAndroidTestKotlin
```

このコマンドが pass すれば本 Issue の AC は満たされる（Issue 本文の AC が
コンパイル通過に絞られているため）。

### connectedAndroidTest はローカル非実施

`./gradlew :app:connectedDebugAndroidTest` は emulator / 実機接続を要するため、
**CI（GitHub Actions の `connectedDebugAndroidTest (API 34)` job）でのみ実行**
する。ローカルでは compile 通過の確認までで十分（Issue #94 の文脈で CI 側が
`connectedAndroidTest` を走らせる体制が整っている）。

### 既存テスト退行確認

念のため、変更影響範囲が JVM unit test に及んでいないことを以下で確認できる:

```
./gradlew :app:testDebugUnitTest
```

ただし本 Issue は androidTest source set のみの変更のため、JVM unit test には
影響しない見込み。実施するかは Developer の判断に委ねる。

## 確認事項

なし（Issue #98 本文「確認事項」セクションで「なし（mechanical な追従修正のみ、
挙動変更なし）」と明示済み）。

## 関連 Issue / PR

- **Blocks**: PR #97 (Issue #94) — `connectedDebugAndroidTest` CI workflow 導入
- **Related (closed, merged to develop, `staged-for-release`)**:
  - Issue #9: ソート順 / recently-used / markUsed / duplicate 追加
    （`observeBySort` / `observeRecentlyUsed` / `markUsed` / `duplicate` 由来）
  - Issue #10: Vault metadata / Danger Zone Vault clear
    （`observeMetadata` / `clearAll` 由来）
