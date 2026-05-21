# Task Breakdown — Issue #91 / feat(passkey): Room migration + PasskeyEntity / DAO の追加

> 関連: `requirements.md`, `design.md`（本ディレクトリ）
>
> 各タスクは独立コミット可能な粒度で、依存順に並べている。Developer はこの順番で実装する。
>
> 略号:
> - **req**: `requirements.md` の Requirement 番号
> - **NFR**: `requirements.md` の Non-Functional Requirement 番号
> - 設計の詳細は `design.md` の対応セクション (`§N.M`) を参照

---

## T-01: PasskeyEntity の追加

### 概要

`passkeys` テーブルの Room エンティティを新規追加する。design §3.1 のとおり 14 カラムを保持し、`@Index` で `rpId` 単独 INDEX と `(rpId, userHandle)` UNIQUE INDEX を宣言する。既存 `CredentialEntity` の書式（`@ColumnInfo(typeAffinity = ColumnInfo.BLOB)` 明示、`ByteArray` の `equals` / `hashCode` / `toString` を `contentEquals` / `contentHashCode` で手書き、暗号 blob は `toString` でサイズ表記のみ）を踏襲する。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/entity/PasskeyEntity.kt`

### 公開 IF（design §3.1 確定形）

`PasskeyEntity` data class:
- `@Entity(tableName = "passkeys", indices = [Index(value = ["rpId"]), Index(value = ["rpId", "userHandle"], unique = true)])`
- `@PrimaryKey(autoGenerate = false) credentialId: String`
- 残りカラムは design §3.3 の型対応表どおり
- `isDiscoverable` に `@ColumnInfo(name = "isDiscoverable", defaultValue = "1")`
- `signCount` に `@ColumnInfo(name = "signCount", defaultValue = "0")`
- `userHandle` / `encryptedPrivateKey` / `privateKeyIv` に `typeAffinity = ColumnInfo.BLOB` を明示
- `equals` / `hashCode` / `toString` を手書き（`userHandle` / `encryptedPrivateKey` / `privateKeyIv` は size 表記のみで logcat 出力 — NFR 2.2）

### 受入基準（要件対応）

- **req 1.1**: 14 カラム全てが Entity に存在
- **req 1.2**: `credentialId` に `@PrimaryKey(autoGenerate = false)`
- **req 1.3**: `rpId` に `@Index(value = ["rpId"])`
- **req 1.4**: `(rpId, userHandle)` に `@Index(unique = true)`
- **req 1.5**: `userHandle: ByteArray` で NOT NULL
- **req 1.6**: `encryptedPrivateKey: ByteArray` / `privateKeyIv: ByteArray` の 2 カラム分離
- **req 1.7**: `isDiscoverable` に `defaultValue = "1"`
- **req 1.8**: `keyAlias: String` で NOT NULL
- **req 1.9**: `signCount: Long` に `defaultValue = "0"`
- **NFR 2.1 / 2.2**: `toString()` が暗号 blob / userHandle を size 表記のみで出力

### テストケース

このタスク単体ではテストを追加しない（DAO テスト T-02、Repository テスト T-09 / T-10 でカバーされる）。コンパイル可能であれば良い。

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功（KSP が `@Entity` を解釈してエラーを出さない）
- 既存テストは触らないので `./gradlew :app:testDebugUnitTest` は引き続き全 pass（ただし schema validation は T-04 まで遅延）

### 依存タスク

- なし（先頭）

---

## T-02: PasskeyDao の追加

### 概要

`PasskeyEntity` を CRUD + WebAuthn フロー検索する DAO を新規追加する。design §5 の確定シグネチャに従い、`suspend` のみ（Flow 系は本 Issue 範囲外）。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/dao/PasskeyDao.kt`

### 公開 IF（design §5 確定形）

`@Dao interface PasskeyDao` に以下 8 メソッド:

