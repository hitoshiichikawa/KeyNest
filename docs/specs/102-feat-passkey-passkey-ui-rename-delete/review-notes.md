# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-23T02:19:19Z -->

## Reviewed Scope

- Branch: claude/issue-102-impl-feat-passkey-passkey-ui-rename-delete
- HEAD commit: be75f34a08f8564c067645ee82d255e75c1a177b
- Compared to: develop..HEAD
- Mode: per-task loop (PER_TASK_LOOP_ENABLED=true) — Task 1 (1.1 / 1.2 / 1.3) only
- Task 1 `_Requirements:_` (合算): 2.5, 2.10, 3.6, 3.7, 6.4, 7.5, 7.6, 7.7
- Task 1 `_Boundary:_` (合算): PasskeyRepository, PasskeyRepositoryImpl
- 実装本体の commit range: 57dc9c0..be75f34 (5 commits)
  - 57dc9c0 feat(passkey-detail): add PasskeyRepository.update API
  - 0a08e67 refactor(passkey-detail): wrap PasskeyRepositoryImpl.delete in withTransaction
  - 6351f23 test(passkey-detail): add update/delete rollback cases to PasskeyRepositoryTest
  - 006e18d docs(tasks): mark 1 as done
  - be75f34 docs(impl-notes): add Task 1 learnings

> Note: `git diff develop..HEAD` の stat には `settings/` 配下や `103-*` spec の削除が
> 表示されるが、これは impl 分岐元 (merge-base a0f8fdb) より後に develop が #103 / #107 /
> hotfix-target-sdk-35 を取り込んだことによる「develop 側で追加された差分の逆向き表示」で
> あり、本 Issue が削除した訳ではない。実装本体は上記 5 commits の範囲に限定される。

## Verified Requirements

- 2.5 — `domain/repository/PasskeyRepository.kt` に `suspend fun update(entity: PasskeyEntity)` が
  加法的に追加され、`data/repository/PasskeyRepositoryImpl.kt` が `dao.update(entity)` に
  delegate 実装されている (commit 57dc9c0)
- 2.10 — `PasskeyRepository.update` KDoc が「呼び出し側が `copy(displayName = trimmed)` で
  他列を保持する責務を持つ」「本メソッドは selective update を行わない」と明記
  (PasskeyRepository.kt L94-L114)。テスト
  `update_persistsEntity_andDoesNotChangeOtherFields` が 14 列のうち displayName 以外の
  13 列が pre-update entity と byte-identical であることを assert
- 3.6 — `delete(credentialId)` が `database.withTransaction { dao.delete(credentialId); ...; keyStore.deleteEntry(alias) }`
  の順序で DB-first に再構成 (PasskeyRepositoryImpl.kt L137-L156)。テスト
  `delete_callOrder_isDbFirstThenKeystore` が KeyStore.deleteEntry 呼出時点で DB 行が
  すでに消えていることを観測
- 3.7 — KeyStore.deleteEntry の `KeyStoreException` を `withTransaction` 内で rethrow し
  Room に rollback させた上で外側 try-catch で `DeletePasskeyResult.KeystoreCleanupFailed(cause)`
  に変換 (PasskeyRepositoryImpl.kt L148-L157)。テスト
  `delete_returnsKeystoreCleanupFailed_andDbRowIsRestored_whenKeyStoreThrows` が
  KeyStore 例外後 `findByCredentialId` が non-null を返す = DB rollback されていることを assert
- 6.4 — `PasskeyRepository.delete(credentialId): DeletePasskeyResult` のシグネチャは
  diff 上で変更なし (戻り値型 / 引数型 / suspend 修飾子いずれも同一)。既存 3 件
  `delete_returns_Success_*` / `delete_returns_KeystoreCleanupFailed_*` が assertion 強化
  を経て依然 PASS
- 7.5 — `delete_callOrder_isDbFirstThenKeystore` テストが mockk の `answers { }` 内で
  `runBlocking { roomDao.findByCredentialId("order-1") }` を呼び、KeyStore.deleteEntry
  呼出時点で DB 行が既に消えていること (=DB-first ordering) を observable に assert
- 7.6 — `delete_returnsKeystoreCleanupFailed_andDbRowIsRestored_whenKeyStoreThrows` テストが
  `roomDao.findByCredentialId("rollback-1")` で行が rollback により復元されていることを assert
- 7.7 — `update_persistsEntity_andDoesNotChangeOtherFields` テストが credentialId / rpId /
  rpDisplayName / userHandle / userName / userDisplayName / isDiscoverable / encryptedPrivateKey
  / privateKeyIv / keyAlias / signCount / createdAt / lastUsedAt の 13 列を 1 列ずつ
  pre-update entity と等価比較し、`displayName` のみが "renamed" に変わっていることを assert

### 範囲外確認 (informational only — per-task review では reject 対象外)

- 1.x / 2.1-2.9 / 3.1-3.5 / 3.8-3.12 / 4.x / 5.x / 6.1-6.3, 6.5, 6.6 / 7.1-7.4, 7.8-7.10 /
  NFR 1.x / NFR 2.x / NFR 3.x / NFR 4.x: Task 2 以降 (UI 層 / Activity / Manifest / strings
  / 一覧画面遷移 / ViewModel テスト / Robolectric テスト) のスコープ。tasks.md 上で `[ ]`
  のまま残っており、後続 per-task ループで担保される予定

## Findings

なし

## Boundary 検証

- 実装本体の commit range で touch されたソース / テストファイル:
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/PasskeyRepository.kt`
    — Task 1.1 boundary 内
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryImpl.kt`
    — Task 1.1 / 1.2 boundary 内
  - `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyRepositoryTest.kt`
    — Task 1.3 boundary 内 (テスト対象 SUT が PasskeyRepositoryImpl)
- spec ドキュメント (`docs/specs/102-*/tasks.md` / `impl-notes.md`) は記録目的のため boundary 制約対象外
- `_Boundary:_` 外のコンポーネント (CredentialListActivity / PasskeyDetailActivity / strings.xml /
  AndroidManifest 等) への変更は一切なし

## テスト実行

- `./gradlew :app:testDebugUnitTest --tests "io.github.hitoshiichikawa.keynest.data.PasskeyRepositoryTest"`
  を Reviewer 自身が実行 → `BUILD SUCCESSFUL` / `tests="26" skipped="0" failures="0" errors="0"`
- 新規 3 ケース (`update_persistsEntity_andDoesNotChangeOtherFields` /
  `delete_returnsKeystoreCleanupFailed_andDbRowIsRestored_whenKeyStoreThrows` /
  `delete_callOrder_isDbFirstThenKeystore`) と既存 23 ケースが全て green
- impl-notes.md には明示的なテスト実行ログ記載がない (PR レビュー時に PjM が補完する可能性あり)
  が、Reviewer 側で再実行して green を確認済のため判定に影響なし

## Summary

Task 1 (Repository 層の rename API 追加 / delete の withTransaction 化 /
PasskeyRepositoryTest 拡張) について、`_Requirements:_` で列挙された 8 つの numeric ID
(2.5 / 2.10 / 3.6 / 3.7 / 6.4 / 7.5 / 7.6 / 7.7) は全て実装またはテストで観測可能に
カバーされている。`_Boundary:_` (PasskeyRepository / PasskeyRepositoryImpl) 逸脱もなし。
Reviewer 側で `:app:testDebugUnitTest` を再実行し 26/26 green を確認した。

RESULT: approve
