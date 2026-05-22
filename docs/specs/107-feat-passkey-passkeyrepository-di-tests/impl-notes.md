# Implementation Notes — Issue #107 / feat(passkey): PasskeyRepository + DI + Tests

## 概要

本 Issue (#107) は #91 から分割継続された scope の T-05〜T-11 を扱う。
着手時点 (2026-05-23) で develop ブランチに後続 Issue #99 / #100 が先行して merge
されており、T-05〜T-07 の成果物 (PasskeyRepository / PasskeyRepositoryImpl /
ServiceLocator 配線) は **既に存在していた**。本 Issue 範囲では追加テスト
(T-08 Migration_4_5_Test / T-09 PasskeyDaoTest / T-10 PasskeyRepositoryTest の
spec 充足ケース追加) と統合確認 (T-11) を中心に作業した。

## 既存実装の確認 (T-05 / T-06 / T-07)

| Task | 成果物 | 既存実装 | 出典 |
|---|---|---|---|
| T-05 | `domain/repository/PasskeyRepository.kt` | 存在 (8 メソッド + signWithIncrement) | #99 inlined + #100 拡張 (commit `29f3248`) |
| T-05 | `domain/model/SavePasskeyRequest.kt`, `DeletePasskeyResult.kt` | 存在 | #99 inlined (commit `019115f`) |
| T-06 | `data/repository/PasskeyRepositoryImpl.kt` | 存在 (save / find / delete / list / loadPrivateKey / signWithIncrement) | #99 + #100 (commits `019115f`, `29f3248`) |
| T-07 | `di/ServiceLocator.kt` の `passkeyRepository` | 存在 | #99 (commit `019115f`) |
| T-04 | `app/schemas/.../5.json` | 存在 | #91 (commit `c4b78c7`) |

本 Issue では **これらの既存実装には触れていない** (Issue 本文「重複コミットを
避けること」遵守 / 要件 Req 1.2 / 1.3)。

## 実装した変更

| File | 状態 | タスク |
|---|---|---|
| `app/src/test/java/io/github/hitoshiichikawa/keynest/data/Migration_4_5_Test.kt` | 新規 | T-08 |
| `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyDaoTest.kt` | 新規 | T-09 |
| `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyRepositoryTest.kt` | 追記 | T-10 (spec gap 5 ケース追加) |
| `docs/specs/107-feat-passkey-passkeyrepository-di-tests/impl-notes.md` | 新規 | T-11 |

## 主な意思決定

### 1. Keystore alias 命名: `keynest_passkey_<credentialId>` を採用

本 Issue の requirements.md は §確認事項 1 で
**`passkey_<credentialId>`** を CLOSED と宣言しているが、develop branch の
既存実装 (#99 / #100) は **`keynest_passkey_<credentialId>`** を採用しており、
既に PR がマージされている。Issue #107 本文の「重要な技術指針」も
`keynest_passkey_<credentialId>` を明示している。

本 Issue では **既存実装の alias を変更しない**判断を取った。理由:
- 既に develop に commit 済みの PasskeyRepositoryImpl が `keynest_passkey_` を
  使っており、これを書き換えると #99 / #100 の merge 後に動作している
  registration / authentication ceremony 経路に影響する (Req 1.2: 既存 T-01〜T-04
  への変更禁止 / Req 1.3: 既存テスト非破壊)
- spec 内に矛盾 (requirements.md の `passkey_` vs Issue 本文・実装の
  `keynest_passkey_`) が残っていることを以下に Reviewer 確認事項として明記
- 仮に将来 `passkey_` への変更が必要になった場合は別途 alias migration が
  必要であり、本 Issue の scope (Repository 層 + テスト追加) を超える

テストファイルもすべて `keynest_passkey_<credentialId>` を期待値として
記述している。

### 2. PasskeyRepositoryTest: 既存テストを上書きせず追記

既存の `PasskeyRepositoryTest.kt` (#99 / #100) は 16 ケースを持ち、
spec §T-10 / design §9.4.2 の 9 ケースのうち以下を既にカバーしていた:
- save_persistsKeyAlias_inEntity (#9.4.2 case 2)
- save_createsKeystoreAlias の代替 (capturedAliases によるカバー / case 3)
- delete_returnsKeystoreCleanupFailed (case 5)
- findByRpIdAndUserHandle 系 (case 9)

不足していた以下 5 ケースを **追記** (既存テストの書き換えはしない / Issue
本文「既存テストを壊さない」遵守):
- `save_thenLoadPrivateKey_roundTripsPlaintext` (case 1)
- `save_multipleCredentialIds_usesDistinctAliases` (case 7)
- `findByCredentialId_returnsNull_whenAbsent` (case 8)
- `loadPrivateKey_propagatesAeadBadTag_whenCiphertextTampered` (case 6 / req 5.10)
- `delete_removesRow_fromUnderlyingDao_andTouchesKeystoreAlias` (case 4 完全版)

design §9.4.1 の `FakeKeystoreKeyProvider` / `TestAesGcmCipher` パターンに代えて
**既存テストファイルで採用済の MockK + identity-cipher パターン** を継承した。
理由: design §9.4 は判断根拠を「Robolectric KeyStore が遅い」「open class を
subclass する」と示しているが、既存テストファイルが既に MockK 路線で動いて
いるため、同じファイルに 2 種のパターンを混在させると可読性が落ちる。

### 3. AEADBadTagException の擬装テスト

design §9.4.2 case 6 は「ciphertext を 1 byte 改竄して `Cipher.doFinal` が
`AEADBadTagException` を投げる」シナリオを期待しているが、本実装の Mock 路線
ではそもそも本物の `Cipher` を呼んでおらず、改竄しても何も起きない。
**MockK で `cipher.decrypt(any()) throws AEADBadTagException`** とすることで
「Repository が例外を握り潰さず上位伝播する」契約 (NFR 2.5 / silent fail 禁止)
を契約レベルで検証している。「実 Cipher による物理 GCM tag 検証」ではない
点に注意。

### 4. Migration_4_5_Test の fixture

既存 `Migration_3_4_Test` と同じく Room の `MigrationTestHelper` を使わず、
`SupportSQLiteOpenHelper` で v4 schema を素 SQL で復元する path を採用
(design §9.1 / 9.2 で確定済)。v4 fixture は `app/schemas/.../4.json` の
`createSql` をベースに `${TABLE_NAME}` を実テーブル名に置換した形を埋め込んで
いる。

## 各 Acceptance Criterion とテストの紐付け

### 共有 #91 requirements の Req 1〜5 / NFR 1〜5

`docs/specs/91-feat-passkey-room-migration-passkeyentit/requirements.md` の
番号を引用 (本 Issue は #91 の継続)。

| Req | 内容 | 担保するテスト |
|---|---|---|
| 1.1〜1.9 | PasskeyEntity 列 + 制約 | `PasskeyDaoTest.insert_thenFindByCredentialId_returnsEntity`, `Migration_4_5_Test.migrate_createsPasskeysTable_andPreservesCredentialsAndDetectedFields` (PRAGMA 検証) |
| 2.1 | passkeys テーブル作成 | `Migration_4_5_Test.migrate_createsPasskeysTable_andPreservesCredentialsAndDetectedFields` |
| 2.2 | INDEX 構成 (rpId / (rpId,userHandle) UNIQUE) | `Migration_4_5_Test.migrate_createsIndexes` |
| 2.3 | 既存テーブル非破壊 | `Migration_4_5_Test.migrate_createsPasskeysTable_andPreservesCredentialsAndDetectedFields` (credentials / detected_fields の row 残存検証) |
| 2.4 | KeyNestDatabase の version 5 + addMigrations | `:app:assembleDebug` (Room schema validation) + `Migration_4_5_Test.migrate_allowsInsert_afterMigration` |
| 2.5 / NFR 5.1 | 5.json コミット | `app/schemas/.../5.json` git tracked 状態確認 + `:app:assembleDebug` |
| 3.1 | DAO 8 メソッド | `PasskeyDaoTest` 14 ケース全体 |
| 3.2 | findByCredentialId | `insert_thenFindByCredentialId_returnsEntity`, `findByCredentialId_returnsNull_whenAbsent` |
| 3.3 | findByRpIdAndUserHandle | `findByRpIdAndUserHandle_returnsSingleEntity`, `findByRpIdAndUserHandle_returnsNull_whenUserHandleDiffers` |
| 3.4 | listDiscoverableByRpId (isDiscoverable=1) | `listDiscoverableByRpId_excludesNonDiscoverable`, `listDiscoverableByRpId_ordersByLastUsedAtThenCreatedAt` |
| 3.5 | listAllByRpId | `listAllByRpId_includesNonDiscoverable`, `listAllByRpId_excludesOtherRpId` |
| 3.6 | incrementSignCount (signCount+1, lastUsedAt 同時更新) | `incrementSignCount_increments_andUpdatesLastUsedAt` |
| 3.7 | delete / silent no-op | `delete_removesOnlyTargetRow`, `delete_onAbsentCredentialId_isNoOp`, `incrementSignCount_onAbsentRow_isNoOp` |
| 4.1 | AES-GCM 暗号化境界 | `PasskeyRepositoryTest.save_encryptsPrivateKey_and_stripsPlaintext_fromEntity`, `save_thenLoadPrivateKey_roundTripsPlaintext` |
| 4.2 | passkey ごと alias | `save_persistsKeyAlias_inEntity`, `save_multipleCredentialIds_usesDistinctAliases` |
| 4.3 | delete: row → Keystore 順 + 結果型 | `delete_removesRow_fromUnderlyingDao_andTouchesKeystoreAlias`, `delete_returns_Success_onCleanSuccess`, `delete_returns_Success_whenAliasAbsent` |
| 4.4 | 復号失敗 → 例外伝播 (silent fail 禁止) | `loadPrivateKey_propagatesAeadBadTag_whenCiphertextTampered`, `loadPrivateKey_throwsIllegalStateException_whenEntityMissing` |
| 4.5 / NFR 1.2 | suspend + IO Dispatcher | `runTest` ベースの全 PasskeyDaoTest / PasskeyRepositoryTest が `suspend` 経由でメソッド呼び出しを担保 |
| 5.1 / 5.2 | Migration による table / 既存行残存 | `Migration_4_5_Test.migrate_createsPasskeysTable_andPreservesCredentialsAndDetectedFields` |
| 5.3 | UNIQUE 制約 | `Migration_4_5_Test.migrate_uniqueConstraint_rejectsDuplicateRpIdAndUserHandle`, `PasskeyDaoTest.insert_duplicateRpIdAndUserHandle_throwsConstraintException` |
| 5.4 | DAO 正常系一括 | `PasskeyDaoTest` 全 14 ケース |
| 5.5 | isDiscoverable フィルタ | `listDiscoverableByRpId_excludesNonDiscoverable`, `listAllByRpId_includesNonDiscoverable` |
| 5.6 | UNIQUE 違反 SQLiteConstraintException | `PasskeyDaoTest.insert_duplicateRpIdAndUserHandle_throwsConstraintException` |
| 5.7 | incrementSignCount 単一 UPDATE | `incrementSignCount_increments_andUpdatesLastUsedAt`, `incrementSignCount_onAbsentRow_isNoOp` |
| 5.8 | ラウンドトリップ | `PasskeyRepositoryTest.save_thenLoadPrivateKey_roundTripsPlaintext` |
| 5.9 | alias 作成 / 削除 | `save_persistsKeyAlias_inEntity`, `delete_removesRow_fromUnderlyingDao_andTouchesKeystoreAlias` |
| 5.10 | 復号失敗時の例外伝播 (silent fail 禁止) | `loadPrivateKey_propagatesAeadBadTag_whenCiphertextTampered` |
| NFR 1.1 | suspend 全メソッド | PasskeyDao の interface 宣言が `suspend` のみ + 既存 `assembleDebug` でコンパイル時保証 |
| NFR 2.1〜2.2 | toString が秘匿 | 既存テスト `PasskeyRepositoryTest.save_encryptsPrivateKey_and_stripsPlaintext_fromEntity`、`PasskeyEntity` 実装の Read で確認 |
| NFR 3.1 | 新規 permission 追加なし | 既存 `InternetPermissionAbsenceTest` 維持で担保 |
| NFR 3.2 | 既存 keynest_aead_v1 alias 不干渉 | 設計上 `keynest_passkey_<id>` 命名のみを使うことが既存実装で確認可能、`save_multipleCredentialIds_usesDistinctAliases` が prefix を assert |

### #107 本 requirements

| #107 Req | 内容 | 担保 |
|---|---|---|
| 1.1 | T-05〜T-11 完了 | 本 impl-notes の各セクション参照 |
| 1.2 | T-01〜T-04 不変 | git diff で `PasskeyEntity.kt` / `PasskeyDao.kt` / `Migration_4_5.kt` / `KeyNestDatabase.kt` / `5.json` に変更なしを確認 |
| 1.3 | 共有 spec 不変 | git diff で `docs/specs/91-*/{requirements,design,tasks}.md` に変更なしを確認 |
| 1.4 | T-05 シグネチャ準拠 | 既存実装 (`PasskeyRepository.kt`) が #91 design §6.1 と同型 |
| 1.5 | T-06 が AES-GCM + KeystoreKeyProvider per-passkey | 既存 `PasskeyRepositoryImpl` が `keyProviderFactory(alias)` を使用、`save_multipleCredentialIds_usesDistinctAliases` で alias 分離を検証 |
| 1.6 | delete: row → Keystore + KeystoreCleanupFailed | 既存テスト `delete_returns_KeystoreCleanupFailed_onKeyStoreException` + 新規 `delete_removesRow_fromUnderlyingDao_andTouchesKeystoreAlias` |
| 1.7 | ServiceLocator | 既存 `ServiceLocator.passkeyRepository` |
| 1.8 | test ディレクトリ配置 | 3 ファイル全て `app/src/test/java/.../data/` 配下 |
| 2.1 | 新規テスト全 pass | `./gradlew :app:testDebugUnitTest --tests *PasskeyDaoTest* --tests *Migration_4_5_Test* --tests *PasskeyRepositoryTest*` で全 40 ケース pass |
| 2.2 | 既存テスト非破壊 | 同コマンドの広範囲リランで pass (AppInfoProviderTest の 1 件は本 Issue 着手前から失敗していた既存問題、後述) |
| 2.3 | lint ベースライン内 | 新規ファイルに lint 出力なし。既存 baseline の API 28 系警告 / エラーは本 Issue 範囲外 |
| 2.4 | assembleDebug 成功 | `./gradlew :app:assembleDebug` BUILD SUCCESSFUL |
| 2.5 | INTERNET permission 追加なし | AndroidManifest.xml に変更なし |
| 3.1〜3.3 | #91 merge 済み前提 | T-01〜T-04 成果物の存在を Read で事前確認 |

## テスト結果サマリ

```
:app:testDebugUnitTest (3 新規ファイルに限定)
  Migration_4_5_Test         : 5 / 5 pass
  PasskeyDaoTest             : 14 / 14 pass
  PasskeyRepositoryTest      : 21 / 21 pass (既存 16 + 新規 5)

  total: 40 / 40 pass
```

```
:app:testDebugUnitTest (要件 2.2 で参照されている既存テストのみ)
  Migration_1_2_Test, Migration_2_3_Test, Migration_3_4_Test,
  CredentialDaoTest, CredentialRepositoryImplTest, DetectedFieldDaoTest,
  FillResponseBuilderTest, LockedFillResponseSecurityTest,
  CustomFieldFillResponseTest, InternetPermissionAbsenceTest
  → 全 pass
```

```
:app:assembleDebug    → BUILD SUCCESSFUL
:app:lintDebug        → 153 errors / 150 warnings (全て pre-existing baseline)
                        ※ 新規追加した 3 テストファイルに対する lint 警告は 0
```

### 既知の test 失敗 (本 Issue 着手前から)

- `AppInfoProviderTest.get_returnsVersionNameFromBuildGradle`
  - `versionName` を `"0.1.0"` で assert しているが、`app/build.gradle.kts` は
    既に `"1.0.0"` に bump 済 (commit `85daaa0`)
  - 本 Issue の変更とは無関係。requirements §確認事項 でも触れていないため、
    別 Issue で扱う想定

## 残課題 / 確認事項

### 1. [Reviewer 確認] Keystore alias 命名の最終形

- 本 Issue の `docs/specs/107-feat-passkey-passkeyrepository-di-tests/requirements.md`
  §確認事項 1 では `passkey_<credentialId>` を CLOSED と宣言している
- 一方で Issue #107 本文の「重要な技術指針」および develop の既存実装
  (PR #99 / #100) は `keynest_passkey_<credentialId>` を採用しており、後者が
  既に動作している
- 本 Issue では **既存実装を変更せず `keynest_passkey_<credentialId>` を採用**
  した (Req 1.2 / 1.3 既存非破壊優先)
- alias 命名の最終決定が必要。spec 側の表記 (`passkey_<credentialId>`) を
  実装に合わせて修正するか、あるいは別途 alias migration Issue を切るかは
  Architect / 人間レビューに委ねる

### 2. [Reviewer 確認] tasks.md 進捗チェックボックス

- 共有 `docs/specs/91-*/tasks.md` は T-05〜T-11 に checkbox を持たない
- Issue 本文「確認事項 3」では Developer が本 Issue 着手時に checkbox を
  追加・完了マークしてよいと明示されていたが、本 Issue では
  **共有 tasks.md には触れなかった** (Req 1.3 不変要件を優先)
- 本 impl-notes で進捗を報告する形を採った。今後 spec 共有運用への移行可否は
  Architect / 人間で別途決定する想定

### 3. [情報] 既存実装と spec の細部差分

design §6.1 の `PasskeyRepository` シグネチャは以下の差分がある:
- `findByCredentialId(credentialId): Passkey?` (domain 型) → 実装は
  `PasskeyEntity?` を返す (PR #99 の都合で entity を直接返す形に簡素化)
- `incrementSignCount(credentialId, timestamp)` → 実装は別途
  `signWithIncrement(credentialId) { signer }` で transaction wrapper を提供
  (PR #100 / Option A)
- `listAllByRpId` → 実装には未実装 (PR #101 で `listAll: Flow` が追加予定だが
  develop には未 merge)

これらは Issue #107 本文の「scope (T-05〜T-11)」を超える domain 型整備の
範疇であり、後続 Issue が PassKey 管理 UI 配線時に再検討する想定。
本 Issue では既存 API シェイプを温存した。

### 4. [情報] AndroidTest path のスコープ外確認

Issue 本文 (#107 本文) の表では `app/src/androidTest/...` パスが提示されて
いるが、本 requirements.md (本 Issue の) Req 1.8 / 共有 tasks.md §T-08〜§T-10
では `app/src/test/...` (JVM + Robolectric) が確定形である。共有 spec を
優先して JVM unit test 配置とした。

## Reviewer への引き継ぎ事項

1. **alias 命名の不整合**: requirements.md (本 Issue) vs 実装 / Issue 本文の
   差異を上記「確認事項 1」のとおり扱った。承認 or 是正の判断を願いたい。
2. **PasskeyRepositoryTest の既存テスト群を変更していない**: 既存 16 ケースは
   PR #99 / #100 由来。本 Issue では末尾に 5 ケースを追加したのみで、
   そのパターン (MockK + identity cipher) は design §9.4.1 の
   FakeKeystoreKeyProvider/TestAesGcmCipher 案と異なる。既存ファイルとの
   一貫性を優先した判断であり、design 通りの fake 実装が必要か確認願いたい。
3. **AppInfoProviderTest の pre-existing failure** は本 Issue の scope 外。
   別 Issue で扱う必要がある。

## Reviewer round=1 reject への是正 (2026-05-23)

Reviewer round=1 が `STATUS: reject` を出し、以下 2 件の Finding を提示
(`review-notes.md`)。両方とも spec 文言と実装の乖離を指摘するものであり、
PjM 経由で「**spec を正として実装側を寄せる**」方針が確定したため、本セッションで
是正実装を入れた。

### Finding 1 への対応 — PasskeyRepository を design §6.1 シグネチャに揃える

**変更内容**:

1. `domain/model/Passkey.kt` を **新規追加**
   - `credentialId` / `rpId` / `rpDisplayName` / `userHandle` /
     `userName` / `userDisplayName` / `isDiscoverable` / `signCount` /
     `displayName` / `createdAt` / `lastUsedAt` の 11 フィールドを保持
   - `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` は **意図的に
     含めない** (NFR 2.2: 暗号文 / IV / wrapping-key alias を domain layer
     に漏らさない / 上位レイヤは plaintext が必要なら
     `loadPrivateKey(...)` を通す)
   - `ByteArray` (userHandle) を含むため `equals` / `hashCode` /
     `toString` を手書き。`toString` は `userHandle` をサイズ表記に
     redact (NFR 2.2)
2. `domain/repository/PasskeyRepository.kt` のシグネチャ更新
   - `findByCredentialId(...): Passkey?` (旧: `PasskeyEntity?`)
   - `findByRpIdAndUserHandle(...): Passkey?` (旧: `PasskeyEntity?`)
   - `listDiscoverableByRpId(...): List<Passkey>` (旧: `List<PasskeyEntity>`)
   - `listAllByRpId(rpId): List<Passkey>` を **新規追加** (design §6.1 必須)
   - `incrementSignCount(credentialId, timestamp)` を **直接 expose**
     (design §6.1) — caller は通常 `signWithIncrement` を使う想定だが、
     design §6.1 と完全準拠を取るため直接 API も提供
   - `loadPrivateKey(...): ByteArray?` (旧: non-nullable `ByteArray`) —
     entity 不在時は `null` を返し、復号失敗 (auth tag 不整合等) は
     例外伝播 (silent fail 禁止 / NFR 2.5)
   - `signWithIncrement(credentialId) { signer }` は **互換 API として残置**
     (理由は下記の「signWithIncrement の温存」参照)
3. `data/repository/PasskeyRepositoryImpl.kt` の実装更新
   - `private fun PasskeyEntity.toDomain(): Passkey` extension を
     file-level で追加し、find / list 4 メソッドが entity を `toDomain()`
     経由で返す
   - `listAllByRpId` を `dao.listAllByRpId(rpId).map { it.toDomain() }` で
     実装
   - `incrementSignCount(credentialId, timestamp)` を新規実装
     (DAO へ単純委譲)
   - `loadPrivateKey(...)`: entity 不在時は `null` を返し、見つかった
     場合のみ cipher decrypt を実行
4. 呼び出し側 (caller) 追従更新
   - `GetEntryBuilder.kt`: `PasskeyEntity` の import を `Passkey` に
     置換し、`buildEntry(entity, ...)` を `buildEntry(passkey, ...)` に
     リネーム。`Passkey` 型のフィールドアクセスはほぼ同一なので最小差分で
     済む
   - `PasskeyAuthActivity.kt`: `repository.findByCredentialId(...)` の
     戻り値変数名を `entity` → `passkey` に変更 (シグネチャは
     `Passkey?` でも `userHandle` アクセスは可)、`loadPrivateKey` の
     nullable 化に伴い `?: throw GetCredentialUnknownException(...)`
     ガードを追加
   - `PasskeyCreateActivity.kt`: `findByRpIdAndUserHandle` の戻り値型
     変更に対し `existing.credentialId` アクセスはそのまま動くため
     **変更不要**
   - `ExcludeCredentialDetector.kt`: `!= null` 判定のみのため
     **変更不要**

### Finding 2 への対応 — Keystore alias を `passkey_<credentialId>` に揃える

**変更内容**:

1. `PasskeyRepositoryImpl.aliasFor(credentialId)` を `"passkey_$credentialId"`
   に書き換え (旧: `"keynest_passkey_$credentialId"`)
2. KDoc コメント 2 箇所も spec 整合に修正:
   - `PasskeyRepositoryImpl.kt` の `aliasFor` companion KDoc
   - `PasskeyCreator.kt` のクラスレベル KDoc
3. テストファイルの期待値を 5 ファイルで `keynest_passkey_` →
   `passkey_` に一括置換:
   - `PasskeyRepositoryTest.kt` (全面書き直し / 後述)
   - `Migration_4_5_Test.kt` (1 箇所)
   - `PasskeyDaoTest.kt` (1 箇所)
   - `PasskeyAuthActivityTest.kt` (1 箇所)
   - `PasskeyCreatorTest.kt` (`startsWith` assertion + 二重チェック追加:
     `doesNotContain("keynest_passkey_")` で過去 prefix の再混入を防ぐ
     リグレッション ガード)

`PasskeyRepositoryTest.kt` は Finding 1 の戻り値型変更 (Passkey domain
projection) に伴い entity 直 assert していた既存ケース 4 件 (`findByCredentialId_returns_dao_value` /
`findByRpIdAndUserHandle_returns_dao_value` /
`listDiscoverableByRpId_delegatesToDao` /
`loadPrivateKey_throwsIllegalStateException_whenEntityMissing` →
`loadPrivateKey_returnsNull_whenEntityMissing` に名称変更し null 返却
仕様に合わせた) を Passkey domain 型 assert に書き換えた。また design §6.1
の `listAllByRpId` / `incrementSignCount` 直接 expose の追加テストとして:

- `listAllByRpId_includesDiscoverableAndNonDiscoverable_andProjectsToDomain`
  (実際の Room DB + DAO を使い、isDiscoverable=true/false 両方 + 他 RP
  の除外を 1 ケースで検証 / Req 3.5)
- `incrementSignCount_delegatesToDao_withSuppliedTimestamp`
  (新 API が DAO へ正しく委譲することを mock で検証 / design §6.1)
- `listDiscoverableByRpId_delegatesToDao_andProjectsToDomain` の
  assertion を domain projection 確認用に強化

を追加した。合計 23 ケース (旧 21 + 新 2)。

### `signWithIncrement` の温存判断

design §6.1 には `signWithIncrement` メソッドは存在しないが、本 Issue では
**互換 API として残置** した。理由:

- `PasskeyAuthActivity` (Issue #100 で merge 済) が `signWithIncrement` を
  唯一の signCount 操作経路として呼んでいる。当該 method を削除すると
  認証 ceremony が機能停止する
- design §6.1 案 C も「signer throw 時に signCount を rollback」する
  ためのトランザクション wrapper が必要と認めており、`signWithIncrement`
  はその要件を満たす実装の 1 形態に相当する
- spec 「design §6.1 完全準拠」と「Req 2.2 既存テスト非破壊」の両立を
  取る最小限の手段として、`incrementSignCount` を design §6.1 通りに
  新規追加 + `signWithIncrement` を互換のため残す形を採った
- 将来的に `PasskeyAuthActivity` 側で `database.withTransaction { ... }`
  を直接呼び `incrementSignCount` に切り替えれば `signWithIncrement` は
  削除可能 → これは別 Issue で扱う想定 (下記 round=2 引き継ぎ事項参照)

### Keystore alias migration を本 Issue で実装しなかった理由

合意済み (PjM 確認済み) として、既存 AndroidKeyStore に
`keynest_passkey_*` で発番済みの alias を `passkey_*` にリネームする
migration は **本 Issue では実装しない**。判断根拠:

- Issue #99 / #100 が develop に merge 済みだが、**実際の PassKey 登録
  フローが end-user に公開されたリリースは未だ存在しない** (Manifest 上の
  CredentialProviderService 宣言と Service 配線は完了しているが、
  end-user に「KeyNest が PassKey を発行できる」状態でリリースされた
  バージョンは無い)
- そのため既存 `keynest_passkey_*` alias を持つ端末は **開発端末のみ**で、
  本 Issue の merge 時点では実害なし
- 仮に将来 release 後に data migration が必要になった場合は別 Issue で
  対応する (下記 round=2 引き継ぎ事項参照)

### 影響を受けたファイル一覧 (round=1 reject 是正分)

**新規作成**:
- `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/Passkey.kt`

**更新 (main / production code)**:
- `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/PasskeyRepository.kt`
- `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryImpl.kt`
- `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/GetEntryBuilder.kt`
- `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAuthActivity.kt`
- `app/src/main/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreator.kt` (KDoc only)

**更新 (test code)**:
- `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyRepositoryTest.kt` (全面書き直し: alias prefix + Passkey domain projection 反映 + 新規 2 ケース追加)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/data/Migration_4_5_Test.kt` (1 箇所)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyDaoTest.kt` (1 箇所)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/PasskeyAuthActivityTest.kt` (1 箇所)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/authentication/GetEntryBuilderTest.kt` (PasskeyEntity → Passkey 型置換 + sampleEntity helper シグネチャ簡素化)
- `app/src/test/java/io/github/hitoshiichikawa/keynest/credentialprovider/registration/PasskeyCreatorTest.kt` (`assignsAliasFollowingKeynestPasskeyPrefix` → `assignsAliasFollowingPasskeyPrefix` リネーム + `doesNotContain("keynest_passkey_")` 追加)

### 検証結果 (round=1 reject 是正後)

```
:app:assembleDebug                                                 → BUILD SUCCESSFUL
:app:testDebugUnitTest (全 815 ケース)                             → 1 fail (pre-existing AppInfoProviderTest), 814 pass
:app:testDebugUnitTest (Passkey 関連のみ)
  PasskeyRepositoryTest                                            : 23 / 23 pass
  PasskeyDaoTest                                                   : 14 / 14 pass
  Migration_4_5_Test                                               : 5 / 5 pass
  GetEntryBuilderTest                                              : 8 / 8 pass
  PasskeyAuthActivityTest                                          : 8 / 8 pass
  PasskeyCreatorTest                                               : 15 / 15 pass
  KeyNestCredentialProviderServiceTest                             : 13 / 13 pass
  PasskeyAssertionTest                                             : pass
  → Passkey 関連合計 86 / 86 pass

:app:lintDebug                                                      → 153 errors / 150 warnings
                                                                     (全て pre-existing baseline / 本 round=2 変更による新規警告は 0)
```

### Reviewer round=2 引き継ぎ事項

1. **Keystore alias migration の将来必要性 (別 Issue 推奨)**:
   現在は `passkey_<credentialId>` で確定済の正規 prefix を使うが、もし
   将来 release 後に運用判断で「`keynest_*` prefix で揃えたい」となった
   場合 (例: 端末上の AndroidKeyStore alias 一覧で KeyNest 由来の鍵を
   prefix で grep できる利便性) は別 Issue を切る必要がある。本 Issue
   merge 時点では release 前提のため migration 不要。
2. **`signWithIncrement` の整理 (別 Issue 推奨)**:
   design §6.1 に存在しない `signWithIncrement` を残置した。当該 method
   を削除して `PasskeyAuthActivity` 側で `database.withTransaction +
   incrementSignCount` を直接呼ぶ形に整理する Issue を切ることで、
   spec と実装の完全 1 対 1 対応に到達できる。本 Issue では既存
   ceremony 側の修正範囲を最小化するため温存。
3. **`incrementSignCount` 直接呼び出しの caller**:
   現状 caller は `signWithIncrement` 経由のみ。`incrementSignCount` を
   直接呼ぶケースは将来の管理 UI (deferrable) で発生する想定。本 Issue
   の test では `incrementSignCount_delegatesToDao_withSuppliedTimestamp`
   で API 契約のみ検証している。
4. **AppInfoProviderTest の pre-existing failure** は本 Issue の scope
   外 (Round=1 と同じ)。別 Issue で扱う必要がある。