```kotlin
@Insert
suspend fun insert(entity: PasskeyEntity)

@Update
suspend fun update(entity: PasskeyEntity)

@Query("DELETE FROM passkeys WHERE credentialId = :credentialId")
suspend fun delete(credentialId: String)

@Query("SELECT * FROM passkeys WHERE credentialId = :credentialId LIMIT 1")
suspend fun findByCredentialId(credentialId: String): PasskeyEntity?

@Query("SELECT * FROM passkeys WHERE rpId = :rpId AND userHandle = :userHandle LIMIT 1")
suspend fun findByRpIdAndUserHandle(rpId: String, userHandle: ByteArray): PasskeyEntity?

@Query(
    "SELECT * FROM passkeys WHERE rpId = :rpId AND isDiscoverable = 1 " +
        "ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC"
)
suspend fun listDiscoverableByRpId(rpId: String): List<PasskeyEntity>

@Query(
    "SELECT * FROM passkeys WHERE rpId = :rpId " +
        "ORDER BY (lastUsedAt IS NULL) ASC, lastUsedAt DESC, createdAt DESC"
)
suspend fun listAllByRpId(rpId: String): List<PasskeyEntity>

@Query(
    "UPDATE passkeys SET signCount = signCount + 1, lastUsedAt = :timestamp " +
        "WHERE credentialId = :credentialId"
)
suspend fun incrementSignCount(credentialId: String, timestamp: Long)
```

KDoc に各メソッドの用途（design §5 / req §「DAO 公開 IF 一覧」）と「row 不在時は silent no-op」「`findByRpIdAndUserHandle` は UNIQUE 制約により 0 or 1」「`incrementSignCount` は単一 UPDATE で atomic」を明記する。

### 受入基準（要件対応）

- **req 3.1**: 8 メソッド全て実装
- **req 3.2**: `findByCredentialId` の戻り値が `PasskeyEntity?`
- **req 3.3**: `findByRpIdAndUserHandle` の戻り値が `PasskeyEntity?` で引数 `userHandle: ByteArray`
- **req 3.4**: `listDiscoverableByRpId` の `WHERE` 句で `isDiscoverable = 1`
- **req 3.5**: `listAllByRpId` で `isDiscoverable` filter なし
- **req 3.6**: `incrementSignCount` が単一 UPDATE で `signCount + 1` と `lastUsedAt = :timestamp` を同時更新
- **req 3.7**: `delete` の `@Query` 文に `LIMIT` を付けず、存在しない行に対しても silent no-op
- **NFR 1.1**: 全 public メソッドが `suspend`

### テストケース

このタスク単体ではテストを追加しない（T-04 完了後の T-10 で in-memory DB を作って一括テストする）。

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功（KSP が `@Query` 文を validate して "no such column" 等のエラーを出さない — T-01 で entity が用意されていれば pass する）

### 依存タスク

- T-01（参照する `PasskeyEntity` が必要）

---

## T-03: Migration_4_5 の追加

### 概要

Room schema v4 → v5 の migration を追加する。design §4 の確定 SQL に従い、`passkeys` テーブルの `CREATE TABLE IF NOT EXISTS` + 2 つの INDEX (`CREATE INDEX` + `CREATE UNIQUE INDEX`) を発行する。既存 `Migration_3_4` と同じ `object Migration_4_5 : Migration(4, 5)` パターン。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/migration/Migration_4_5.kt`

### 公開 IF（design §4 確定形）

`object Migration_4_5 : Migration(4, 5)` の `migrate(db: SupportSQLiteDatabase)` 内で、design §4 のコードスケッチどおりに 3 つの execSQL を順に発行する:

1. `CREATE TABLE IF NOT EXISTS \`passkeys\` (...)` — 14 列 + `PRIMARY KEY(credentialId)` + DEFAULT 1 / 0
2. `CREATE INDEX IF NOT EXISTS \`index_passkeys_rpId\` ON \`passkeys\` (\`rpId\`)`
3. `CREATE UNIQUE INDEX IF NOT EXISTS \`index_passkeys_rpId_userHandle\` ON \`passkeys\` (\`rpId\`, \`userHandle\`)`

KDoc に Migration の意図（PassKey storage for Issue #91 / parent #89）、`fallbackToDestructiveMigration` OFF 維持、列順序と DEFAULT 値が `5.json` の `createSql` と一致する必要性、`IF NOT EXISTS` 句による冪等性を記載する。

### 受入基準（要件対応）

- **req 2.1**: `passkeys` テーブルを `CREATE TABLE IF NOT EXISTS` で新規作成
- **req 2.2(a)**: `credentialId` PK
- **req 2.2(b)**: `rpId` 単独 INDEX
- **req 2.2(c)**: `(rpId, userHandle)` UNIQUE INDEX
- **req 2.2(d)**: `isDiscoverable` の DEFAULT 1
- **req 2.2(e)**: `signCount` の DEFAULT 0
- **req 2.3**: 既存 `credentials` / `detected_fields` テーブルに `ALTER TABLE` を発行しない（追加 SQL のみ）

