# Requirements Document — Issue #107 / feat(passkey): PasskeyRepository + DI + Tests

> 本 Issue は **#91 の scope 分割継続 Issue** である。
>
> #91 が当初 T-01〜T-11 の 11 タスクを 1 Issue で実装する想定だったが、
> Developer エージェントの turn budget を超過したため 2 Issue に分割された:
>
> | タスク範囲 | 担当 Issue |
> |---|---|
> | T-01〜T-04 (データ層 scaffold) | **#91** |
> | **T-05〜T-11 (Repository + DI + Tests + 統合)** | **#107 (本 Issue)** |
>
> 共有の設計 / 要件 / タスク仕様は
> [`docs/specs/91-feat-passkey-room-migration-passkeyentit/`](../91-feat-passkey-room-migration-passkeyentit/)
> に集約されている。本ファイルはそれを参照する **scope ガード用 requirements**
> であり、要件本体 (Requirement 1〜5 / NFR 1〜5) は再掲しない。
>
> 参照すべき共有ドキュメント:
>
> - [`requirements.md`](../91-feat-passkey-room-migration-passkeyentit/requirements.md)
>   — Requirement 1〜5 / NFR 1〜5 / Out of Scope / 確定済の設計判断 (decision 1〜3)
> - [`design.md`](../91-feat-passkey-room-migration-passkeyentit/design.md)
>   — §6 (Repository), §7 (Keystore alias 管理), §9 (テスト戦略)
> - [`tasks.md`](../91-feat-passkey-room-migration-passkeyentit/tasks.md)
>   — T-05〜T-11 の詳細手順 / 受入基準 / テストケース一覧

## Introduction

