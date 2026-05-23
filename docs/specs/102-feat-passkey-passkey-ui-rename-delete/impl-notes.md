# Implementation Notes — Issue #102

## Implementation Notes

### Task 1

- **採用方針**: design.md §4.4 / §5.3 の擬似コードを忠実に踏襲し、Repository 層のみで
  rename API (`update`) を加法公開し、`delete` を `database.withTransaction { ... }` で
  原子化する。public シグネチャ (`PasskeyRepository.delete(credentialId): DeletePasskeyResult`)
  は変更しない (Req 6.4)。
- **重要な判断**:
  - **Keystore alias prefix は実装の `passkey_<credentialId>` を踏襲**: design.md §1.4 /
    requirements.md NFR 2.1 等の本文に `keynest_passkey_<credentialId>` という旧名が散見される
    が、これは #99 開発初期の暫定 prefix で、#107 Reviewer round=2 で `passkey_<credentialId>`
    に reshape 済 (`PasskeyRepositoryImpl.aliasFor` KDoc に明記)。本 Issue は data 層境界のみを
    触る Task 1 なので、`aliasFor(credentialId)` 経由で取得して既存テスト群との整合を保った。
    UI / 文言層に到達するまでに spec 側の整合を確認してもらえると好ましい (「確認事項 1」参照)。
  - **既存 mock-based `delete_returns_*` 3 件を real-Room 化**: `delete` を
    `withTransaction` で囲うと、relaxed `mockk<KeyNestDatabase>` ではブロックが
    完了せず `UncompletedCoroutinesError` で hang する。これは tasks.md §1.3 が
    「既存 `delete_returnsSuccess_*` の assertion を `withTransaction` 経由でも PASS する
    ことを再確認 (必要なら assertion 強化)」と既に許容しているため、in-memory roomDb
    fixture に置き換えて assertion を強化 (Success 後の row 不在 / alias-absent 時の
    deleteEntry 非呼び出し) した。元の検証意図 (Success / alias absent / KeystoreCleanupFailed
    の 3 経路) は完全に保存。
  - **`cause` の identity assertion を class + message に切り替え**: Room の
    `withTransaction` は CoroutineDebugging で例外 instance を wrap して再 throw するため
    `isSameInstanceAs(boom)` は失敗する。既存 `signWithIncrement_rollsBackSignCount_whenSignerThrows`
    (line 379-380) と同パターンに揃えた。
  - **Task 1.3 (c) 呼び出し順序検証**: `mockk.verifyOrder` は mock 同士の順序しか保証
    できない (DAO 側を real Room にしているので使えない)。代わりに `mockKs.deleteEntry`
    の `answers { }` 内で `roomDao.findByCredentialId(id)` を `runBlocking` で読み、
    「KS deleteEntry 呼出時点で DB row は既に消えている = DB-first」を観測する手法を採用。
    transaction 内同一スレッド・同一 DB なので row visibility は信頼できる。
- **残存課題**:
  - **Task 2 (UI 状態モデル) 着手前に**「Keystore alias prefix が `passkey_<credentialId>`
    であることを spec 本文との差分として impl-notes に記録した」点を確認すること。UI 層は
    alias prefix に直接触らないため後続 task の実装には影響しないが、PR レビューで指摘
    される可能性あり。
  - `DeletePasskeyResult.KeystoreCleanupFailed` の KDoc (`domain/model/DeletePasskeyResult.kt`)
    は旧 semantics 「Row removed but the Keystore alias delete raised」のままで、本 Issue で
    「両方 untouched」に意味が変わった。boundary 外 (PasskeyRepository / PasskeyRepositoryImpl
    に含まれない) ため Task 1 では書き換え対象外としたが、後続 task または別 Issue で
    調整が望ましい (「確認事項 2」参照)。

## 確認事項

1. **Keystore alias prefix の spec 不整合**: requirements.md / design.md / tasks.md
   本文では `keynest_passkey_<credentialId>` 表記だが、実装は #107 Reviewer round=2 で
   `passkey_<credentialId>` に reshape 済。本 Task 1 は `aliasFor(credentialId)` を経由する
   ので影響なく完了したが、後続 task の Robolectric テスト等で alias 名が登場する場合は
   実装側の `passkey_<id>` を使うこと。spec 本文の書き換えは Architect の領分なので
   ここでは触っていない。

2. **`DeletePasskeyResult.KeystoreCleanupFailed` KDoc の旧 semantics 残存**:
   `app/src/main/java/.../domain/model/DeletePasskeyResult.kt` の `KeystoreCleanupFailed` の
   KDoc は「Row removed but the Keystore alias delete raised. Caller may proceed (e.g.
   continue to a new INSERT for the overwrite case) and surface the orphaned alias
   separately」と書かれている。これは Issue #102 反映前の旧実装に基づく説明で、本 Issue
   完了後は「両方 untouched (DB rollback 済 / alias 残存)」が正しい semantics。当該ファイルは
   Task 1.1 / 1.2 の `_Boundary:_` 外なので本 Task では書き換えていない。Task 2 以降または
   別 Issue で更新が望ましい。

## Requirement → Test 担保マッピング (Task 1 範囲)

- Req 2.5 / 2.10 / 6.4 (update API シグネチャ / 他列保持 / signature backward compat):
  `update_persistsEntity_andDoesNotChangeOtherFields` で 14 列の差分なしを検証
- Req 3.6 (DB first ordering): `delete_callOrder_isDbFirstThenKeystore`
- Req 3.7 (KS 失敗時 DB rollback): `delete_returnsKeystoreCleanupFailed_andDbRowIsRestored_whenKeyStoreThrows`
- Req 7.5 (call order verify): `delete_callOrder_isDbFirstThenKeystore`
- Req 7.6 (rollback observable via findByCredentialId): `delete_returnsKeystoreCleanupFailed_andDbRowIsRestored_whenKeyStoreThrows`
- Req 7.7 (update が他列を変更しない): `update_persistsEntity_andDoesNotChangeOtherFields`
- Req 6.4 (delete のシグネチャ不変): 既存 3 件 `delete_returns_*` が assertion を保ったまま
  PASS していること & `PasskeyRepository` interface の `delete(credentialId): DeletePasskeyResult`
  シグネチャに diff なし