### テストケース

このタスク単体ではテストを追加しない（T-09 でカバー）。

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功

### 依存タスク

- T-01（PasskeyEntity が存在すること自体は migration 実装には不要だが、`5.json` 生成と整合を取るため T-01 完了後が望ましい）

---

## T-04: KeyNestDatabase.kt の version up + entities/migration 登録

### 概要

`KeyNestDatabase` の `@Database(version = 5, entities = [...PasskeyEntity::class])` 更新、`abstract fun passkeyDao(): PasskeyDao` 追加、`addMigrations(..., Migration_4_5)` 追記、KDoc の schema history 追記、および KSP による `5.json` 自動生成 + commit を行う。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/KeyNestDatabase.kt`
  - `@Database` の `entities` に `PasskeyEntity::class` を追加、`version = 5` に変更
  - `abstract fun passkeyDao(): PasskeyDao` を追加
  - `addMigrations(Migration_1_2, Migration_2_3, Migration_3_4, Migration_4_5)` に変更
  - KDoc の `Schema history:` セクションに `v4 -> v5 ([Migration_4_5]): creates the passkeys table for PassKey credential storage (Issue #91 / parent #89).` を追加
- 新規 (KSP 自動生成 / commit 必須): `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/5.json`

### 公開 IF

`KeyNestDatabase.passkeyDao(): PasskeyDao` が ServiceLocator / テストから参照可能になる。

### 受入基準（要件対応）

- **req 2.4**: `version = 5` と `addMigrations(... Migration_4_5)` 追記
- **req 2.5 / NFR 5.1**: `app/schemas/.../5.json` が KSP で生成され commit される
- **NFR 5.2 / 5.3**: schema バージョンが v5（連番）、`fallbackToDestructiveMigration` 引き続き OFF

### テストケース

このタスク単体ではテストを追加しない。ただし完了条件として `./gradlew :app:kspDebugKotlin` を実行して `5.json` を生成し、内容を以下の観点で目視確認する:

- `database.version` が `5`
- `entities[]` に `passkeys` が含まれ、`fields[]` が 14 件、列順序が Entity 宣言順と一致
- `indices[]` に `index_passkeys_rpId` (unique = false) と `index_passkeys_rpId_userHandle` (unique = true) が含まれる
- `isDiscoverable` / `signCount` の `defaultValue` が `"1"` / `"0"` で記録される

### 完了条件

- `./gradlew :app:kspDebugKotlin` 成功、`5.json` が生成されている
- `./gradlew :app:assembleDebug` 成功（Room の compile-time schema validation が pass）
- `./gradlew :app:testDebugUnitTest` で既存テストが全 pass（既存 Migration / DAO テストは引き続き動作する）

### 依存タスク

- T-01（PasskeyEntity）
- T-02（PasskeyDao）
- T-03（Migration_4_5）

---

## T-05: PasskeyRepository インターフェース定義

### 概要

`domain/repository/PasskeyRepository.kt` 新規追加。design §6.1 のシグネチャに従う。同時に必要な domain 型 (`Passkey` / `SavePasskeyRequest` / `DeletePasskeyResult`) を `domain/model/Passkey.kt` に追加する。`EncryptedPasskeyRecord` 型は本 Issue では追加しない（design §12 / 未解決事項 4）。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/model/Passkey.kt`
  - `Passkey` data class（metadata aggregate — `encryptedPrivateKey` / `privateKeyIv` を持たない）
  - `SavePasskeyRequest` data class（登録セレモニーが渡す DTO — 平文 `privateKey: ByteArray` を含む）
  - `DeletePasskeyResult` sealed class（`Success` / `KeystoreCleanupFailed(cause: Throwable)`）
- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/PasskeyRepository.kt`

### 公開 IF（design §6.1 確定形）

```kotlin
interface PasskeyRepository {
    suspend fun save(request: SavePasskeyRequest)
    suspend fun findByCredentialId(credentialId: String): Passkey?
    suspend fun findByRpIdAndUserHandle(rpId: String, userHandle: ByteArray): Passkey?
    suspend fun listDiscoverableByRpId(rpId: String): List<Passkey>
    suspend fun listAllByRpId(rpId: String): List<Passkey>
    suspend fun incrementSignCount(credentialId: String, timestamp: Long)
    suspend fun loadPrivateKey(credentialId: String): ByteArray?
    suspend fun delete(credentialId: String): DeletePasskeyResult
}
```

`SavePasskeyRequest` には credentialId / rpId / rpDisplayName / userHandle / userName / userDisplayName / isDiscoverable / privateKey (平文) / displayName / createdAt フィールド。KDoc で「呼び出し元が `privateKey` の wipe 責務を持つ」「`signCount` は登録時 0 / `lastUsedAt` は null から開始」を明記。

### 受入基準（要件対応）

- **req 4.1**: 公開 IF 上で「private key を平文受け取り / 平文返却」の境界が明示される（Repository より上で平文を扱う設計）
- **NFR 1.3**: `Passkey` aggregate には `encryptedPrivateKey` / `privateKeyIv` を含めず、metadata のみで構成
- **NFR 2.2**: `SavePasskeyRequest.toString()` も `privateKey` を size 表記のみで出力

### テストケース

なし（interface 定義 + DTO のみ）。

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功

### 依存タスク

- なし（T-01〜T-04 と独立。T-06 で参照されるため T-06 より前に完了）

---

## T-06: PasskeyRepositoryImpl 実装

### 概要

`PasskeyRepository` の実装を `data/repository/PasskeyRepositoryImpl.kt` に追加する。design §6 / §7 のとおり、`AesGcmCipher` + `KeystoreKeyProvider(keyAlias = "passkey_<credentialId>")` の組を **passkey ごとに new** して暗号化境界を確立する。IO Dispatcher は `withContext(Dispatchers.IO)` で明示。復号失敗は例外伝播。delete は `DeletePasskeyResult` 結果型で返す（例外伝播しない）。

### 変更ファイル

- 新規: `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryImpl.kt`

### 公開 IF（design §6.2 / §7 確定形）

```kotlin
class PasskeyRepositoryImpl(
    private val dao: PasskeyDao,
    private val cipherFactory: (alias: String) -> AesGcmCipher = { AesGcmCipher(KeystoreKeyProvider(keyAlias = it)) },
    private val keyProviderFactory: (alias: String) -> KeystoreKeyProvider = { KeystoreKeyProvider(keyAlias = it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PasskeyRepository {
    // save: cipherFactory(aliasFor(id)).encrypt(privateKey) → dao.insert(...)
    // loadPrivateKey: dao.findByCredentialId → cipherFactory(entity.keyAlias).decrypt(...)
    // delete: dao.findByCredentialId → dao.delete → keyProviderFactory(alias).deleteKey()
    //         try-catch で KeystoreException を DeletePasskeyResult.KeystoreCleanupFailed に変換
    // findByCredentialId / findByRpIdAndUserHandle / listDiscoverableByRpId /
    //         listAllByRpId / incrementSignCount: dao 委譲のみ + Entity → Passkey マッピング

    companion object { internal fun aliasFor(credentialId: String): String = "passkey_$credentialId" }
}
```

Entity → `Passkey` のマッピング private 拡張関数 `PasskeyEntity.toDomain(): Passkey` を併設する（既存 `CredentialEntity.toDomain()` と同じ作り）。

### 受入基準（要件対応）

- **req 4.1**: `AesGcmCipher` 経由で AES-GCM 暗号化 / 復号
- **req 4.2**: `passkey_<credentialId>` alias で `KeystoreKeyProvider` を都度 new し独立 alias を作成 / 取得
- **req 4.3**: `delete` で row 削除 → Keystore alias 削除の順、結果を `DeletePasskeyResult` で返す（design §7.4）
- **req 4.4**: 復号失敗時に例外を伝播（`AEADBadTagException`）、silent fail 禁止。`SafeLogger.warn` で warn ログ（`credentialId` raw を tag / message に出さない — NFR 2.4）
- **req 4.5 / NFR 1.2**: 全 public メソッドを `suspend` + `withContext(ioDispatcher)` で実行
- **NFR 1.3 / 2.3**: 例外メッセージや log message に平文 private key / 復号 byte を含めない。Repository 内で `request.privateKey` を wipe しない（呼び出し元責務）

### テストケース

このタスク単体ではテストを追加しない（T-10 で一括）。

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功
- `./gradlew :app:assembleDebug` 成功

### 依存タスク

- T-02（PasskeyDao）
- T-04（KeyNestDatabase に PasskeyDao がぶら下がる）
- T-05（PasskeyRepository interface + domain 型）

---

## T-07: ServiceLocator に PasskeyRepository を追加

### 概要

`di/ServiceLocator.kt` に `passkeyRepository` lazy singleton を追加する。既存 `credentialRepository` / `aesGcmCipher` / `keystoreKeyProvider` には触れない（NFR 3.2）。Hilt module は追加しない（design §8）。

### 変更ファイル

- 変更: `app/src/main/java/io/github/hitoshiichikawa/keynest/di/ServiceLocator.kt`
  - `import io.github.hitoshiichikawa.keynest.data.repository.PasskeyRepositoryImpl`
  - `import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository`
  - `val passkeyRepository: PasskeyRepository by lazy { PasskeyRepositoryImpl(database.passkeyDao()) }` を `credentialRepository` / `detectedFieldRepository` の隣に追加
  - KDoc コメントを足して「PassKey storage for Issue #91 — used by future registration / authentication ceremony Issues」と明記

### 公開 IF

`ServiceLocator.passkeyRepository: PasskeyRepository` が後続セレモニー Issue から参照可能になる。

### 受入基準（要件対応）

- **req 4.1〜4.5**: Repository が DI で取り出せる状態になり、後続 Issue が再実装不要で参照できる
- **NFR 3.2**: 既存の `aesGcmCipher` / `keystoreKeyProvider` singleton（alias = `keynest_aead_v1`）には変更を加えない

### テストケース

なし。既存 ServiceLocator 経由のテストが引き続き動くことが確認できれば良い。

### 完了条件

- `./gradlew :app:compileDebugKotlin` 成功
- `./gradlew :app:testDebugUnitTest` で既存テスト全 pass

### 依存タスク

- T-06（PasskeyRepositoryImpl）

---

## T-08: Migration_4_5_Test の追加

### 概要

`Migration_4_5` の検証テストを追加する。design §9.2 のとおり、既存 `Migration_3_4_Test` と同じ `SupportSQLiteOpenHelper` で v4 fixture を素 SQL で構築 → `Migration_4_5.migrate(db)` を直接呼ぶパターンを採用する。Room の `MigrationTestHelper` は既存テストでも使われていないため踏襲しない。

### 変更ファイル

- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/Migration_4_5_Test.kt`

### 公開 IF

なし（テストファイル）。

### 受入基準（要件対応）

- **req 5.1**: `passkeys` テーブルが追加され、14 カラム全てが PRAGMA `table_info(passkeys)` で確認できる
- **req 5.2**: v4 fixture に投入した `credentials` 行（id / package_name / username / label / password_ciphertext / password_iv / signature_sha256 / signature_captured_at / created_at / updated_at / last_used_at / custom_fields_ciphertext / custom_fields_iv）と `detected_fields` 行（package_name / field_key / source / last_detected_at）が migration 後も完全一致で読み出せる
- **req 5.3**: PRAGMA `index_list('passkeys')` で `index_passkeys_rpId` (unique=0) と `index_passkeys_rpId_userHandle` (unique=1) が存在。PRAGMA `index_info('index_passkeys_rpId_userHandle')` で `(rpId, userHandle)` 2 列であることを確認

### テストケース（design §9.2 で 5 ケース確定）

1. `migrate_createsPasskeysTable_andPreservesCredentialsAndDetectedFields` (Req 2.1 / 2.3 / 5.1 / 5.2)
2. `migrate_createsIndexes` (Req 2.2 / 5.3)
3. `migrate_isIdempotent_onSecondCall` (既存パターン踏襲 / `IF NOT EXISTS` 確認)
4. `migrate_uniqueConstraint_rejectsDuplicateInsert` (Req 5.3 補強 — `(rpId, userHandle)` 重複 insert を `CONFLICT_IGNORE` で -1 になることで検証)
5. `migrate_allowsInsert_afterMigration` (DEFAULT 値 / NOT NULL 制約に違反しない合法行が insert できる)

v4 fixture の `CREATE TABLE credentials` / `CREATE INDEX index_credentials_package_name` / `CREATE TABLE detected_fields` / `CREATE INDEX index_detected_fields_package_name_last_detected_at` は `app/schemas/.../4.json` の `createSql` から書き起こす（既存 `Migration_3_4_Test` の `V3_CREATE_CREDENTIALS_SQL` と同じ作り）。

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *Migration_4_5_Test*` が 5 ケース全 pass
- 既存 `Migration_1_2_Test` / `Migration_2_3_Test` / `Migration_3_4_Test` も引き続き pass（NFR 既存破壊禁止）

### 依存タスク

- T-04（version 5 / Migration_4_5 が DB に登録済み）

---

## T-09: PasskeyDaoTest の追加

### 概要

`PasskeyDao` の振る舞いテストを追加する。design §9.3 のとおり、`Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()` で in-memory DB を作る `DetectedFieldDaoTest` パターンを踏襲する。

### 変更ファイル

- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyDaoTest.kt`

### 公開 IF

なし。

### 受入基準（要件対応）

- **req 5.4**: `insert` / `update` / `delete` / `findByCredentialId` / `findByRpIdAndUserHandle` / `listDiscoverableByRpId` / `listAllByRpId` / `incrementSignCount` の正常系を全件カバー
- **req 5.5**: `isDiscoverable = false` 行が `listDiscoverableByRpId` から除外され、`listAllByRpId` / `findByCredentialId` では取り出せる
- **req 5.6**: 同一 `(rpId, userHandle)` の 2 件目 insert で `SQLiteConstraintException` 伝播
- **req 5.7**: `incrementSignCount` が `signCount +1` と `lastUsedAt = timestamp` を同時更新、存在しない `credentialId` で no-op

### テストケース（design §9.3 で 14 ケース確定）

1. `insert_thenFindByCredentialId_returnsEntity`
2. `findByCredentialId_returnsNull_whenAbsent`
3. `insert_duplicateRpIdAndUserHandle_throwsConstraintException`
4. `findByRpIdAndUserHandle_returnsSingleEntity`
5. `findByRpIdAndUserHandle_returnsNull_whenUserHandleDiffers`
6. `listDiscoverableByRpId_excludesNonDiscoverable`
7. `listDiscoverableByRpId_ordersByLastUsedAtThenCreatedAt` (NULL 行は末尾)
8. `listAllByRpId_includesNonDiscoverable`
9. `listAllByRpId_excludesOtherRpId`
10. `incrementSignCount_increments_andUpdatesLastUsedAt`
11. `incrementSignCount_onAbsentRow_isNoOp`
12. `delete_removesOnlyTargetRow`
13. `delete_onAbsentCredentialId_isNoOp`
14. `update_persistsModifiedFields`

`@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [33])` + `runTest` で構築。test fixture 用の `passkeyEntity(...)` ヘルパ関数を private で定義し、各テストはそこから派生させる。

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *PasskeyDaoTest*` が 14 ケース全 pass

