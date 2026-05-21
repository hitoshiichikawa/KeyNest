# Implementation Notes — Issue #98

`fix(test): SignatureMismatchTest stub に CredentialRepository 後方追加 6 メソッドを補完`

## 概要

`androidTest` source set 内の `SignatureMismatchTest.kt` にある匿名
`CredentialRepository` 実装が、Issue #9 / #10 で追加された 6 メソッドの
override を欠いておりコンパイル失敗していた。本変更は **テストスタブにのみ**
最小の override を追加し、CI の `connectedDebugAndroidTest (API 34)` ジョブが
回せる状態に戻す。製品コード / interface 定義 / 既存テストロジックには
一切手を入れていない。

## 変更ファイル

| ファイル | 種別 | 追加 / 削除 |
|---|---|---|
| `app/src/androidTest/java/io/github/hitoshiichikawa/keynest/security/SignatureMismatchTest.kt` | test (modify) | +9 / -0 |
| `docs/specs/98-fix-test-signaturemismatchtest-stub-cred/impl-notes.md` | docs (new) | +N (this file) |

`git diff --stat` ベースで実コードは 1 ファイル / +9 行のみ（import 3 行 +
override 6 行）。

## 追加した import

`SignatureMismatchTest.kt` の import ブロックに以下 3 行を追加:

```kotlin
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder
import io.github.hitoshiichikawa.keynest.domain.model.VaultMetadata
import kotlinx.coroutines.flow.emptyFlow
```

- `CredentialSortOrder`: `observeBySort(order: CredentialSortOrder)` パラメータ型
- `VaultMetadata`: `observeMetadata(): Flow<VaultMetadata>` 戻り値型
- `emptyFlow`: `observeMetadata()` の stub 実装に使用

`Credential` は既存 `observeAll` で FQN 直書き
(`io.github.hitoshiichikawa.keynest.domain.model.Credential`) されていたため、
新規 `observeBySort` / `observeRecentlyUsed` でも同じ FQN を踏襲して差分を
最小化した（top-level import 追加で既存行を書き換える方が diff ノイズが
大きい）。Reviewer が import 化を望む場合は別途リファクタ可。

## 追加した override 6 件と戻り値選択理由

| # | メソッド | stub 実装 | 理由 |
|---|---|---|---|
| 1 | `observeBySort(order)` | `flowOf(emptyList())` | 既存 `observeAll` と同スタイル。Flow は collect されない想定だが、生成時に side-effect を起こさず safe。 |
| 2 | `observeRecentlyUsed(limit)` | `flowOf(emptyList())` | 同上。 |
| 3 | `markUsed(id, timestamp)` | `error("n/a")` | suspend / Unit。呼ばれたら fail-fast。AC「silent fail 禁止」を満たす。既存 `save` / `update` / `delete` と表記揃え。 |
| 4 | `duplicate(sourceId, timestamp)` | `error("n/a")` | suspend / `Result<CredentialId>`。silent な `Result.failure(...)` 返却は AC 違反のため throw 選択。 |
| 5 | `observeMetadata()` | `emptyFlow()` | requirements.md でも `emptyFlow()` または `flow { error("n/a") }` を許容。stub のテスト本体は `findByPackage` のみ叩くため collect 自体されない見込み。空 `VaultMetadata` を返すと silent fail 化するため、それを避けつつ最も軽量な `emptyFlow()` を選択。collect されると即 complete し、後続が timeout や `IllegalStateException("Flow has no elements")` で気付ける。 |
| 6 | `clearAll()` | `error("n/a")` | suspend / Unit。fail-fast。 |

注: 既存の `findByPackage(records.filter ...)` / `findById = null` /
`observeAll = flowOf(emptyList())` / `save` / `update` / `delete` / `findById`
の振る舞いは **完全に維持**。テスト本体 `candidateFilter_*` 2 ケースの
ロジック・assertion・テストデータ・`runBlocking` 構造は無変更。

## 実行コマンドと結果

