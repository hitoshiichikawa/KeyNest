# Review Notes — Issue #98 (round 1)

## Summary

差分は `SignatureMismatchTest.kt` への +9 行 (import 3 行 + override 6 行) と
spec 文書のみ。本 Issue は mechanical な追従修正で、`tasks.md` / `design.md` は
作成されていない (requirements.md と impl-notes.md のみ)。AC が requirements.md
内の 4 項目に集約されているため、その 4 項目で AC カバレッジ判定を行った。
6 つの override シグネチャは `CredentialRepository` interface 定義
(`app/src/main/java/.../domain/repository/CredentialRepository.kt:24-100`) と
完全一致。既存 6 override / 2 テストメソッド / `runBlocking` 構造は無変更。
製品コード変更ゼロ (`git diff develop..HEAD -- app/src/main/` empty) 。

## AC Coverage

- AC-1 (compileDebugAndroidTestKotlin が BUILD SUCCESSFUL): **covered** — 6 メソッドは
  すべて interface シグネチャと一致
  (`SignatureMismatchTest.kt:91-96` ↔ `CredentialRepository.kt:54,61,67,78,89,100`)。
  impl-notes.md §「実行コマンドと結果」に 28.5s で BUILD SUCCESSFUL の実行ログ記載。
- AC-2 (既存 2 テストケースのロジック・assertion・テストデータ・runBlocking 構造を保持):
  **covered** — `git diff` 上 `@Test` 関数 (`SignatureMismatchTest.kt:36-66`) /
  `rec(...)` / 既存 6 override (`save` / `update` / `delete` / `findByPackage` /
  `findById` / `observeAll`) はいずれも無変更。差分は anonymous object 末尾への
  追加 6 行と import 3 行のみ。
- AC-3 (未 override メソッドが呼ばれたら明示失敗): **covered** — suspend 系 3 件
  (`markUsed` / `duplicate` / `clearAll`) は `error("n/a")` で fail-fast。Flow 系
  3 件 (`observeBySort` / `observeRecentlyUsed` / `observeMetadata`) は
  `flowOf(emptyList())` / `emptyFlow()`。requirements.md §1「non-suspend / Flow 系は
  `emptyFlow()` または `flowOf(emptyList())` で良い」「silent fail（空 VaultMetadata
  返却）は禁止」に従っており、`VaultMetadata` インスタンスは構築していない (= silent
  fail 回避)。
- AC-4 (製品コード一切変更しない): **covered** — `git diff develop..HEAD -- app/src/main/`
  の結果が空。`CredentialRepository.kt` / `CredentialRepositoryImpl.kt` 等は touched
  されていない。

## Findings

### AC 未カバー

- None

### Missing Test

- None — 本 Issue の AC は新規 `@Test` 追加を**禁止** (requirements.md §「対象外」)
  しており、検証手段は `compileDebugAndroidTestKotlin` 通過のみ。impl-notes.md に
  ローカルで BUILD SUCCESSFUL を確認した記録あり。Runtime テスト
  (`connectedDebugAndroidTest`) は emulator 要件のため CI 側に委任が requirements.md
  上明示済み。

### Boundary 逸脱

- None — `tasks.md` が存在しない (mechanical な単一ファイル修正のため未作成) が、
  requirements.md §「スコープ」「対象外」「実装上の指針 §3」に列挙された boundary
  (1 ファイル / 既存 override の振る舞い保持 / interface 不変 / 製品コード不変 /
  新規 `@Test` なし / `n/a` メッセージ統一) のいずれも逸脱なし。
  - メッセージ表記は既存 `"n/a"` と統一されている (impl-notes.md §「追加した
    override 6 件」記載通り)。
  - `Credential` の FQN 直書きは既存 `observeAll` と同スタイルを踏襲しており、
    requirements.md §「実装上の指針 §3 テストロジック保護」が要求する「偶発的な
    リファクタを入れない」方針とも整合。

## Verdict Rationale

requirements.md の 4 AC すべて covered、boundary 逸脱なし、製品コード変更ゼロ、
既存テストロジック完全保持。impl-notes.md で `observeMetadata` の `emptyFlow()`
選択理由も requirements.md §1 の許容範囲内 (「`emptyFlow()` で良い、ただし silent
fail は禁止」) に収まっている。テストスタブ追従の最小修正として完全に要件を満たす。

RESULT: approve