### 依存タスク

- T-04（KeyNestDatabase に passkeyDao がぶら下がる）

---

## T-10: PasskeyRepositoryTest の追加

### 概要

`PasskeyRepositoryImpl` のラウンドトリップテスト + Keystore alias 命名規則 / 削除時クリーンアップ / 復号失敗ハンドリングを検証する。design §9.4 のとおり、Robolectric の Bouncy Castle AndroidKeyStore は使わず、**fake CryptoHelper** (`FakeKeystoreKeyProvider` + `TestAesGcmCipher`) で差し替える。

### 変更ファイル

- 新規: `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyRepositoryTest.kt`

### 公開 IF

なし。

### 受入基準（要件対応）

- **req 5.8**: 暗号化 → 復号のラウンドトリップで平文 private key が完全一致
- **req 5.9**: `save("X", ...)` で alias `passkey_X` が Keystore に作成、`delete("X")` で同 alias が削除
- **req 5.10**: ciphertext を意図的に改竄した状態で `loadPrivateKey` を呼ぶと例外伝播（silent fail で null を返さない）
- **req 4.3 / 4.4**: Keystore 削除失敗時に `DeletePasskeyResult.KeystoreCleanupFailed` が返る（silent fail せず構造化結果で通知）

### テストケース（design §9.4 で 9 ケース確定）