| Step | コマンド | 結果 | 所要時間 |
|---|---|---|---|
| 1 | `./gradlew :app:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** | 28.5s |

実行時 env: `JAVA_HOME=/home/hitoshi/sdks/jdk-17`,
`ANDROID_HOME=/home/hitoshi/sdks/android-sdk` を export して実行。

副次的に `:app:compileDebugKotlin`（product code）も上記タスクの依存として
実行され成功している（既存の deprecation warning のみ、新規エラーなし）。

`:app:testDebugUnitTest` は本 Issue が `androidTest` source set のみの変更
であるため省略（requirements.md でも Developer 判断に委ねる旨記載）。

`./gradlew :app:connectedDebugAndroidTest` は emulator / 実機が必要なため
ローカル実行せず。CI 側で検証される。

## 受入基準セルフチェック

| AC | 状態 |
|---|---|
| `compileDebugAndroidTestKotlin` が `Object is not abstract ...` 解消で BUILD SUCCESSFUL | OK (28.5s) |
| 既存 2 テストケースのロジック / assertion / runBlocking 構造 変更なし | OK (diff 上 stub override 追加のみ) |
| 未 override メソッドが呼ばれたら明示失敗 | OK (suspend は `error("n/a")`、Flow は `flowOf/emptyFlow` で silent fail 回避：呼ばれない前提だが、`observeMetadata` collect 時は即 complete で気付ける) |
| 製品コード変更なし | OK (`git diff` で `app/src/main` 配下に変更なし) |

## 未解決の確認事項

なし。requirements.md の「確認事項」セクション通り mechanical な追従修正で
完結した。

唯一、選択上の判断ポイントとして `observeMetadata` の `emptyFlow()` vs
`flow { error("n/a") }` がある。本実装では `emptyFlow()` を採用した。理由は
requirements.md §1「呼ばれない想定の Flow 系は `emptyFlow()` で良い、ただし
silent fail（空 `VaultMetadata` 返却）は禁止」に従い、`VaultMetadata` インスタンス
は生成せず代わりに「要素ゼロの Flow」とすることで「呼ばれても無害（即完了）
だが意味のあるデータは返さない」を表現したため。Reviewer が
`flow { error("n/a") }` の方が AC「明示失敗」に近いと判断するなら 1 行変更で
切り替え可能。

## Reviewer への申し送り

1. **`Credential` の FQN 利用**: `observeBySort` / `observeRecentlyUsed` の
   戻り値型に既存 `observeAll` と揃えて
   `io.github.hitoshiichikawa.keynest.domain.model.Credential` を FQN で書いた。
   3 行とも一貫しているが、可読性向上のため top-level import 化して 3 行短く
   する案もある（その場合 diff +1 行 / -3 行）。本 PR ではノイズ最小化を優先
   した。
2. **`observeMetadata` の stub**: 上記「未解決」セクション参照。`emptyFlow()`
   vs `flow { error("n/a") }` の好み。
3. **CI 確認**: ローカルでは `compileDebugAndroidTestKotlin` までしか検証
   していない。`connectedDebugAndroidTest (API 34)` が PR #97 経由で初実行
   される際に runtime で `SignatureMismatchTest` 2 ケースが pass することを
   CI 側で確認してほしい。
4. **`testDebugUnitTest`**: 影響範囲外と判断して省略した。Reviewer が JVM
   unit test の退行確認を望む場合は別途 `./gradlew :app:testDebugUnitTest` を
   走らせてほしい（変更が androidTest source set 限定のため、unit test source
   set に波及するパスはないはず）。

## Push 状況

- ローカル commit 作成のみ。`git push` は実施していない。本ステージは
  Reviewer ゲート前のため、PR 作成も実施しない。Reviewer / PjM が同じ
  branch (`claude/issue-98-impl-fix-test-signaturemismatchtest-stub-cred`)
  を読みたい場合は push が必要だが、運用上の判断は次段へ委ねる。

## 関連

- Issue: #98
- Blocks: PR #97 (`connectedDebugAndroidTest` CI workflow)
- Background: Issue #9 / #10（追加された 6 メソッド由来）