KeyNest の Android Credential Manager 対応 (umbrella #89 / Phase 1 #91) のうち、
本 Issue (#107) は T-01〜T-04 で構築された PassKey データ層 scaffold
(`PasskeyEntity` / `PasskeyDao` / `Migration_4_5` / `KeyNestDatabase` v5) の上に、
**Repository 層 (AES-GCM 暗号化境界) と DI 配線、および 3 種のテスト群** を被せて
後続セレモニー Issue (#99 / #100) が再実装なしで `PasskeyRepository` を呼び
出せる状態に到達する。spec は #91 と完全に共有し、新規 spec dir を作らない。

## Scope (T-05〜T-11)

| Task | 概要 | 主な成果物 |
|---|---|---|
| T-05 | `PasskeyRepository` interface + domain 型 (`Passkey` / `SavePasskeyRequest` / `DeletePasskeyResult`) | `domain/repository/PasskeyRepository.kt`, `domain/model/Passkey.kt` |
| T-06 | `PasskeyRepositoryImpl` 実装 (AES-GCM 暗号化 / 復号 / DAO 委譲 / Keystore alias 廃棄) | `data/repository/PasskeyRepositoryImpl.kt` |
| T-07 | `ServiceLocator` への `passkeyRepository` lazy singleton 追加 | `di/ServiceLocator.kt` |
| T-08 | `Migration_4_5_Test` 追加 (Room schema v4→v5 / 既存テーブル非影響) | `test/.../data/Migration_4_5_Test.kt` |
| T-09 | `PasskeyDaoTest` 追加 (CRUD + 検索 + signCount + UNIQUE 制約) | `test/.../data/PasskeyDaoTest.kt` |
| T-10 | `PasskeyRepositoryTest` 追加 (ラウンドトリップ + alias + 復号失敗) | `test/.../data/PasskeyRepositoryTest.kt` |
| T-11 | 統合確認 (全テスト pass + 既存テスト非破壊 + `5.json` commit 確認) | (確認のみ) |

> ※ Issue #107 本文では `androidTest/...` パスが提示されているが、共有 tasks.md
> 確定形では `test/...` (Robolectric / JVM unit test) を採用している。
> 共有 tasks.md を正とする。

## Out of Scope

- **T-01〜T-04** (PasskeyEntity / PasskeyDao / Migration_4_5 / KeyNestDatabase
  version up + `5.json` 生成) — 親 Issue #91 が担当。本 Issue では一切変更しない。
- **共有 spec ファイルの書き換え**
  (`docs/specs/91-feat-passkey-room-migration-passkeyentit/{requirements,design,tasks}.md`)
  — design / tasks は変更しない。本 Issue では本 requirements.md のみを新規作成する。
- 登録セレモニー (`onBeginCreateCredentialRequest` 実体実装) — 後続 #99
- 認証セレモニー (`onBeginGetCredentialRequest` 実体実装) — 後続 #100
- `CredentialProviderService` の Service 配線 / Manifest 宣言 — 並列 #90
- 既存クレデンシャル一覧 UI への PassKey 表示 / PassKey 単位の管理 UI / 設定画面 /
  README ・ Privacy Policy 更新 — #89 分割案 5〜8
- AAGUID の `authenticatorData` 埋め込み — 登録セレモニー Issue
- PassKey の export / backup — umbrella #89 で「不可」確定
- StrongBox / 通常 Keystore の使い分け — umbrella #89 のリスク項目

## Requirements

### Requirement 1: 担当タスクの完了

**Objective:** As a #91 スコープ分割の継続実装者, I want T-05〜T-11 のすべてを
共有 tasks.md 確定形どおりに完了させ、かつ親 #91 担当の T-01〜T-04 には触れない
こと, so that PR が重複コミットなく merge でき、共有 spec の整合性が保たれる。

#### Acceptance Criteria

1. The implementation shall complete tasks T-05 through T-11 as defined in
   [`docs/specs/91-feat-passkey-room-migration-passkeyentit/tasks.md`](../91-feat-passkey-room-migration-passkeyentit/tasks.md)
   §T-05〜§T-11, including all per-task 完了条件.
2. The implementation shall not modify tasks T-01 through T-04 deliverables
   (`PasskeyEntity.kt`, `PasskeyDao.kt`, `Migration_4_5.kt`, `KeyNestDatabase.kt`
   `version` / `entities` / `addMigrations`, `app/schemas/.../5.json`).
3. The implementation shall not modify shared spec files
   (`requirements.md`, `design.md`, `tasks.md`) under
   `docs/specs/91-feat-passkey-room-migration-passkeyentit/`.
4. When T-05 が完了したとき, the resulting `PasskeyRepository` interface and
   `domain/model/Passkey.kt` types shall match the signatures declared in
   [`design.md §6.1`](../91-feat-passkey-room-migration-passkeyentit/design.md).
5. When T-06 が完了したとき, the resulting `PasskeyRepositoryImpl` shall use
   `AesGcmCipher` + `KeystoreKeyProvider(keyAlias = "passkey_<credentialId>")`
   per-passkey instance (not the singleton `aesGcmCipher` bound to
   `keynest_aead_v1`) as specified in
   [`design.md §6.2`](../91-feat-passkey-room-migration-passkeyentit/design.md).
6. When T-06 implements `delete(credentialId)`, the repository shall perform
   row deletion first then Keystore alias deletion, and wrap any
   `KeyStoreException` into `DeletePasskeyResult.KeystoreCleanupFailed(cause)`
   per [`design.md §7.4`](../91-feat-passkey-room-migration-passkeyentit/design.md).
7. When T-07 が完了したとき, the `ServiceLocator` shall expose
   `passkeyRepository: PasskeyRepository` as a lazy singleton without
   modifying the existing `aesGcmCipher` / `keystoreKeyProvider` / `credentialRepository`
   singletons.
8. The implementation shall write all new test files under
   `app/src/test/java/...` (JVM / Robolectric unit tests), aligning with
   [`tasks.md §T-08 〜 §T-10`](../91-feat-passkey-room-migration-passkeyentit/tasks.md),
   not under `app/src/androidTest/...`.

### Requirement 2: 既存テスト非破壊

**Objective:** As a メンテナ, I want 本 Issue の追加によって既存の
Migration / DAO / Repository / autofill テストが一切壊れないこと, so that
PassKey データ層の追加が他のレイヤに波及していないことを CI で保証できる。

#### Acceptance Criteria

1. When `./gradlew :app:testDebugUnitTest` is executed at the head of the
   #107 implementation branch, the build shall pass all unit tests including
   the newly added `Migration_4_5_Test`, `PasskeyDaoTest`, and
   `PasskeyRepositoryTest`.
2. The existing tests `Migration_1_2_Test`, `Migration_2_3_Test`,
   `Migration_3_4_Test`, `CredentialDaoTest`, `CredentialRepositoryImplTest`,
   `DetectedFieldDaoTest`, `FillResponseBuilderTest`,
   `LockedFillResponseSecurityTest`, `CustomFieldFillResponseTest`, and
   `InternetPermissionAbsenceTest` shall remain passing without modification.
3. When `./gradlew :app:lintDebug` is executed, the build shall produce zero
   new warnings (or stay within the existing lint baseline) — no new
   suppressions for issues introduced by #107 implementation.
4. When `./gradlew :app:assembleDebug` is executed, the build shall succeed
   with Room compile-time schema validation passing against
   `app/schemas/.../5.json`.
5. The implementation shall not introduce `android.permission.INTERNET` or
   any other new permission to `AndroidManifest.xml`
   (umbrella #89 のネット境界ポリシー維持 / NFR 3.1).

### Requirement 3: 親 Issue 依存

**Objective:** As a 自動化実行系, I want 親 Issue #91 の impl PR が develop に
merge された後にのみ本 Issue が impl 着手される状態を保証すること, so that
共有 spec / scaffold が存在しない状態で `PasskeyRepository` 実装が空中浮遊する
事態を避ける。

#### Acceptance Criteria

1. If 親 Issue #91 の impl PR が develop に未 merge であるとき, the
   implementation shall be deferred until #91 の impl PR が develop に
   merge されるまで (本 Issue は #91 の merge を前提とする継続 Issue).
2. The implementation shall verify, before starting T-05, that the files
   `data/entity/PasskeyEntity.kt`, `data/dao/PasskeyDao.kt`,
   `data/migration/Migration_4_5.kt`, and
   `app/schemas/.../5.json` exist on the working branch (i.e., #91 が
   merge 済み).
3. (補足) 親 Issue #91 の impl PR (#110) は 2026-05-21 に develop へ merge
   完了済みのため、本 Acceptance Criteria 3.1 / 3.2 は本 Issue 着手時点で
   既に満たされている (本 requirements 作成日: 2026-05-23 時点)。

## Non-Functional Requirements

> NFR の本体定義 (NFR 1 スレッディング / NFR 2 秘密情報取り扱い / NFR 3 セキュリティ
> 境界 / NFR 4 命名 / NFR 5 スキーマバージョニング) は
> [`docs/specs/91-feat-passkey-room-migration-passkeyentit/requirements.md` §「Non-Functional Requirements」](../91-feat-passkey-room-migration-passkeyentit/requirements.md)
> を参照する。本 Issue では再掲せず、以下の継承事項のみを明示する。

### NFR-INHERIT: 共有 NFR の継承

1. The implementation shall satisfy NFR 1 (threading / no main thread
   blocking) for `PasskeyRepository` public methods by declaring all of them
   `suspend` and dispatching to `Dispatchers.IO`.
2. The implementation shall satisfy NFR 2 (秘密情報の取り扱い) by ensuring
   `SavePasskeyRequest.toString()` outputs `privateKey` as a size indicator
   only (not raw bytes), `PasskeyRepositoryImpl` does not log decrypted
   private key plaintext, and decryption failures propagate as exceptions
   (no silent fail returning `null`).
3. The implementation shall satisfy NFR 3 (セキュリティ境界) by not touching
   the existing `keynest_aead_v1` Keystore alias and by using the
   `passkey_<credentialId>` prefix for all PassKey wrapping keys
   (decision 3 / design §6.2).
4. The implementation shall satisfy NFR 4 (命名) by using the "PassKey"
   notation in newly added class names / KDoc / log messages.

## 確認事項 (Open Questions)

> Issue #107 本文に列挙された 3 件。共有 spec で既に決着しているものは
> 「[CLOSED]」を冒頭に付し、参照先を併記する。未決着のものは「[OPEN]」とし
> 人間レビューを要する。

1. **[CLOSED] Keystore alias 命名規則の最終形**
   - Issue #107 本文では `keynest_passkey_<credentialId>` 形式の確認を求めて
     いるが、共有 requirements の **決定 3** および
     [`design.md §6.2 / §7.1`](../91-feat-passkey-room-migration-passkeyentit/design.md)
     により最終形は **`passkey_<credentialId>`** で確定済み
     (`keynest_` prefix は付けない)。
     根拠: 既存 `keynest_aead_v1` (credential 用) との完全分離は `passkey_`
     prefix のみで成立しており、二重 prefix は冗長。
   - 本 Issue では `passkey_<credentialId>` を採用する。

2. **[CLOSED] Repository の rollback / 削除失敗時の挙動**
   - 共有
     [`design.md §7.4`](../91-feat-passkey-room-migration-passkeyentit/design.md)
     で確定: `delete` は「row 削除 → Keystore alias 削除」の順で実行し、
     Keystore 削除失敗時は **例外を伝播せず**
     `DeletePasskeyResult.KeystoreCleanupFailed(cause: Throwable)` 結果型で
     呼び出し側に通知する (silent fail 禁止は要件 4.4 と整合)。
   - 本 Issue では design §7.4 の構造化結果型方式を採用する。

3. **[OPEN] Developer の tasks.md チェックボックス追記権限**
   - Issue #107 本文「確認事項 3」: Developer エージェントが共有
     [`tasks.md`](../91-feat-passkey-room-migration-passkeyentit/tasks.md)
     にチェックボックスを追記してよいか、Architect 経由が必要か。
   - **現時点での暫定方針**: 共有 tasks.md は Architect の成果物であるため、
     Developer は本 Issue 範囲では **tasks.md を編集せず**、進捗は本 Issue
     のコメント / PR 本文の「実装サマリ」セクションで報告する。タスク完了
     チェックボックスを共有 tasks.md に追記する運用への移行可否は、本 Issue
     merge 後に Architect / 人間で別途決定する。
   - **Human review required**: 本暫定方針で進めて問題ないかの最終確認。