1. `save_thenLoadPrivateKey_roundTripsPlaintext`
2. `save_persistsKeyAlias_inEntity` — DAO 直読みで `entity.keyAlias == "passkey_<credentialId>"`
3. `save_createsKeystoreAlias` — fake の `aliveAliases` を assert
4. `delete_removesRow_andKeystoreAlias`
5. `delete_returnsKeystoreCleanupFailed_whenKeystoreFails` — fake が `deleteKey` で例外を投げる構成
6. `loadPrivateKey_throwsAEADBadTagException_whenCiphertextTampered`
7. `save_multipleCredentialIds_usesDistinctAliases`
8. `findByCredentialId_returnsNull_whenAbsent`
9. `findByRpIdAndUserHandle_returnsPasskey_whenPresent`

#### テストヘルパ実装方針

- `FakeKeystoreKeyProvider(alias)` を `KeystoreKeyProvider` の `open` メソッドを override する形で同ファイル内に private に定義する。`aliveAliases: MutableSet<String>` を companion で持ち `@Before` で `clear()` する。
- `TestAesGcmCipher(keyProvider)` を `AesGcmCipher` の `encrypt` / `decrypt` を override する形で同ファイル内に private に定義する。`Cipher.getInstance("AES/GCM/NoPadding")` をデフォルト JCE provider 経由で呼び、`keyProvider.getOrCreateKey()` の戻り値（`SecretKeySpec` ベース）で encrypt / decrypt する。
- `PasskeyRepositoryImpl` の `cipherFactory` / `keyProviderFactory` コンストラクタ引数に fake を injection し、`ioDispatcher = UnconfinedTestDispatcher()` で `runTest` から同期実行する。
- DAO は `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build().passkeyDao()` を実機で使う（DAO は本物、暗号化レイヤだけ fake）。

