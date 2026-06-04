# Review Notes

<!-- idd-claude:review round=2 model=claude-opus-4-7 timestamp=2026-05-23T05:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-107-impl-feat-passkey-passkeyrepository-di-tests
- HEAD commit: 28a2807ffcfa4d949773e0465ee40a34b84ae59c
- Compared to: develop..HEAD
- Round: 2 / Previous result: reject
- Diff scope: 14 files / +2062 / -137 lines
  - 6 production files updated (PasskeyRepository / PasskeyRepositoryImpl / Passkey domain / GetEntryBuilder / PasskeyAuthActivity / PasskeyCreator)
  - 1 production file added (`domain/model/Passkey.kt`)
  - 6 test files updated/added (Migration_4_5_Test new / PasskeyDaoTest new / PasskeyRepositoryTest reshape / GetEntryBuilderTest / PasskeyAuthActivityTest / PasskeyCreatorTest)
  - 2 spec docs (requirements.md / impl-notes.md)

## Round 1 reject 是正状況

Round 1 で出した 2 件の Finding は本 Round 2 で **両方とも解消** されている。

### Finding 1 (Req 1.4 / design §6.1 シグネチャ準拠) → 解消

- **新規追加**: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/Passkey.kt` (87 lines)
  - 11 フィールド (credentialId / rpId / rpDisplayName / userHandle / userName / userDisplayName / isDiscoverable / signCount / displayName / createdAt / lastUsedAt) を保持
  - `encryptedPrivateKey` / `privateKeyIv` / `keyAlias` を意図的に除外 (NFR 2.2 / data layer 内に閉じる)
  - `ByteArray` の `equals` / `hashCode` / `toString` を手書きし、`toString` は `userHandle` を size redact (NFR 2.2)
- **PasskeyRepository interface 更新**:
  - `findByCredentialId(...): Passkey?` (旧 `PasskeyEntity?`)
  - `findByRpIdAndUserHandle(...): Passkey?`
  - `listDiscoverableByRpId(...): List<Passkey>`
  - `listAllByRpId(rpId): List<Passkey>` を **新規追加** (design §6.1 必須)
  - `incrementSignCount(credentialId, timestamp)` を **直接 expose** (design §6.1)
  - `loadPrivateKey(...): ByteArray?` (旧 non-nullable) — entity 不在時は null、復号失敗は例外伝播 (NFR 2.5)
- **PasskeyRepositoryImpl 更新**: file-level `private fun PasskeyEntity.toDomain(): Passkey` を追加し、lookup / list 4 メソッドが domain 投影経由で返るように変更
- **caller 追従**:
  - `GetEntryBuilder.kt`: `PasskeyEntity` import を `Passkey` に置換、`buildEntry(entity, ...)` を `buildEntry(passkey, ...)` にリネーム
  - `PasskeyAuthActivity.kt`: 戻り値変数名 `entity` → `passkey`、`loadPrivateKey` nullable 化に対応する `?: throw GetCredentialUnknownException(...)` ガード追加
  - `PasskeyCreateActivity.kt` / `ExcludeCredentialDetector.kt`: アクセス内容上シグネチャ変更の影響を受けないため変更不要
- **`signWithIncrement` の温存**: design §6.1 には存在しないが、`PasskeyAuthActivity` の唯一の signCount 操作経路として残置。interface KDoc にその経緯と将来の整理計画 (別 Issue で `withTransaction + incrementSignCount` に切り替え予定) が明記されており、design §6.1 に必須メソッドはすべて揃っている (`incrementSignCount` も直接 expose 済) ため Req 1.4 への充足を阻害しない。

### Finding 2 (Req 1.5 / Keystore alias `passkey_<credentialId>`) → 解消

- `PasskeyRepositoryImpl.aliasFor(credentialId)` を `"passkey_$credentialId"` に修正 (旧 `"keynest_passkey_$credentialId"`)
- KDoc 2 箇所 (`PasskeyRepositoryImpl.kt` companion, `PasskeyCreator.kt`) も spec 整合に修正
- テストファイル 5 箇所の期待値を一括更新:
  - `PasskeyRepositoryTest.kt`: 旧 alias `keynest_passkey_` → `passkey_` の置換 + domain projection 反映 + 新規 2 ケース追加 (`listAllByRpId_includesDiscoverableAndNonDiscoverable_andProjectsToDomain`, `incrementSignCount_delegatesToDao_withSuppliedTimestamp`)
  - `Migration_4_5_Test.kt` (1 箇所), `PasskeyDaoTest.kt` (1 箇所), `PasskeyAuthActivityTest.kt` (1 箇所): 期待値を `passkey_<id>` に修正
  - `PasskeyCreatorTest.kt`: テスト名を `assignsAliasFollowingKeynestPasskeyPrefix` → `assignsAliasFollowingPasskeyPrefix` にリネーム、`startsWith("passkey_")` + `doesNotContain("keynest_passkey_")` の二重ガードに変更
- repo grep 結果: 残存する `keynest_passkey_` 文字列は (a) `PasskeyRepositoryImpl.kt` の KDoc で「legacy prefix is intentionally dropped」と説明する 1 行と、(b) `PasskeyCreatorTest.kt` のリグレッションガード `doesNotContain("keynest_passkey_")` の 1 行のみ。実 alias 値として `keynest_passkey_` を使う production / test コードは存在しない
- migration を見送る判断 (release 前のため実害なし) は impl-notes に明記されており、Reviewer 引き継ぎ事項として「将来 release 後に必要になれば別 Issue で対応」と整理されている

## Verified Requirements

### #107 本 requirements

- 1.1 — T-05〜T-11 完了: T-05 (Passkey 新規追加 + PasskeyRepository 更新 / 本 PR で対応) / T-06 (PasskeyRepositoryImpl 更新 / 本 PR で対応) / T-07 (ServiceLocator は develop 上で既に satisfy) / T-08〜T-10 (テスト 3 ファイル) / T-11 (統合確認) 全て満たす
- 1.2 — T-01〜T-04 deliverables 不変: `git diff --name-only develop..HEAD` で `PasskeyEntity.kt` / `PasskeyDao.kt` / `Migration_4_5.kt` / `KeyNestDatabase.kt` / `5.json` 変更なしを確認
- 1.3 — 共有 spec 不変: `docs/specs/91-*` 配下の `requirements.md` / `design.md` / `tasks.md` に変更なし
- 1.4 — T-05 シグネチャ準拠: domain/model/Passkey.kt 新規追加 + PasskeyRepository interface が design §6.1 のメソッドセットを完備 (`findByCredentialId`/`findByRpIdAndUserHandle` 戻り値が `Passkey?`、`listDiscoverableByRpId`/`listAllByRpId` が `List<Passkey>`、`incrementSignCount` 直接 expose、`loadPrivateKey: ByteArray?`)。`signWithIncrement` は §6.1 の必須メソッドに含まれないが残置 (互換 API)、§6.1 必須要素は全て揃っている
- 1.5 — `passkey_<credentialId>` alias: `PasskeyRepositoryImpl.aliasFor` で `"passkey_$credentialId"` を返す。テスト `save_persistsKeyAlias_inEntity` で `"passkey_ABC"` を assert、`save_multipleCredentialIds_usesDistinctAliases` で `"passkey_id-A"` / `"passkey_id-B"` を確認
- 1.6 — delete: row → Keystore 順 + `DeletePasskeyResult.KeystoreCleanupFailed`: 既存 `PasskeyRepositoryImpl.delete` の構造 (dao.delete → keyStore.containsAlias → deleteEntry try-catch) は変更されておらず維持。新規追加 `delete_removesRow_fromUnderlyingDao_andTouchesKeystoreAlias` で end-to-end 検証 (row 消失 + alias 削除呼び出し)、既存 `delete_returns_KeystoreCleanupFailed_onKeyStoreException` で例外パスを担保
- 1.7 — ServiceLocator: 既存実装が `passkeyRepository: PasskeyRepository by lazy { ... }` を持ち変更なし (develop 上で satisfy 済)
- 1.8 — test ディレクトリ配置: 全テストが `app/src/test/java/.../data/` 配下に配置
- 2.1 — 新規テスト pass: impl-notes に「PasskeyRepositoryTest 23/23 / PasskeyDaoTest 14/14 / Migration_4_5_Test 5/5 / GetEntryBuilderTest 8/8 / PasskeyAuthActivityTest 8/8 / PasskeyCreatorTest 15/15 / 計 86/86」が記録されている
- 2.2 — 既存テスト非破壊: 815 ケース中 pre-existing AppInfoProviderTest 1 件のみ fail (本 Issue 範囲外、Developer が分離記述)
- 2.3 — lint baseline 内: 新規変更で警告 0、既存 baseline は不変
- 2.4 — assembleDebug 成功 (impl-notes)
- 2.5 — INTERNET permission 不変: AndroidManifest.xml 未変更、`InternetPermissionAbsenceTest` 維持
- 3.1〜3.3 — 親 #91 merge 済み前提: T-04 成果物 `5.json` / `PasskeyEntity.kt` / `PasskeyDao.kt` / `Migration_4_5.kt` 全て存在

### 共有 #91 requirements の Acceptance Criteria

- 1.1〜1.9 — PasskeyEntity 14 カラム + 制約: `Migration_4_5_Test.migrate_createsPasskeysTable_andPreservesCredentialsAndDetectedFields` が PRAGMA で検証
- 2.1〜2.5 — passkeys テーブル / INDEX / 既存テーブル非破壊 / version 5 / 5.json: `Migration_4_5_Test` 5 ケースで担保
- 3.1〜3.7 — DAO 8 メソッド: `PasskeyDaoTest` 14 ケースで全 cover
- 4.1 — AES-GCM 暗号化境界: `save_encryptsPrivateKey_and_stripsPlaintext_fromEntity` + 新規 `save_thenLoadPrivateKey_roundTripsPlaintext`
- 4.2 — passkey ごと alias (`passkey_<credentialId>`): `save_multipleCredentialIds_usesDistinctAliases` で `"passkey_id-A"` / `"passkey_id-B"` を確認 → **解消済み**
- 4.3 — delete: row→Keystore 順 + DeletePasskeyResult: `delete_returns_Success_onCleanSuccess` + 新規 `delete_removesRow_fromUnderlyingDao_andTouchesKeystoreAlias`
- 4.4 — 復号失敗時の例外伝播 (silent fail 禁止): `loadPrivateKey_propagatesAeadBadTag_whenCiphertextTampered` で `AEADBadTagException` 伝播を検証
- 4.5 / NFR 1.2 — suspend + IO: interface 宣言が全 suspend
- 5.1〜5.10 — テスト全件: DAO 14 / Migration 5 / Repository 23 で網羅
- NFR 1.1 / 2.1〜2.2 / 3.1〜3.2 / 4.1〜4.2 / 5.1〜5.3 — 命名 / 秘匿 / 境界 / schema: 新 `Passkey` domain 型は redacted `toString` を実装 (NFR 2.2)、既存 `InternetPermissionAbsenceTest` 維持で permission 境界担保

## Findings

なし (Round 1 reject 2 件は両方解消済み)。

## Summary

Round 1 reject の 2 件 (Finding 1: PasskeyRepository interface を design §6.1 シグネチャに揃える / Finding 2: Keystore alias を `passkey_<credentialId>` に揃える) は本 Round 2 で全て解消されている。新規 `domain/model/Passkey.kt` の追加、interface の domain 投影への移行、`listAllByRpId` の追加、`incrementSignCount` 直接 expose、`loadPrivateKey` nullable 化、alias 文字列の修正、テスト 5 ファイルの期待値追従が一貫して行われている。`signWithIncrement` の温存は design §6.1 の必須要素ではない互換 API としての判断であり、KDoc / impl-notes に経緯と将来整理計画が documented されている。テスト結果は新規 5 ケース追加 + 全 86/86 pass、AppInfoProviderTest 1 件 fail は本 Issue 範囲外の pre-existing。Req 1.1〜1.8 / 2.1〜2.5 / 3.1〜3.3 全て充足。

RESULT: approve
