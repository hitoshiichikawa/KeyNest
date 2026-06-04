# Implementation Notes — Issue #91 (T-01〜T-04: data layer scaffold)

## Scope

本サイクルで実装した範囲は **T-01〜T-04** のみ:

- T-01: `PasskeyEntity` 追加
- T-02: `PasskeyDao` 追加
- T-03: `Migration_4_5` 追加
- T-04: `KeyNestDatabase` v4→v5 + `5.json` export

T-05〜T-11 (Repository / DI / Tests / 統合) は **Issue #107** で別途実装される
スコープのため、本サイクルでは着手していない。

## 実装サマリ

| Task | Commit | 内容 |
|---|---|---|
| T-01 | `7fd3727` | `app/src/main/java/io/github/hitoshiichikawa/keynest/data/entity/PasskeyEntity.kt` 追加。14 カラム + `@Index` で `rpId` 単独 INDEX と `(rpId, userHandle)` UNIQUE INDEX を宣言。`equals` / `hashCode` / `toString` を手書きし、`ByteArray` 系 (`userHandle` / `encryptedPrivateKey` / `privateKeyIv`) は `toString` で size 表記のみ (NFR 2.2) |
| T-02 | `2c65509` | `app/src/main/java/io/github/hitoshiichikawa/keynest/data/dao/PasskeyDao.kt` 追加。design §5 の 8 メソッド (`insert` / `update` / `delete` / `findByCredentialId` / `findByRpIdAndUserHandle` / `listDiscoverableByRpId` / `listAllByRpId` / `incrementSignCount`)、全 `suspend`。各メソッドに用途 / silent no-op / atomic UPDATE の KDoc を付与 |
| T-03 | `a569d5e` | `app/src/main/java/io/github/hitoshiichikawa/keynest/data/migration/Migration_4_5.kt` 追加。`CREATE TABLE IF NOT EXISTS passkeys` (14 列 + PK + DEFAULT 1 / 0) + `CREATE INDEX IF NOT EXISTS index_passkeys_rpId` + `CREATE UNIQUE INDEX IF NOT EXISTS index_passkeys_rpId_userHandle` の 3 つの `execSQL` |
| T-04 | `b7a5dfc` + `c4b78c7` | `KeyNestDatabase.kt` を `version = 5` / `entities` に `PasskeyEntity::class` 追加 / `passkeyDao()` 抽象 fun 追加 / `addMigrations(... Migration_4_5)` 追記 / KDoc に `v4 -> v5` 追記。`app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/5.json` を KSP で生成して commit |

## 検証

| コマンド | 結果 |
|---|---|
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL (各タスク完了後に都度実行) |
| `./gradlew :app:kspDebugKotlin` | BUILD SUCCESSFUL (`5.json` を新規生成) |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL (Room compile-time schema validation pass) |
| `./gradlew :app:testDebugUnitTest` | 680 tests run, 1 failed — 唯一の失敗は `AppInfoProviderTest.get_returnsVersionNameFromBuildGradle` で、本 Issue 着手前から既存する問題 (`versionName` が build.gradle.kts では `"1.0.0"` だがテストは `"0.1.0"` を期待) であり、本 Issue の変更とは無関係。stash で T-01〜T-04 の差分を一時取り外した状態でも同一に失敗することを確認済み |

新規テストは Issue #107 (T-08 / T-09 / T-10) で追加されるため、本サイクルでは
テスト追加なし。

## 5.json 目視確認結果

`app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/5.json` の
主要 field を以下のとおり確認:

| 確認項目 | 期待 | 実測 |
|---|---|---|
| `database.version` | `5` | `5` ✓ |
| `entities[].passkeys.fields[]` 件数 | 14 | 14 ✓ |
| `entities[].passkeys.primaryKey.columnNames` | `["credentialId"]` (autoGenerate=false) | `["credentialId"]` (autoGenerate=false) ✓ |
| `entities[].passkeys.fields[].isDiscoverable.defaultValue` | `"1"` | `"1"` ✓ |
| `entities[].passkeys.fields[].signCount.defaultValue` | `"0"` | `"0"` ✓ |
| `entities[].passkeys.indices[]` 件数 | 2 | 2 ✓ |
| `index_passkeys_rpId` | `unique: false`, `columnNames: ["rpId"]` | 同 ✓ |
| `index_passkeys_rpId_userHandle` | `unique: true`, `columnNames: ["rpId", "userHandle"]` | 同 ✓ |
| `userHandle` / `encryptedPrivateKey` / `privateKeyIv` の affinity | `BLOB`, `notNull: true` | 同 ✓ |
| 既存 `credentials` / `detected_fields` entity の `fields[]` / `indices[]` | v4 と同一 | 同 ✓ (`4.json` と diff なし) |

`identityHash` は `8ce54462d9bdc8bc359c56f85853b9e2` で確定。`setupQueries` の
`room_master_table` `INSERT` 文と一致。

## design / tasks との差分

なし。design.md §3 (PasskeyEntity) / §4 (Migration_4_5) / §5 (PasskeyDao) の
確定 Kotlin / SQL シグネチャに完全準拠した。tasks.md は heading-only 構造で
進捗マーカーが存在しないため (前提どおり) 更新不要。

## 確認事項 (Reviewer / 人間向け)

特になし。design.md / tasks.md / requirements.md の指示と既存パターン
(`CredentialEntity` / `CredentialDao` / `Migration_3_4`) を踏襲して
矛盾なく実装できた。`AppInfoProviderTest` の既存失敗のみ事前注意:

- `AppInfoProviderTest.get_returnsVersionNameFromBuildGradle` は本 Issue
  以前から develop で失敗していた既存問題 (versionName の build.gradle.kts
  値 `"1.0.0"` に対しテストが `"0.1.0"` を期待) であり、本 Issue の変更とは
  独立。修正は本 Issue 範囲外のため touch していない。Reviewer 視点での
  既存テスト非破壊チェック時にはこのテストの失敗を本 Issue の責ではないと
  認識いただきたい。

## 次サイクル (Issue #107) への引き継ぎ

T-05〜T-11 は本サイクルで一切手を付けていない。Issue #107 で以下を実装する:

- **T-05**: `PasskeyRepository` interface + domain 型 (`Passkey` /
  `SavePasskeyRequest` / `DeletePasskeyResult`) を `domain/model/Passkey.kt`
  と `domain/repository/PasskeyRepository.kt` に追加 (design §6.1)
- **T-06**: `PasskeyRepositoryImpl` 実装。`AesGcmCipher` +
  `KeystoreKeyProvider(keyAlias = "passkey_<credentialId>")` を passkey
  ごとに new する暗号化境界を確立 (design §6 / §7)
- **T-07**: `ServiceLocator` に `passkeyRepository` lazy singleton 追加
  (design §8)
- **T-08**: `Migration_4_5_Test` 5 ケース (design §9.2)
- **T-09**: `PasskeyDaoTest` 14 ケース (design §9.3)
- **T-10**: `PasskeyRepositoryTest` 9 ケース。fake CryptoHelper で
  AndroidKeyStore に依存しない (design §9.4)
- **T-11**: 統合確認 (全テスト pass / 既存テスト非破壊 / PR 確認事項転記)

本サイクルで作成した `PasskeyEntity` / `PasskeyDao` / `Migration_4_5` /
`KeyNestDatabase` の API はすべて design.md の確定シグネチャに従っているため、
Issue #107 はそのまま参照可能。