### 完了条件

- `./gradlew :app:testDebugUnitTest --tests *PasskeyRepositoryTest*` が 9 ケース全 pass

### 依存タスク

- T-06（PasskeyRepositoryImpl）
- T-09（同じ in-memory DB パターンの確立後が望ましい — 並列でも可）

---

## T-11: 統合確認（全テスト pass + 既存テスト非破壊 + 5.json commit 確認）

### 概要

T-01 〜 T-10 完了後、全テスト pass / build 成功 / 既存テスト非破壊 / KSP 生成物 (`5.json`) の commit 確認 / PR 確認事項転記を行う。コミット対象は無し（確認のみ）。

### 変更ファイル

なし。ただし以下を確認:

- `app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/5.json` が commit に含まれている（T-04 で生成されたものが diff に乗っていること）

### 受入基準（要件対応）

- **req 全件**: requirements §「Requirements」セクションの Req 1.1〜5.10 が design.md → tasks.md → 実装テストの紐づけによって全件カバーされていること
- **既存テスト非破壊**（requirements §「既存テストへの影響」）:
  - `Migration_1_2_Test` / `Migration_2_3_Test` / `Migration_3_4_Test` 全 pass
  - `CredentialDaoTest` / `CredentialRepositoryImplTest` / `DetectedFieldDaoTest` 全 pass
  - `KeyNestAutofillService` 関連 (`FillResponseBuilderTest` / `LockedFillResponseSecurityTest` / `CustomFieldFillResponseTest`) 全 pass
