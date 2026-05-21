# Requirements Document

## 概要 / Goal / Non-Goal

### 概要

KeyNest は現在パスワードベースの credential のみを Room (`credentials` /
`detected_fields` テーブル) に保管している。umbrella Issue #89 で確定した
「Android Credential Manager API 経由の PassKey プロバイダ対応」の Phase 1 として、
本 Issue (#91) では **PassKey を保管するためのデータ層** を整備する。

具体的には、

- 新規 `PasskeyEntity` (`passkeys` テーブル) と対応する `PasskeyDao`
- 新規 `PasskeyRepository` インターフェースおよび実装 (DAO の上に AES-GCM
  暗号化 / 復号を挟むレイヤ)
- 新規 `Migration_4_5` (`passkeys` テーブル追加)
- `KeyNestDatabase.kt` の schema バージョン `4 → 5` 更新、`PasskeyEntity` /
  `PasskeyDao` 登録、新 migration の `addMigrations(...)` 追記
- 既存 `security/` ヘルパ (AES-GCM + Keystore) を PassKey の private key blob
  にも適用 (流用可能なら最小変更)

の 5 点を整え、後続サブ Issue (#89 分割案 3 「登録セレモニー」 / 分割案 4
「認証セレモニー」) が **DAO / Repository をそのまま呼び出して PassKey の保管 /
取り出しができる状態** までを到達点とする。

この Issue 単体では「PassKey が実際に生成される」「`CredentialProviderService`
の callback で実装に差し替わる」までは到達しない。`KeyNestCredentialProviderService`
本体の骨組み (Manifest 登録 + 空応答) は **並列 Issue #90** が担当する。

なお umbrella #89 のとおり、本機能で扱う名称表記は **「PassKey」** に統一する
(クラス名 / KDoc / コメント / ログ)。テーブル名・カラム名のみ既存命名規則
(snake_case + 慣用の `passkeys` 複数形) を優先する。

### Goal

- Room schema を v4 → v5 に上げ、`passkeys` テーブルを新規追加する。既存の
  `credentials` / `detected_fields` テーブルおよびそのデータには一切影響を
  与えない。
- `PasskeyEntity` で Issue #91 本文 schema 表の全カラムを保持する。
  `userHandle` は **BLOB (ByteArray)** で、`isDiscoverable` は INTEGER (0/1)
  で持つ。
- `passkeys` テーブルに `UNIQUE(rpId, userHandle)` 制約を付け、同一 RP +
  userHandle の重複登録を SQLite レベルで防ぐ。
- `PasskeyDao` で CRUD + `findByCredentialId` / `findByRpIdAndUserHandle` /
  `listDiscoverableByRpId` / `listAllByRpId` / `incrementSignCount` を提供する。
- `PasskeyRepository` (インターフェース + 実装) で private key 平文 ⇔
  `encryptedPrivateKey` (AES-GCM ciphertext + IV) の暗号化境界を確立する。
  Keystore wrapping key の alias は **passkey ごとに独立** (`passkey_<credentialId>`)
  で発番し、削除時に対応 Keystore エントリも廃棄できるようにする。
- 上記をカバーする migration テスト (`Migration_4_5_Test`) / DAO テスト
  (`PasskeyDaoTest`) / Repository テスト (`PasskeyRepositoryTest`) を追加する。

### Non-Goal (Out of Scope)

- `CredentialProviderService` の Service 登録 / Manifest 宣言 (並列 Issue #90
  が担当)。
- `onBeginCreateCredentialRequest` 実体: ES256 (P-256) keypair 生成、生体認証、
  `PublicKeyCredential` 返却 (#89 分割案 3「登録セレモニー」)。
- `onBeginGetCredentialRequest` 実体: エントリ提示、`BiometricPrompt`、assertion
  署名 (#89 分割案 4「認証セレモニー」)。
- 既存クレデンシャル一覧 UI への PassKey 表示 (#89 分割案 5)。
- PassKey 単位の rename / 削除 UI (#89 分割案 6)。
- 設定画面の「PassKey プロバイダ有効化」状態表示 / OS 設定導線 (#89 分割案 7)。
- README / Privacy Policy / Support ページの PassKey 取り扱い追記 (#89 分割案 8)。
- **AAGUID** (`2a56cf86-8332-4829-9f2a-e9a4adbc7abe`) を `authenticatorData` に
  埋め込む処理。AAGUID は登録セレモニー側で定数として扱うため、本 Issue では
  Entity に保持しない (#89 / Issue #91 本文で確定)。
- PassKey の **export / backup** (#89 で「不可」確定。同ポリシー)。
- StrongBox / 通常 Keystore の使い分け設計 (umbrella #89 のリスク項目として
  記載されているが、本 Issue では既存 `KeystoreKeyProvider` と同じ
  AndroidKeyStore (通常) を使い、StrongBox 切替は後続 Issue で扱う)。
- `signCount` 運用方針の確定 (常時 0 固定か RP ごとにインクリメントか)。
  Issue #91 本文「確認事項 2」のとおり登録 / 認証セレモニー Issue で議論し、
  本 Issue の DAO は両方に対応できる API (`incrementSignCount` 任意呼び出し)
  にとどめる。

## 背景

- umbrella Issue #89 で、KeyNest は Android Credential Manager API
  (`CredentialProviderService`) に PassKey プロバイダとして登録される方針が
  確定済み。WebAuthn 規格上、`credentialId` / `rpId` / `userHandle` /
  `signCount` 等のメタデータと ES256 (P-256) private key を端末側で保管する
  必要がある。
- 現状の `app/src/main/java/.../data/KeyNestDatabase.kt` は schema v4 まで
  (Issue #67 Phase 2 で `detected_fields` テーブル追加)。`PasskeyEntity` /
  `PasskeyDao` は未定義であり、PassKey を保管できない。
- 既存の `security/AesGcmCipher.kt` + `security/KeystoreKeyProvider.kt` は
  AES-256-GCM (`AES/GCM/NoPadding`, 12 byte IV, 128 bit auth tag) + AndroidKeyStore
  保管の wrapping key (alias `keynest_aead_v1`) を提供している。
  `CredentialEntity.passwordCiphertext` / `passwordIv` で実績があり、本 Issue の
  `encryptedPrivateKey` / IV 列もこの方式に乗せられる。
- 既存方針: `fallbackToDestructiveMigration` は OFF。すべての schema 変更は
  明示的 Migration を伴う (`Migration_3_4` 参考)。
- umbrella #89 で人間確認済みの決定 (本 Issue 関連分):
  - **両対応 (discoverable + non-discoverable)**: `isDiscoverable` フラグを
    schema に持つ。`listDiscoverableByRpId` はこのフラグで filter する。
  - **AAGUID**: `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` 固定。Entity には
    保持しない (本 Issue 範囲外、登録セレモニーで定数として
    `authenticatorData` に埋め込む)。
  - **Export / Backup 禁止**: 既存 credential と同ポリシー。DAO に export 系
    メソッドは追加しない。
- Issue #91 のコメントで人間確定済みの決定 (本ドキュメント「確定済の設計判断」
  セクションで詳述):
  - `userHandle` の格納型 = **BLOB**。
  - 同一 `rpId + userHandle` の重複登録 = **許可しない (UNIQUE 制約)**。
  - Keystore alias 発番 = **passkey ごとに独立 alias** (`passkey_<credentialId>`)。

## ユーザーストーリー

- As a 後続「登録セレモニー」Issue (#89 分割案 3) の実装担当, I want
  `PasskeyRepository.save(...)` を呼ぶだけで ES256 private key を AES-GCM 暗号化
  して保管できること, so that 自分の Issue では WebAuthn 登録レスポンス組み立て
  と OS / RP との往復に集中でき、暗号化 / DB 配線を再実装しなくて済む。
- As a 後続「認証セレモニー」Issue (#89 分割案 4) の実装担当, I want
  `PasskeyDao.findByCredentialId(...)` / `listDiscoverableByRpId(...)` /
  `findByRpIdAndUserHandle(...)` で必要な PassKey を 1 クエリで取り出せること,
  so that `allowCredentials` 経路 (non-discoverable) と usernameless 経路
  (discoverable) のどちらでもエントリ提示〜assertion 署名フローを組み立てられる。
- As a 既存ユーザー, I want 本 Issue による Room migration v4 → v5 後も既存
  credential / detected_fields のデータが完全に保持され、一覧 / autofill /
  編集が従前通り動作すること, so that PassKey 機能の追加によって既存資産を
  失わない。
- As a メンテナ, I want PassKey 削除時に対応する Keystore wrapping key
  エントリも一緒に廃棄されること, so that 「DB 上は消えたが Keystore には
  鍵が残り続ける」というリーク状態を作らない。

## 確定済の設計判断

> Issue #91 コメントで人間が確定した 3 つの決定事項。schema 確定後の変更には
> 追加 migration / API 変更を伴うため、本ドキュメントで明示的に固定する。

### 決定 1: `userHandle` の格納型 = BLOB (ByteArray)

- **決定**: `passkeys.userHandle` カラムは BLOB (Kotlin: `ByteArray`) で保持する。
- **理由**: WebAuthn 仕様 (W3C WebAuthn Level 2 §5.4.3) で `userHandle` は
  最大 64 byte の opaque byte sequence として定義されている。Entity が binary
  を直接持つことでレイヤ責務が明確になり、DAO 引数型も `ByteArray` で型安全に
  扱える。Base64url 文字列変換は不要 (デバッグ時のみ logcat で行えば十分)。
- **影響範囲**:
  - `PasskeyEntity.userHandle: ByteArray` (NOT NULL)
  - `PasskeyDao.findByRpIdAndUserHandle(rpId: String, userHandle: ByteArray)`
    の引数型
  - `PasskeyRepository` 公開 API の引数 / 戻り値の `userHandle` 型
  - `PasskeyDaoTest` / `PasskeyRepositoryTest` のテストデータは
    `ByteArray.of(...)` 系で構築

### 決定 2: 同一 `rpId + userHandle` の重複登録は許可しない (UNIQUE 制約あり)

- **決定**: `passkeys` テーブルに **`UNIQUE(rpId, userHandle)`** 制約を付与し、
  SQLite レベルで重複登録を拒否する。`PasskeyDao.findByRpIdAndUserHandle(...)`
  は 0 or 1 件を返す API (戻り値 `PasskeyEntity?`) とする。
- **理由**: 個人用パスワードマネージャである KeyNest では同一 RP に同一ユーザー
  が複数 PassKey を持つ実用シナリオが薄く、シンプルなスキーマが保守性を高める。
  登録時の上書き / エラー判定は呼び出し側 (登録セレモニー Issue) で
  `findByRpIdAndUserHandle` の戻り値 null/非 null を見て分岐する。複数対応が
  将来必要になった場合は migration で制約を落とせる (制約を後付けする方向より
  外す方向のほうが migration コストが低い)。
- **影響範囲**:
  - `Migration_4_5` の `CREATE TABLE` 文に
    `UNIQUE(\`rpId\`, \`userHandle\`)` を含める
  - `PasskeyEntity` の `@Entity(indices = [...])` で
    `Index(value = ["rpId", "userHandle"], unique = true)` を宣言
  - `PasskeyDao.findByRpIdAndUserHandle` の戻り値型は `PasskeyEntity?`
    (List ではない)
  - 重複 INSERT 時の挙動: SQLite が `SQLiteConstraintException` をスローする。
    Repository / 上位レイヤがこの例外を「重複検知」シグナルとして扱う前提

### 決定 3: Keystore wrapping key alias = passkey ごとに独立 (`passkey_<credentialId>`)

- **決定**: `encryptedPrivateKey` を保護する AES-GCM wrapping key は、
  passkey ごとに独立した AndroidKeyStore alias で管理する。alias 命名規則は
  **`passkey_<credentialId>`** とし、`credentialId` は Issue #91 本文の
  schema どおり base64url 文字列 (PassKey の PK) をそのまま使う。
- **理由**: 削除時に対応 Keystore エントリを確実に廃棄でき、鍵分離のセキュリティ
  が高い。`PasskeyRepository.delete(credentialId)` が DB の row 削除と Keystore
  alias 削除を同一トランザクション境界で実行できるため、「DB 上は消えたが鍵が
  残り続ける」リークを避けられる。Keystore エントリ数は passkey 数に比例して
  増加するが、個人向けアプリで passkey 数が膨大になることは想定しにくく、
  実用上の問題にはならない。
- **影響範囲**:
  - `PasskeyEntity.keyAlias: String` カラム (NOT NULL) に
    `passkey_<credentialId>` 形式の値を保持
  - `PasskeyRepository` 実装は `KeystoreKeyProvider` を **alias 注入可能** な
    形で利用する (既存 `KeystoreKeyProvider(keyAlias = "passkey_<id>")`
    コンストラクタ引数を活用)
  - `PasskeyRepository.delete(credentialId)` は DB の row 削除に加え、対応
    Keystore alias を `keyProvider.deleteKey()` 相当で廃棄する
  - 既存の `keynest_aead_v1` alias (credential 用 wrapping key) には影響を
    与えない (alias prefix が完全に異なる)
  - `PasskeyDaoTest` では Keystore は触らない (DAO は alias 文字列を保持する
    だけ)。`PasskeyRepositoryTest` で alias 命名規則と削除時の Keystore
    エントリ廃棄を検証する

## スコープ

### 新規追加するファイル

| 対象 | 概要 |
|---|---|
| `app/src/main/java/.../data/entity/PasskeyEntity.kt` | Room エンティティ。`passkeys` テーブル schema 表の全カラムを保持 |
| `app/src/main/java/.../data/dao/PasskeyDao.kt` | CRUD + `findByCredentialId` / `findByRpIdAndUserHandle` / `listDiscoverableByRpId` / `listAllByRpId` / `incrementSignCount` |
| `app/src/main/java/.../data/migration/Migration_4_5.kt` | `passkeys` テーブルを `CREATE TABLE IF NOT EXISTS` で追加。`UNIQUE(rpId, userHandle)` 制約と `rpId` INDEX を含む |
| `app/src/main/java/.../domain/repository/PasskeyRepository.kt` (interface) | 暗号化境界を持つ Repository API |
| `app/src/main/java/.../data/repository/PasskeyRepositoryImpl.kt` | `PasskeyRepository` 実装。`AesGcmCipher` + `KeystoreKeyProvider` 経由で private key を暗号化 / 復号 |
| `app/src/main/java/.../domain/model/Passkey*.kt` 等 | 必要に応じて domain モデル (`Passkey` / `EncryptedPasskeyRecord` / `PasskeyId` 相当)。最終粒度は design で決定 |
| `app/src/androidTest/.../data/migration/Migration_4_5_Test.kt` | v4 → v5 migration テスト |
| `app/src/androidTest/.../data/dao/PasskeyDaoTest.kt` | DAO 振る舞いテスト |
| `app/src/test/.../data/repository/PasskeyRepositoryTest.kt` (Robolectric or unit) | Repository 暗号化ラウンドトリップ / Keystore alias 削除テスト |
| `app/schemas/.../5.json` | Room exportSchema 出力 (`exportSchema = true` を維持するため) |

### 変更する既存ファイル

| 対象 | 変更内容 |
|---|---|
| `app/src/main/java/.../data/KeyNestDatabase.kt` | `@Database(entities = [..., PasskeyEntity::class], version = 5)` に更新。`abstract fun passkeyDao(): PasskeyDao` を追加。`addMigrations(..., Migration_4_5)` を追記。KDoc の schema history に v4 → v5 行を追加 |
| `app/src/main/java/.../security/AesGcmCipher.kt` | 変更しない (PassKey private key blob にも既存 API がそのまま使える) |
| `app/src/main/java/.../security/KeystoreKeyProvider.kt` | 変更しない (alias 注入可能な既存コンストラクタを `passkey_<credentialId>` で呼び出すだけ) |
| `app/src/main/java/.../security/EncryptedBlob.kt` | 変更しない (`iv` + `ciphertext` のペアをそのまま `encryptedPrivateKey` 列 + 別 IV 列で持つ。具体的なカラム分割は design で確定) |

### Out of Scope (再掲)

- `CredentialProviderService` 配線 (#90)
- 登録 / 認証セレモニーの実装本体 (#89 分割案 3 / 4)
- 一覧 UI への PassKey 表示 (#89 分割案 5)
- PassKey 単位の管理 UI (#89 分割案 6)
- 設定画面 / OS 設定導線 (#89 分割案 7)
- ドキュメント更新 (#89 分割案 8)
- AAGUID の `authenticatorData` 埋め込み (登録セレモニー Issue)
- Export / Backup 機能 (#89 で「不可」確定)

## データモデル仕様

### `passkeys` テーブル schema (確定版)

| カラム | 型 (SQLite) | Kotlin 型 | NULL | PK | 制約 / INDEX | 用途 |
|---|---|---|---|---|---|---|
| `credentialId` | TEXT | `String` | NOT NULL | **PK** | — | WebAuthn credentialId (base64url 文字列)。PassKey の一意キー |
| `rpId` | TEXT | `String` | NOT NULL | — | INDEX `index_passkeys_rpId` | Relying Party ID (`example.com` 等) |
| `rpDisplayName` | TEXT | `String?` | NULL 可 | — | — | RP の表示名 (Credential Manager UI 表示用) |
| `userHandle` | BLOB | `ByteArray` | NOT NULL | — | **UNIQUE(rpId, userHandle)** の構成要素 | ユーザー識別子 (RP が決定。最大 64 byte)。両対応のため non-discoverable でも RP から提供された値を常に保管 |
| `userName` | TEXT | `String?` | NULL 可 | — | — | RP が提示する username (表示用) |
| `userDisplayName` | TEXT | `String?` | NULL 可 | — | — | RP が提示する displayName (表示用) |
| `isDiscoverable` | INTEGER | `Boolean` | NOT NULL, DEFAULT 1 | — | — | discoverable credential として `listDiscoverableByRpId` で usernameless login 候補に含めるか。`false` の場合は credentialId 完全一致 (`allowCredentials`) でのみ取り出される |
| `encryptedPrivateKey` | BLOB | `ByteArray` | NOT NULL | — | — | AES-GCM 暗号化済み private key (ciphertext) |
| `privateKeyIv` | BLOB | `ByteArray` | NOT NULL | — | — | `encryptedPrivateKey` に対応する 12 byte GCM IV (既存 `password_ciphertext` / `password_iv` のペアと同方式) |
| `keyAlias` | TEXT | `String` | NOT NULL | — | — | Keystore wrapping key の alias。命名規則 `passkey_<credentialId>` |
| `signCount` | INTEGER | `Long` | NOT NULL, DEFAULT 0 | — | — | WebAuthn signature counter |
| `displayName` | TEXT | `String?` | NULL 可 | — | — | ユーザーが KeyNest 上で付ける別名 |
| `createdAt` | INTEGER | `Long` | NOT NULL | — | — | unix epoch ms |
| `lastUsedAt` | INTEGER | `Long?` | NULL 可 | — | — | unix epoch ms |

#### 補足

1. `isDiscoverable` は INTEGER で 0 / 1 を保持 (Room の Boolean 標準表現)。
   Migration の DEFAULT 値は `1` (true)。
2. `UNIQUE(rpId, userHandle)` は Room の
   `@Entity(indices = [Index(value = ["rpId", "userHandle"], unique = true)])`
   で宣言する。`Migration_4_5` の `CREATE TABLE` 文または別個の
   `CREATE UNIQUE INDEX` 文で同等の制約を生成する (どちらで実現するかは
   design で確定。Room がエクスポートする schema との diff がゼロになる方を
   選ぶ)。
3. `encryptedPrivateKey` と `privateKeyIv` は別カラムに分けて持つ
   (既存 `password_ciphertext` / `password_iv` と同じ方式)。1 カラムに連結する
   方式は採らない (12 byte 固定の IV を勝手に切り出す前提が将来の IV 長変更で
   壊れるため)。
4. Issue 本文 schema 表との対応:
   - 本文の単一カラム「`encryptedPrivateKey`」を、本仕様では
     `encryptedPrivateKey` + `privateKeyIv` の 2 カラムに分解する
     (上記 (3) と同じ理由)。
   - 本文の `keyAlias` の用途は決定 3 で確定 (`passkey_<credentialId>`)。
   - その他のカラムは Issue 本文 schema 表と完全一致。
5. `credentialId` を PK に採用するため、Room の `@PrimaryKey` には
   `autoGenerate = false` を指定する (base64url 文字列を呼び出し側が決定する)。

### Room exportSchema

`KeyNestDatabase` は既存方針どおり `exportSchema = true` を維持する。
本 Issue で `app/schemas/.../5.json` が新規生成される (5.json は CI で
schema 検証されるため、commit 必須)。

## DAO 公開 IF 一覧

> 全メソッドは `@Dao interface PasskeyDao { ... }` 内に定義する。
> Coroutines の主スレッド阻害を避けるため、単発操作はすべて `suspend`、
> 連続観測はすべて `Flow<...>` で返す。

| メソッド | 戻り値 cardinality | 戻り値型 | 種別 | 用途 |
|---|---|---|---|---|
| `insert(entity: PasskeyEntity)` | — | `Unit` (suspend) | mutation | 新規 PassKey 登録 (登録セレモニーから呼び出し)。`UNIQUE(rpId, userHandle)` / PK 衝突時は `SQLiteConstraintException` |
| `update(entity: PasskeyEntity)` | — | `Unit` (suspend) | mutation | 既存エントリの全カラム上書き (主に displayName 等 UI 起因の変更) |
| `delete(credentialId: String)` | — | `Unit` (suspend) | mutation | `DELETE FROM passkeys WHERE credentialId = :credentialId`。row が存在しない場合は silent no-op |
| `findByCredentialId(credentialId: String)` | 0 or 1 | `PasskeyEntity?` (suspend) | query | 認証セレモニーで `allowCredentials` に含まれる credentialId を完全一致で引く (両モード共通の lookup) |
| `findByRpIdAndUserHandle(rpId: String, userHandle: ByteArray)` | **0 or 1** | `PasskeyEntity?` (suspend) | query | 登録時の重複検知用 (決定 2 により UNIQUE 制約があるため 0 or 1) |
| `listDiscoverableByRpId(rpId: String)` | 0..N | `List<PasskeyEntity>` (suspend) | query | 認証セレモニーで `allowCredentials` 空のとき (usernameless login) に提示する候補。`WHERE rpId = :rpId AND isDiscoverable = 1 ORDER BY lastUsedAt DESC, createdAt DESC`。`lastUsedAt IS NULL` は末尾に並ぶ (SQLite の NULL ソートに従う / 必要なら `IS NULL` 専用句を挿入。最終 SQL は design で確定) |
| `listAllByRpId(rpId: String)` | 0..N | `List<PasskeyEntity>` (suspend) | query | KeyNest 内の管理 UI から呼び出し。`isDiscoverable` 値を問わず対象 RP の全 PassKey を返す。並び順は `listDiscoverableByRpId` と同じ |
| `incrementSignCount(credentialId: String, timestamp: Long)` | — | `Unit` (suspend) | mutation | `UPDATE passkeys SET signCount = signCount + 1, lastUsedAt = :timestamp WHERE credentialId = :credentialId`。row 不在時は silent no-op |

#### 設計メモ

1. `incrementSignCount` の signature は **`(credentialId, timestamp)`** を取る。
   `lastUsedAt` を `signCount` と同一 UPDATE 文で更新することで、認証セレモニー
   の hot path で SQL 1 発に収める (既存 `CredentialDao.updateLastUsedAt` と
   独立に呼ぶより atomic)。
2. 本 Issue の DAO には Flow を返す observer 系 (`observe*`) は **追加しない**。
   一覧 UI への PassKey 表示は #89 分割案 5 で実装されるため、それまで Flow
   API の需要がない。必要になった時点で別 Issue で追加する。
3. 既存 `CredentialDao` の `observeMetadata` / `clearAll` 相当 (全件削除や
   合計件数) も本 Issue では追加しない。設定画面 / Danger Zone への統合は
   #89 分割案 7 で実施する。
4. `delete(credentialId: String)` は entity ではなく PK を引数に取る形を採用
   (登録セレモニー / 認証セレモニーから呼ぶときに entity 取得→削除の二段
   コールを避ける)。

## Requirements

> EARS 形式 (The X shall …, When … the X shall …, While … the X shall …) で
> 記述。Issue #91 本文の Requirement 1〜5 をベースに、「確定済の設計判断」
> 3 項目および「DAO 公開 IF 一覧」を反映してアップデート。

### Requirement 1: データモデル

**Objective:** As a 後続セレモニー Issue 担当, I want PassKey に必要な
WebAuthn メタデータと暗号化済み private key が schema に揃っていること, so that
DB から取り出した値で `PublicKeyCredential` / assertion 署名を組み立てられる。

#### Acceptance Criteria

1.1. The `PasskeyEntity` shall 「データモデル仕様」表の全カラム
(`credentialId` / `rpId` / `rpDisplayName` / `userHandle` / `userName` /
`userDisplayName` / `isDiscoverable` / `encryptedPrivateKey` / `privateKeyIv` /
`keyAlias` / `signCount` / `displayName` / `createdAt` / `lastUsedAt`) を持つ。

1.2. The `credentialId` shall `passkeys` テーブルのプライマリキーであり、
`@PrimaryKey(autoGenerate = false)` で宣言される。

1.3. The `rpId` shall インデックスが張られている
(`Index(value = ["rpId"])`)。

1.4. The `(rpId, userHandle)` 組 shall UNIQUE インデックスが張られている
(`Index(value = ["rpId", "userHandle"], unique = true)`)。これは決定 2 の
SQLite レベル制約を反映する。

1.5. The `userHandle` shall BLOB (Kotlin: `ByteArray`) として保持され、
NOT NULL である (決定 1)。

1.6. The `encryptedPrivateKey` shall AES-GCM で暗号化された blob (BLOB) で
あり、平文の private key は DB に置かない。対応する 12 byte IV は別カラム
`privateKeyIv` (BLOB, NOT NULL) に保持する。

1.7. The `isDiscoverable` shall NOT NULL かつ DEFAULT 値 `1` (true) を持つ。

1.8. The `keyAlias` shall NOT NULL であり、命名規則 `passkey_<credentialId>`
に従う文字列を保持する (決定 3)。

1.9. The `signCount` shall NOT NULL かつ DEFAULT 値 `0` を持つ Long である。

### Requirement 2: Migration

**Objective:** As a 既存ユーザー, I want アプリ更新 (v4 → v5) で既存の
credential / detected_fields データが完全に保持されたまま PassKey 機能の
DB 層が追加されること, so that 既存資産を失わずに今後 PassKey を登録できる。

#### Acceptance Criteria

2.1. When Room schema を v4 → v5 にアップグレードしたとき, the `Migration_4_5`
shall `passkeys` テーブルを `CREATE TABLE IF NOT EXISTS` で新規作成する。

2.2. The `Migration_4_5` shall `passkeys` テーブルに対して以下を生成する:
(a) `credentialId` を PK とする `PRIMARY KEY` 制約, (b) `rpId` 列の INDEX,
(c) `(rpId, userHandle)` 列の UNIQUE INDEX, (d) `isDiscoverable` の DEFAULT 1,
(e) `signCount` の DEFAULT 0。

2.3. The `Migration_4_5` shall 既存の `credentials` / `detected_fields`
テーブル定義およびそのデータに影響を与えない (追加のみ。既存テーブルへの
`ALTER TABLE` は行わない)。

2.4. The `KeyNestDatabase` shall `version = 5` に更新し、`addMigrations(...)`
に `Migration_4_5` を追記する。`fallbackToDestructiveMigration` は引き続き
OFF (既存ポリシー維持)。

2.5. The Room exported schema (`app/schemas/.../5.json`) shall 本 Issue で
新規生成され、コミットされる (`exportSchema = true` 維持のため)。

### Requirement 3: DAO

**Objective:** As a 後続セレモニー Issue 担当, I want PassKey の CRUD と
WebAuthn フローに必要な検索 API が揃っていること, so that 登録 / 認証の各
セレモニーで DB アクセスを直書きせずに済む。

#### Acceptance Criteria

3.1. The `PasskeyDao` shall 「DAO 公開 IF 一覧」表に列挙された 8 メソッドを
すべて提供する (`insert` / `update` / `delete` / `findByCredentialId` /
`findByRpIdAndUserHandle` / `listDiscoverableByRpId` / `listAllByRpId` /
`incrementSignCount`)。

3.2. The `PasskeyDao.findByCredentialId(credentialId)` shall 0 or 1 件を
返す (戻り値型 `PasskeyEntity?`)。両モード (discoverable / non-discoverable)
共通の lookup として使用される。

3.3. The `PasskeyDao.findByRpIdAndUserHandle(rpId, userHandle)` shall 0 or 1
件を返す (戻り値型 `PasskeyEntity?`)。決定 2 の UNIQUE 制約により 2 件以上
返ることはない。

3.4. The `PasskeyDao.listDiscoverableByRpId(rpId)` shall `isDiscoverable = 1`
の PassKey のみを `lastUsedAt DESC, createdAt DESC` で返す。`isDiscoverable = 0`
のエントリは結果に含まれない。

3.5. The `PasskeyDao.listAllByRpId(rpId)` shall `isDiscoverable` 値に関わらず
対象 RP の全 PassKey を `lastUsedAt DESC, createdAt DESC` で返す。

3.6. The `PasskeyDao.incrementSignCount(credentialId, timestamp)` shall 単一
SQL UPDATE で `signCount` を `+1` し、同時に `lastUsedAt = :timestamp` を
更新する。

3.7. When `PasskeyDao.delete(credentialId)` が存在しない credentialId で
呼ばれたとき, the dao shall silent no-op (例外スローしない)。

### Requirement 4: Repository / 暗号化

**Objective:** As a 後続セレモニー Issue 担当, I want private key の平文を
DAO レイヤより上で見ずに済むこと, so that 暗号化境界が PasskeyRepository に
集約され、登録 / 認証セレモニーから漏洩する可能性が下がる。

#### Acceptance Criteria

4.1. The `PasskeyRepository` shall private key を保存時に AES-GCM
(`AES/GCM/NoPadding`, 12 byte IV, 128 bit auth tag) で暗号化し、取り出し時に
復号する。暗号化は既存 `AesGcmCipher` API で行う。

4.2. The `PasskeyRepository` shall PassKey ごとに独立した Keystore wrapping
key を `passkey_<credentialId>` alias で作成 / 取得する (決定 3)。新規 PassKey
保存時に対応 alias が AndroidKeyStore に未存在なら作成し、既存ならそれを
使い回す。

4.3. When `PasskeyRepository.delete(credentialId)` が呼ばれたとき, the repository
shall (a) `PasskeyDao.delete(credentialId)` で row を削除し、かつ (b) 対応する
Keystore alias `passkey_<credentialId>` を `KeystoreKeyProvider.deleteKey()`
相当で廃棄する。順序は「row 削除 → Keystore alias 削除」とし、Keystore 削除
失敗時の挙動は design で確定する (但し silent fail は不可。Requirement 4.4
参照)。

4.4. If 復号が失敗したとき, the repository shall ログ出力 (NFR 2 のとおり
平文・鍵バイト列・credentialId raw value を logcat に出さない方針を遵守) した
上で例外を呼び出し側に伝播する。silent fail (例外を握り潰して空 PassKey を
返す) は禁止する。

4.5. The `PasskeyRepository` shall すべての public メソッドを `suspend` で
公開し、Room / Keystore 呼び出しは IO Dispatcher 上で実行する (NFR 1 と整合)。
main thread を blocking する API は提供しない。

### Requirement 5: テスト

**Objective:** As a メンテナ, I want migration / DAO / Repository の振る舞いが
自動テストで回帰検知できること, so that 後続セレモニー Issue 実装中に Phase 1
データ層の退行を早期に検知できる。

#### Acceptance Criteria

5.1. The `Migration_4_5_Test` shall Room の `MigrationTestHelper` を用いて
v4 → v5 を実行し、`passkeys` テーブルが追加されること、および schema に
`isDiscoverable` カラムを含む 14 カラム全てが存在することを検証する。

5.2. The `Migration_4_5_Test` shall v4 schema で投入した
`credentials` テーブルの代表データ (id / package_name / username / label /
password_ciphertext / password_iv / created_at / updated_at / last_used_at /
custom_fields_ciphertext / custom_fields_iv) が v5 migration 後も完全に同一
内容で読み出せることを検証する。`detected_fields` についても同様に
代表データを保持して読み出せることを検証する。

5.3. The `Migration_4_5_Test` shall v5 にアップグレード後の `passkeys`
テーブルが (`rpId, userHandle`) UNIQUE 制約を持つこと、および `rpId` 単独
INDEX を持つことを検証する (PRAGMA `index_list` / `index_info` で確認)。

5.4. The `PasskeyDaoTest` shall `insert` / `update` / `delete` / `findByCredentialId`
/ `findByRpIdAndUserHandle` / `listDiscoverableByRpId` / `listAllByRpId` /
`incrementSignCount` の正常系挙動を検証する。

5.5. The `PasskeyDaoTest` shall `isDiscoverable = false` のエントリが
`listDiscoverableByRpId` の結果に **含まれず**、`listAllByRpId` と
`findByCredentialId` では取り出せることを検証する。

5.6. The `PasskeyDaoTest` shall 同一 `(rpId, userHandle)` で 2 件目を
`insert` したとき `SQLiteConstraintException` がスローされることを検証する
(決定 2 の UNIQUE 制約検証)。

5.7. The `PasskeyDaoTest` shall `incrementSignCount` 実行後に対象 row の
`signCount` が +1 され、`lastUsedAt` が引数 `timestamp` に更新されることを
検証する。存在しない `credentialId` で呼んだ場合は例外なく完了し、他 row に
影響を与えないことも検証する。

5.8. The `PasskeyRepositoryTest` shall 暗号化 / 復号のラウンドトリップ
(平文 private key → save → fetch → 復号後の平文が完全一致) を検証する。
Keystore 操作は Robolectric もしくは AndroidKeyStore 互換のテストダブルで
代替する。

5.9. The `PasskeyRepositoryTest` shall `save(credentialId = "X", ...)` 実行後
に AndroidKeyStore に alias `passkey_X` が存在し、`delete("X")` 実行後に
同 alias が削除されることを検証する (決定 3 の alias 命名規則 + 削除時
クリーンアップの検証)。

5.10. The `PasskeyRepositoryTest` shall 復号失敗時 (例: ciphertext を
意図的に改竄) に例外が呼び出し側へ伝播することを検証する
(`AEADBadTagException` 系または上位ラップ例外)。silent fail で空 PassKey が
返されないことを確認する。

## 既存テストへの影響

本 Issue は **既存の Migration / DAO / Repository / autofill 系テストを
壊さない** ことを必須要件とする。具体的には:

1. 既存の `Migration_1_2_Test` / `Migration_2_3_Test` / `Migration_3_4_Test`
   (存在する場合) は本 Issue の変更後も全て pass する。
2. `CredentialDaoTest` / `CredentialRepositoryImplTest` / `DetectedFieldDaoTest`
   等 既存 DAO / Repository テストは本 Issue の変更後も全て pass する
   (`KeyNestDatabase` の version bump および `addMigrations` への
   `Migration_4_5` 追記によって既存テストが追加 setup を必要としないことを
   確認する)。
3. `KeyNestAutofillService` 関連テスト (`FillResponseBuilderTest` /
   `LockedFillResponseSecurityTest` 等) は Schema バージョン変更の影響を受けない。
4. Room の compile-time schema check (`exportSchema = true` 経由の
   `app/schemas/.../5.json` 生成) が CI で fail しないように、新 schema JSON
   を必ずコミットする (Requirement 2.5)。

## Non-Functional Requirements

### NFR 1: スレッディング / 主スレッド阻害禁止

1. The `PasskeyDao` の全 public メソッド shall `suspend` または `Flow` で
   宣言され、Room 既定の Coroutines サポートにより IO スレッド上で実行される。
2. The `PasskeyRepository` の全 public メソッド shall `suspend` で公開され、
   実装内部で `withContext(Dispatchers.IO)` または同等の IO ディスパッチを
   行う (Keystore / AES-GCM の cryptographic ops を main thread で実行しない)。
3. The `PasskeyRepository` shall blocking I/O を返す `runBlocking` 系 API を
   提供しない。

### NFR 2: 秘密情報の取り扱い

1. The `PasskeyEntity` shall private key の **平文** を DB に保持しない
   (Requirement 1.6 再掲)。`encryptedPrivateKey` は常に AES-GCM ciphertext。
2. The `PasskeyEntity.toString()` shall 暗号化 blob (`encryptedPrivateKey` /
   `privateKeyIv`) と `userHandle` を **サイズ表記のみ** で出力する
   (既存 `CredentialEntity.toString()` と同方式)。raw bytes / base64 値を
   logcat に出さない。
3. The `PasskeyRepository` shall 復号後の private key 平文を Logcat に
   出力しない。例外メッセージにも含めない。
4. The `PasskeyRepository` shall `credentialId` (base64url) を Logcat に
   出力する場合、必要最小限の頻度に絞り、`info` レベル以上では出さない
   (debug レベルのみ。本 Issue では追加ログを最小化する方針で、必要な
   ログ仕様は design で確定)。
5. The 復号失敗 (auth tag mismatch / IV 不整合 / Keystore alias 欠落) shall
   silent fail せず例外として伝播する (Requirement 4.4 再掲)。

### NFR 3: セキュリティ境界の不変

1. The 本 Issue 変更 shall `android.permission.INTERNET` を追加しない
   (umbrella #89 のネット境界ポリシー維持)。
2. The 本 Issue 変更 shall 既存 `keynest_aead_v1` alias の Keystore エントリに
   触れない (passkey 用 alias は `passkey_` prefix で完全分離。Requirement
   4.2 / 決定 3 と整合)。
3. The Keystore wrapping key spec shall 既存 `KeystoreKeyProvider` 既定値
   (AES-256, GCM, `setRandomizedEncryptionRequired(true)`,
   `setUserAuthenticationRequired(false)`) と同等の設定で作成される
   (PassKey 用 alias であっても spec を別途定義しない)。

### NFR 4: 命名 / 表記

1. The 新規追加クラス名 / KDoc / コメント / ログメッセージ shall PassKey
   機能に言及する箇所で **「PassKey」** 表記を用いる (umbrella #89 確認事項 3
   で人間確定済み)。
2. The Room テーブル名 / カラム名 shall 既存スタイル (snake_case + 慣用の
   英語複数形 / camelCase の混在は既存 `CredentialEntity` 準拠) を踏襲する。
   テーブル名は **`passkeys`** (複数形) で確定。カラム名は Issue #91 本文
   schema 表のとおり camelCase (`credentialId` / `rpId` 等) を採用する。
   この命名は既存 `credentials` テーブルとは異なる方針だが、Issue 本文で
   既に確定しているため踏襲する。

### NFR 5: スキーマバージョニング

1. The `KeyNestDatabase` shall `exportSchema = true` を維持し、
   `app/schemas/.../5.json` を新規生成する。
2. The schema バージョンは v4 → **v5** に上げる。v4 はスキップしない
   (連番)。
3. The `fallbackToDestructiveMigration` shall OFF を維持する (既存ポリシー)。

## Out of Scope

> Issue #91 本文の Out of Scope をそのまま記載 + AAGUID は本 Issue 範囲外を
> 再掲。

- `CredentialProviderService` の Service 登録 (並列 Issue: #90)。
- 登録 / 認証セレモニーの **実装本体** (#89 分割案 3 / 4)。
- 一覧 UI への PassKey 表示 (#89 分割案 5)。
- PassKey 単位の rename / 削除 UI (#89 分割案 6)。
- 設定画面 / OS 設定導線 (#89 分割案 7)。
- README / Privacy Policy / Support ページ更新 (#89 分割案 8)。
- PassKey の **export / backup** (#89 で「不可」確定。同ポリシー)。
- **AAGUID** (`2a56cf86-8332-4829-9f2a-e9a4adbc7abe`) を `authenticatorData`
  に埋め込む処理 (登録セレモニー Issue)。本 Issue では entity に保持しない
  (Issue #91 本文「#89 確定事項の反映」より再掲)。
- StrongBox / 通常 Keystore の使い分け設計 (umbrella #89 リスク項目)。
- `signCount` 運用方針の確定 (常時 0 固定 / RP ごとにインクリメント) 。本
  Issue の DAO は両対応 API を提供するにとどめる。
- 一覧 UI / 設定 UI から `passkeys` テーブルを参照する Flow API
  (本 Issue では `suspend` 系のみ。Flow 追加は需要発生時に別 Issue)。

## 未解決事項

> 本 Issue で人間確定済の 3 論点 (userHandle 型 / UNIQUE 制約 / Keystore
> alias 発番方式) は「確定済の設計判断」セクションで反映済み。以下は
> design / 実装フェーズで決着が必要な細部のみ列挙する。

1. **`Migration_4_5` での UNIQUE 制約生成方法**: `CREATE TABLE` 文の中に
   `UNIQUE(rpId, userHandle)` を書くか、別途 `CREATE UNIQUE INDEX
   index_passkeys_rpId_userHandle ON passkeys(rpId, userHandle)` 文を発行する
   か。Room がエクスポートする `5.json` schema との diff がゼロになる方を
   design で確認のうえ確定する (Requirement 2.2 / 5.3 はどちらの実現方式でも
   pass する書き方になっている)。

2. **`listDiscoverableByRpId` / `listAllByRpId` の `lastUsedAt NULL` 並び順**:
   SQLite 既定では NULL は ASC で先頭 / DESC で先頭になる (実装による)。
   "使ったことのある PassKey を上に出す" UX を優先するなら
   `ORDER BY lastUsedAt IS NULL ASC, lastUsedAt DESC, createdAt DESC` の
   ように NULL を末尾に明示する句が必要。最終 SQL は design で確定する。

3. **`PasskeyRepository.delete` における Keystore alias 削除失敗時の挙動**:
   DB row 削除は成功したが Keystore `deleteKey()` が失敗した場合に、
   (a) 例外をそのまま伝播してリトライ可能にする、(b) DB 削除はコミット済として
   扱い Keystore 削除失敗を warning ログ + 結果通知で返す、のどちらを採用するか。
   Requirement 4.3 / 4.4 は「silent fail 禁止」までは確定しているが、上位への
   通知粒度 (例外型) は design で決定する。

4. **PassKey 用 `Passkey` / `EncryptedPasskeyRecord` / `PasskeyId` domain 型の
   配置**: 既存 `domain/model/Credential` / `EncryptedCredentialRecord` /
   `CredentialId` と同様の三層 (encrypted record / domain aggregate / id
   value class) を採るか、本 Issue 範囲では Entity ↔ Repository 直接で済ませて
   後続 Issue で domain 型を追加するか。本 Issue 単体では UI 連携がないため
   どちらでも動くが、後続セレモニー Issue が再利用する型を本 Issue で確定して
   おくのが望ましい。

5. **`androidx.room` バージョン**: 並列 Issue #90 で `androidx.credentials`
   依存追加が議論されている。本 Issue は Room の追加機能 (例:
   `MigrationTestHelper` の最新版 API) を要求しないため、現行の Room バージョン
   で実装可能と推定するが、design 段階で `gradle/libs.versions.toml` の現行
   Room バージョンが `Migration_4_5_Test` で必要な API を満たすかを最終確認する。

## 関連 Issue / PR

- **Parent**: #89 (umbrella: feat(passkey): Android Credential Manager 経由の
  passkey プロバイダ対応)
- **並列実施可**: #90 (CredentialProviderService の Manifest 登録と最小骨組み)
- **後続予定** (#89 分割案):
  - #89 分割案 3: 登録セレモニー (`onBeginCreateCredentialRequest` 実体)。
    本 Issue の `PasskeyRepository.save(...)` を呼び出す。
  - #89 分割案 4: 認証セレモニー (`onBeginGetCredentialRequest` 実体)。
    本 Issue の `PasskeyDao.findByCredentialId` / `listDiscoverableByRpId` /
    `findByRpIdAndUserHandle` / `incrementSignCount` を呼び出す。
- **参考**:
  - W3C WebAuthn Level 2: https://www.w3.org/TR/webauthn-2/
  - WebAuthn Credential Properties:
    https://www.w3.org/TR/webauthn-2/#sctn-credential-storage-modality
  - Android Credential Provider:
    https://developer.android.com/training/sign-in/credential-provider

## 用語集

- **PassKey**: WebAuthn / FIDO2 で定義される public-key credential。本リポジトリ
  では umbrella #89 確定により「PassKey」表記で統一する。
- **credentialId**: WebAuthn における credential の一意識別子。本 Issue では
  base64url 文字列形式で `passkeys.credentialId` (PK) に保持する。
- **userHandle**: RP がユーザーを識別するための opaque byte sequence
  (最大 64 byte, WebAuthn Level 2 §5.4.3)。本 Issue では BLOB
  (`ByteArray`) として保持する (決定 1)。
- **rpId**: Relying Party ID。`example.com` 等のドメイン文字列。
- **discoverable credential (resident key)**: ユーザー名入力なしで RP に提示
  できる PassKey。`isDiscoverable = true` の行が該当。
- **non-discoverable credential**: `allowCredentials` が必須となる PassKey。
  `isDiscoverable = false` の行が該当。
- **signCount**: WebAuthn signature counter。replay 攻撃検知のため認証ごとに
  単調増加が期待される値 (RP の検証ポリシーによっては 0 固定も許容)。
- **AAGUID**: Authenticator Attestation GUID。KeyNest authenticator を識別する
  128bit UUID。umbrella #89 で `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` 確定。
  本 Issue では entity に保持しない (#89 / Issue #91 本文の確定事項)。
- **wrapping key**: PassKey の private key を AES-GCM で暗号化する Keystore
  管理 AES-256 鍵。本 Issue では PassKey ごとに独立 alias
  (`passkey_<credentialId>`) で管理する (決定 3)。
- **Migration_4_5**: Room schema v4 → v5 移行スクリプト。本 Issue で新規追加。
- **空応答 (empty response)**: 並列 Issue #90 用語。本 Issue では使用しない
  (本 Issue は DAO / Repository を実体実装するため)。