- **NFR 5.1**: `5.json` が CI で fail せず通る（`exportSchema = true` 維持）
- **NFR 3.1**: 本 Issue 変更で `<uses-permission android:name="android.permission.INTERNET">` が追加されていないこと（既存 `InternetPermissionAbsenceTest` も pass）

### 完了条件

- `./gradlew :app:testDebugUnitTest` が **全 test pass**（新規 3 テストファイル + 既存全件）
- `./gradlew :app:assembleDebug` が成功
- `./gradlew :app:lintDebug` が警告ゼロ or 既存ベースライン内
- `git status` で `app/schemas/.../5.json` が tracked になっている
- `design.md §12.2` の確認事項候補を PR 本文「確認事項」セクションに転記:
  1. `5.json` の `indices[]` 内容（unique = true / false / 列順）目視確認
  2. `DeletePasskeyResult.KeystoreCleanupFailed` の通知粒度（例外伝播ではなく結果型）が要件 4.4「silent fail 禁止」を満たすことの human レビュー
  3. `EncryptedPasskeyRecord` domain 型を本 Issue で追加せず Entity ↔ Repository 直接マッピングにした判断（design §6.1 / 未解決事項 4）の human レビュー
  4. 平文 private key bytes の wipe 責務を呼び出し元に置く判断（design §12.1 最下行）の human レビュー

### 依存タスク

- T-01 〜 T-10 全完了
